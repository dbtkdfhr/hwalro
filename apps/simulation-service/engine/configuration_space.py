from __future__ import annotations

import math
from dataclasses import dataclass
from enum import StrEnum
from typing import Final, assert_never

from shapely.affinity import rotate, translate
from shapely.geometry import box
from shapely.geometry.base import BaseGeometry

from constraints import FIXED, FREE, WITHIN_ZONE, SearchConstraints
from search_precision import decimal_value, quantized

MIN_ANGLE_STEP_DEGREES: Final = 1.0
MAX_ANGLE_STEP_DEGREES: Final = 30.0
PROJECTION_REFINEMENTS: Final = 48


@dataclass(frozen=True, slots=True)
class InvalidConfigurationValueError(ValueError):
    name: str
    value: float

    def __str__(self) -> str:
        return f"{self.name} must be finite and positive where applicable: {self.value}"


class MovementPolicy(StrEnum):
    FREE = FREE
    WITHIN_ZONE = WITHIN_ZONE
    FIXED = FIXED


_POLICIES: Final = {policy.value: policy for policy in MovementPolicy}


def _normalized_number(value: float) -> float:
    normalized = float(quantized(decimal_value(value)))
    return 0.0 if normalized == 0.0 else normalized


def normalized_angle(value: float) -> float:
    return _normalized_number(value % 360.0)


@dataclass(frozen=True, slots=True)
class Pose:
    x: float
    y: float
    theta: float = 0.0

    def __post_init__(self) -> None:
        for name, value in (("x", self.x), ("y", self.y), ("theta", self.theta)):
            if not math.isfinite(value):
                raise InvalidConfigurationValueError(name, value)
        object.__setattr__(self, "x", _normalized_number(self.x))
        object.__setattr__(self, "y", _normalized_number(self.y))
        object.__setattr__(self, "theta", normalized_angle(self.theta))


@dataclass(frozen=True, slots=True)
class RectangleFootprint:
    width: float
    height: float

    def __post_init__(self) -> None:
        for name, value in (("width", self.width), ("height", self.height)):
            if not math.isfinite(value):
                raise InvalidConfigurationValueError(name, value)
            normalized = _normalized_number(value)
            if normalized <= 0.0:
                raise InvalidConfigurationValueError(name, value)
            object.__setattr__(self, name, normalized)


@dataclass(frozen=True, slots=True)
class ConfigurationSpace:
    fabric_id: int
    source: Pose
    footprint: RectangleFootprint
    outside: BaseGeometry
    constraints: SearchConstraints
    solids: tuple[BaseGeometry, ...] = ()
    walls: tuple[BaseGeometry, ...] = ()
    blocked: tuple[BaseGeometry, ...] = ()


def rectangle_at(footprint: RectangleFootprint, pose: Pose) -> BaseGeometry:
    half_width = footprint.width / 2.0
    half_height = footprint.height / 2.0
    centered = box(-half_width, -half_height, half_width, half_height)
    turned = rotate(centered, pose.theta, origin=(0.0, 0.0), use_radians=False)
    return translate(turned, xoff=pose.x, yoff=pose.y)


def _policy_allows(space: ConfigurationSpace, pose: Pose, placed: BaseGeometry) -> bool:
    policy = _POLICIES.get(space.constraints.movement_policy_of(space.fabric_id))
    match policy:
        case MovementPolicy.FREE:
            return True
        case MovementPolicy.WITHIN_ZONE:
            zone = space.constraints.movement_area_of(space.fabric_id)
            return zone is not None and zone.covers(placed)
        case MovementPolicy.FIXED:
            return pose == space.source
        case None:
            return False
        case unreachable:
            assert_never(unreachable)


def is_feasible(space: ConfigurationSpace, pose: Pose) -> bool:
    placed = rectangle_at(space.footprint, pose)
    if not _policy_allows(space, pose, placed) or not space.outside.covers(placed):
        return False
    if any(placed.intersects(obstacle) for obstacle in space.solids):
        return False
    if any(placed.crosses(wall) or placed.contains(wall) for wall in space.walls):
        return False
    excluded = (*space.blocked, *space.constraints.forbidden_zones)
    return not any(placed.intersects(region) for region in excluded)


def _clearance(space: ConfigurationSpace, pose: Pose) -> float:
    placed = rectangle_at(space.footprint, pose)
    constraints = (
        space.outside.boundary,
        *space.solids,
        *space.walls,
        *space.blocked,
        *space.constraints.forbidden_zones,
    )
    return min(placed.distance(item) for item in constraints)


def adaptive_angles(space: ConfigurationSpace, around: Pose) -> tuple[float, ...]:
    radius = math.hypot(space.footprint.width, space.footprint.height) / 2.0
    clearance = _clearance(space, around)
    vertex_motion = min(max(clearance, 0.0001), min(space.footprint.width, space.footprint.height) / 2.0)
    chord_ratio = min(1.0, vertex_motion / (2.0 * radius))
    unconstrained_step = math.degrees(2.0 * math.asin(chord_ratio))
    bounded_step = min(MAX_ANGLE_STEP_DEGREES, max(MIN_ANGLE_STEP_DEGREES, unconstrained_step))
    sample_count = math.ceil(360.0 / bounded_step)
    actual_step = 360.0 / sample_count
    return tuple(normalized_angle(around.theta + index * actual_step) for index in range(sample_count))


def _shortest_turn(start: float, end: float) -> float:
    difference = (end - start + 180.0) % 360.0 - 180.0
    return 180.0 if difference == -180.0 else difference


def _interpolated_pose(start: Pose, end: Pose, fraction: float) -> Pose:
    return Pose(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction,
        start.theta + _shortest_turn(start.theta, end.theta) * fraction,
    )


def project_pose(space: ConfigurationSpace, proposal: Pose) -> Pose | None:
    if is_feasible(space, proposal):
        return proposal
    if not is_feasible(space, space.source):
        return None
    feasible_fraction = 0.0
    projected = space.source
    blocked_fraction = 1.0
    for _ in range(PROJECTION_REFINEMENTS):
        middle = (feasible_fraction + blocked_fraction) / 2.0
        candidate = _interpolated_pose(space.source, proposal, middle)
        if is_feasible(space, candidate):
            feasible_fraction = middle
            projected = candidate
        else:
            blocked_fraction = middle
    return projected
