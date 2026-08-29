"""Deterministic layout-search fixtures for the V2-to-V3 promotion gate."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Final, TypeAlias

JsonPrimitive: TypeAlias = str | int | float | bool | None
JsonValue: TypeAlias = JsonPrimitive | list["JsonValue"] | dict[str, "JsonValue"]


@dataclass(frozen=True, slots=True)
class FabricSpec:
    fabric_id: int
    start_x: float
    start_y: float
    end_x: float
    end_y: float


@dataclass(frozen=True, slots=True)
class SegmentSpec:
    start_x: float
    start_y: float
    end_x: float
    end_y: float


@dataclass(frozen=True, slots=True)
class ExitSpec:
    exit_id: int
    start_x: float
    start_y: float
    end_x: float
    end_y: float


@dataclass(frozen=True, slots=True)
class LayoutSpec:
    case_id: str
    scenario: str
    fabrics: tuple[FabricSpec, ...]
    agents: tuple[tuple[float, float], ...]
    exits: tuple[ExitSpec, ...]
    walls: tuple[SegmentSpec, ...] = ()
    movement_policies: tuple[tuple[int, str], ...] = ()
    movement_zones: tuple[tuple[int, tuple[float, float, float, float]], ...] = ()
    width: float = 12.0
    height: float = 12.0


@dataclass(frozen=True, slots=True)
class BenchmarkCase:
    case_id: str
    scenario: str
    input_data: dict[str, JsonValue]


TOP_EXIT: Final = ExitSpec(1, 5.0, 12.0, 7.0, 12.0)
BOTTOM_EXIT: Final = ExitSpec(2, 5.0, 0.0, 7.0, 0.0)
RIGHT_EXIT: Final = ExitSpec(3, 12.0, 5.0, 12.0, 7.0)


def _fabric(spec: FabricSpec) -> dict[str, JsonValue]:
    return {
        "id": spec.fabric_id,
        "name": f"fixture-{spec.fabric_id}",
        "startX": spec.start_x,
        "startY": spec.start_y,
        "endX": spec.end_x,
        "endY": spec.end_y,
        "rotation": 0.0,
    }


def _segment(spec: SegmentSpec) -> dict[str, JsonValue]:
    return {"startX": spec.start_x, "startY": spec.start_y, "endX": spec.end_x, "endY": spec.end_y}


def _exit(spec: ExitSpec) -> dict[str, JsonValue]:
    return {
        "id": spec.exit_id,
        "name": f"exit-{spec.exit_id}",
        "startX": spec.start_x,
        "startY": spec.start_y,
        "endX": spec.end_x,
        "endY": spec.end_y,
    }


def _input(spec: LayoutSpec) -> dict[str, JsonValue]:
    policies = {str(fabric_id): policy for fabric_id, policy in spec.movement_policies}
    zones = {
        str(fabric_id): {"x": x, "y": y, "width": width, "height": height}
        for fabric_id, (x, y, width, height) in spec.movement_zones
    }
    selected_exit_ids = [exit_spec.exit_id for exit_spec in spec.exits]
    return {
        "searchId": 1,
        "round": 1,
        "drawing": {
            "layoutId": 1,
            "title": spec.case_id,
            "width": spec.width,
            "height": spec.height,
            "outsideBoundary": [
                {"x": 0.0, "y": 0.0},
                {"x": spec.width, "y": 0.0},
                {"x": spec.width, "y": spec.height},
                {"x": 0.0, "y": spec.height},
            ],
            "walls": [_segment(wall) for wall in spec.walls],
            "pillars": [],
            "fabrics": [_fabric(fabric) for fabric in spec.fabrics],
            "layoutTexts": [],
            "exits": [_exit(exit_spec) for exit_spec in spec.exits],
        },
        "agents": [{"x": x, "y": y} for x, y in spec.agents],
        "hazards": [],
        "selectedExitIds": selected_exit_ids,
        "densityThreshold": 3.5,
        "findings": [
            {
                "type": "BOTTLENECK",
                "severity": 0.82,
                "region": {"startX": 4.5, "startY": 4.0, "endX": 7.5, "endY": 8.0},
                "evidence": {"metric": "PEAK_DENSITY", "value": 4.8, "unit": "PERSON_PER_M2", "source": "FIXTURE"},
                "description": spec.scenario,
            }
        ],
        "parents": [],
        "maxCandidates": 6,
        "constraints": {"movementPolicies": policies, "movementZones": zones, "forbiddenZones": []},
    }


_SPECS: Final[tuple[LayoutSpec, ...]] = (
    LayoutSpec("straight-single-blocker", "straight corridor", (FabricSpec(10, 5.5, 4.0, 6.5, 7.0),), ((6.0, 1.0),), (TOP_EXIT,)),
    LayoutSpec("straight-paired-blockers", "straight corridor with paired blockers", (FabricSpec(10, 3.0, 4.0, 9.0, 5.0), FabricSpec(11, 3.0, 6.5, 9.0, 7.5)), ((6.0, 1.0),), (TOP_EXIT,)),
    LayoutSpec("tapered-corridor", "tapered corridor", (FabricSpec(10, 5.5, 4.0, 6.5, 7.5),), ((6.0, 1.0),), (TOP_EXIT,), (SegmentSpec(2.0, 0.0, 4.0, 12.0), SegmentSpec(10.0, 0.0, 8.0, 12.0))),
    LayoutSpec("l-turn", "right angle turn", (FabricSpec(10, 8.2, 4.2, 10.8, 5.2),), ((2.0, 2.0),), (RIGHT_EXIT,), (SegmentSpec(0.0, 4.0, 8.0, 4.0), SegmentSpec(8.0, 4.0, 8.0, 12.0))),
    LayoutSpec("s-turn", "double turn corridor", (FabricSpec(10, 5.0, 4.5, 7.0, 5.5),), ((2.0, 2.0),), (TOP_EXIT,), (SegmentSpec(0.0, 3.5, 7.0, 3.5), SegmentSpec(5.0, 7.5, 12.0, 7.5))),
    LayoutSpec("multi-exit", "two exits", (FabricSpec(10, 6.5, 3.0, 11.8, 4.0),), ((6.0, 1.0), (6.0, 2.0)), (TOP_EXIT, RIGHT_EXIT)),
    LayoutSpec("interacting-blockers", "interacting blocker set", (FabricSpec(10, 4.75, 4.0, 5.75, 8.0), FabricSpec(11, 6.25, 4.0, 7.25, 8.0)), ((6.0, 1.0), (6.0, 2.0)), (TOP_EXIT,)),
    LayoutSpec("within-zone", "within-zone movement", (FabricSpec(10, 5.0, 4.0, 7.0, 6.0),), ((6.0, 1.0),), (TOP_EXIT,), movement_policies=((10, "WITHIN_ZONE"),), movement_zones=((10, (3.5, 3.0, 5.0, 5.0)),)),
    LayoutSpec("fixed-blocker", "fixed blocker", (FabricSpec(10, 5.0, 4.0, 7.0, 6.0),), ((6.0, 1.0),), (TOP_EXIT,), movement_policies=((10, "FIXED"),)),
    LayoutSpec("empty-noop", "empty no-op", (), ((6.0, 1.0),), (TOP_EXIT,)),
)


def benchmark_cases() -> tuple[BenchmarkCase, ...]:
    """Return the fixed V2-to-V3 promotion corpus in stable order."""
    return tuple(BenchmarkCase(spec.case_id, spec.scenario, _input(spec)) for spec in _SPECS)
