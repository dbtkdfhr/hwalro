from __future__ import annotations

import math
from dataclasses import dataclass

from shapely.geometry.base import BaseGeometry

from configuration_space import Pose, rectangle_at
from configuration_space_search_types import FabricId, Layout, LayoutPose, ObjectiveVector, PoseSignal
from continuum_eikonal import solve_continuum_field
from continuum_flow_types import (
    ContinuumEvaluation,
    ContinuumFabric,
    ContinuumScenario,
    FieldRequest,
    MissingContinuumPoseError,
)
from continuum_sampling import sample_field

__all__ = ["ContinuumEvaluation", "ContinuumEvaluator", "ContinuumFabric", "ContinuumScenario"]


@dataclass(frozen=True, slots=True)
class _PoseDelta:
    x: float = 0.0
    y: float = 0.0
    theta: float = 0.0


@dataclass(frozen=True, slots=True)
class _FiniteDifference:
    center: float
    negative: float
    positive: float
    step: float

    def descent(self) -> float:
        if math.isfinite(self.negative) and math.isfinite(self.positive):
            return (self.negative - self.positive) / (2.0 * self.step)
        if math.isfinite(self.center) and math.isfinite(self.negative):
            return -(self.center - self.negative) / self.step
        if math.isfinite(self.center) and math.isfinite(self.positive):
            return -(self.positive - self.center) / self.step
        return 0.0


def _replace_pose(layout: Layout, fabric_id: FabricId, delta: _PoseDelta) -> Layout:
    poses = tuple(
        LayoutPose(
            item.fabric_id,
            Pose(
                item.pose.x + delta.x,
                item.pose.y + delta.y,
                item.pose.theta + delta.theta,
            ),
        )
        if item.fabric_id == fabric_id
        else item
        for item in layout.poses
    )
    return Layout.of(poses)


class ContinuumEvaluator:
    def __init__(self, scenario: ContinuumScenario) -> None:
        self._scenario = scenario
        self._objectives: dict[Layout, ObjectiveVector] = {}

    def _obstacles(self, layout: Layout) -> tuple[BaseGeometry, ...]:
        pose_by_id = {item.fabric_id: item.pose for item in layout.poses}
        obstacles = []
        for fabric in self._scenario.fabrics:
            pose = pose_by_id.get(fabric.fabric_id)
            if pose is None:
                raise MissingContinuumPoseError(fabric.fabric_id)
            obstacles.append(rectangle_at(fabric.footprint, pose))
        return tuple(obstacles)

    def evaluate(self, layout: Layout) -> ContinuumEvaluation:
        field = solve_continuum_field(FieldRequest(self._scenario, self._obstacles(layout)))
        arrivals = [sample_field(field, agent) for agent in self._scenario.agents]
        if not arrivals:
            objective = ObjectiveVector(0.0, 0.0, 0.0)
        elif any(math.isinf(value) for value in arrivals):
            objective = ObjectiveVector(math.inf, math.inf, math.inf)
        else:
            total = math.fsum(arrivals)
            average = total / len(arrivals)
            objective = ObjectiveVector(total, average, (average + max(arrivals)) / 2.0)
        return ContinuumEvaluation(objective, field)

    def __call__(self, layout: Layout) -> ObjectiveVector:
        """Memoize objectives; finite-difference signals revisit the same layouts."""
        cached = self._objectives.get(layout)
        if cached is None:
            cached = self.evaluate(layout).objective
            self._objectives[layout] = cached
        return cached

    def signals(self, layout: Layout) -> tuple[PoseSignal, ...]:
        center = self(layout).balanced
        translation = self._scenario.cell_size
        rotation = 2.0
        signals = []
        for fabric in self._scenario.fabrics:
            negative_x = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(x=-translation))).balanced
            positive_x = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(x=translation))).balanced
            negative_y = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(y=-translation))).balanced
            positive_y = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(y=translation))).balanced
            negative_angle = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(theta=-rotation))).balanced
            positive_angle = self(_replace_pose(layout, fabric.fabric_id, _PoseDelta(theta=rotation))).balanced
            signals.append(
                PoseSignal(
                    fabric.fabric_id,
                    _FiniteDifference(center, negative_x, positive_x, translation).descent(),
                    _FiniteDifference(center, negative_y, positive_y, translation).descent(),
                    _FiniteDifference(center, negative_angle, positive_angle, rotation).descent(),
                )
            )
        return tuple(signals)
