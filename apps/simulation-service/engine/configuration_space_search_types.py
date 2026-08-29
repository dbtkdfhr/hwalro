from __future__ import annotations

import math
from dataclasses import dataclass
from enum import StrEnum, unique
from typing import NewType, Protocol

from configuration_space import Pose


FabricId = NewType("FabricId", int)


@dataclass(frozen=True, slots=True)
class DuplicateFabricPoseError(ValueError):
    fabric_id: FabricId

    def __str__(self) -> str:
        return f"duplicate pose for fabric {self.fabric_id}"


@dataclass(frozen=True, slots=True)
class LayoutPose:
    fabric_id: FabricId
    pose: Pose


@dataclass(frozen=True, slots=True)
class Layout:
    poses: tuple[LayoutPose, ...]

    @classmethod
    def of(cls, poses: tuple[LayoutPose, ...]) -> Layout:
        ordered = tuple(sorted(poses, key=lambda item: item.fabric_id))
        for before, after in zip(ordered, ordered[1:], strict=False):
            if before.fabric_id == after.fabric_id:
                raise DuplicateFabricPoseError(before.fabric_id)
        return cls(ordered)


@dataclass(frozen=True, slots=True)
class ObjectiveVector:
    total: float
    average: float
    balanced: float

    def values(self) -> tuple[float, float, float]:
        return (self.total, self.average, self.balanced)


@dataclass(frozen=True, slots=True)
class PoseSignal:
    fabric_id: FabricId
    force_x: float
    force_y: float
    torque: float


@dataclass(frozen=True, slots=True)
class InvalidSearchBoundError(ValueError):
    name: str
    minimum: float
    maximum: float

    def __str__(self) -> str:
        return f"search bound {self.name} must be finite and increasing: [{self.minimum}, {self.maximum}]"


@dataclass(frozen=True, slots=True)
class PoseBounds:
    fabric_id: FabricId
    minimum_x: float
    maximum_x: float
    minimum_y: float
    maximum_y: float
    minimum_theta: float
    maximum_theta: float
    theta_seeds: tuple[float, ...] = ()

    def __post_init__(self) -> None:
        ranges = (
            ("x", self.minimum_x, self.maximum_x),
            ("y", self.minimum_y, self.maximum_y),
            ("theta", self.minimum_theta, self.maximum_theta),
        )
        for name, minimum, maximum in ranges:
            if not math.isfinite(minimum) or not math.isfinite(maximum) or minimum >= maximum:
                raise InvalidSearchBoundError(name, minimum, maximum)
        for theta in self.theta_seeds:
            if not math.isfinite(theta):
                raise InvalidSearchBoundError("theta_seed", theta, theta)


@dataclass(frozen=True, slots=True)
class SearchDomain:
    bounds: tuple[PoseBounds, ...]
    seeds: tuple[Layout, ...] = ()

    @classmethod
    def of(cls, bounds: tuple[PoseBounds, ...], seeds: tuple[Layout, ...] = ()) -> SearchDomain:
        ordered = tuple(sorted(bounds, key=lambda item: item.fabric_id))
        for before, after in zip(ordered, ordered[1:], strict=False):
            if before.fabric_id == after.fabric_id:
                raise DuplicateFabricPoseError(before.fabric_id)
        return cls(ordered, seeds)


@dataclass(frozen=True, slots=True)
class SearchDomainLayoutMismatchError(ValueError):
    expected: tuple[FabricId, ...]
    actual: tuple[FabricId, ...]

    def __str__(self) -> str:
        return f"search domain fabric ids {self.actual} do not match layout fabric ids {self.expected}"


@dataclass(frozen=True, slots=True)
class InvalidRefinementRatioError(ValueError):
    ratio: float

    def __str__(self) -> str:
        return f"refinement ratio must be greater than zero and less than one: {self.ratio}"


@dataclass(frozen=True, slots=True)
class StepSchedule:
    translation: float
    rotation: float
    minimum_scale: float
    refinement_ratio: float

    def __post_init__(self) -> None:
        if not 0.0 < self.refinement_ratio < 1.0:
            raise InvalidRefinementRatioError(self.refinement_ratio)


@dataclass(frozen=True, slots=True)
class Tolerances:
    pose: float
    objective: float


@dataclass(frozen=True, slots=True)
class SearchLimits:
    evaluations: int
    archive_size: int
    deadline: float


@dataclass(frozen=True, slots=True)
class OptimizationRequest:
    initial_layout: Layout
    steps: StepSchedule
    tolerances: Tolerances
    limits: SearchLimits
    domain: SearchDomain | None = None


class ObjectiveEvaluator(Protocol):
    def __call__(self, layout: Layout) -> ObjectiveVector: ...


class ShapeSignalProvider(Protocol):
    def __call__(self, layout: Layout) -> tuple[PoseSignal, ...]: ...


class LayoutProjector(Protocol):
    def __call__(self, layout: Layout) -> Layout | None: ...


class MonotonicClock(Protocol):
    def __call__(self) -> float: ...


@dataclass(frozen=True, slots=True)
class OptimizationCallbacks:
    evaluate: ObjectiveEvaluator
    signals: ShapeSignalProvider
    project: LayoutProjector
    clock: MonotonicClock


@unique
class TerminationReason(StrEnum):
    REFINEMENT_COMPLETE = "refinement_complete"
    EVALUATION_LIMIT = "evaluation_limit"
    DEADLINE = "deadline"
    INFEASIBLE_START = "infeasible_start"
