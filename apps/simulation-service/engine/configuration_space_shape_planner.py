from __future__ import annotations

from dataclasses import dataclass
import time
from typing import Any, Final, Sequence

from shapely.geometry import LineString, Polygon
from shapely.geometry.base import BaseGeometry

from configuration_space import (
    ConfigurationSpace,
    Pose,
    RectangleFootprint,
    project_pose,
    rectangle_at,
)
from configuration_space_optimizer import OptimizationResult, optimize
from configuration_space_planner_deadline import (
    GenerationBudgetExpired,
    GenerationDeadline,
)
from configuration_space_search_types import (
    FabricId,
    Layout,
    LayoutPose,
    ObjectiveVector,
    OptimizationCallbacks,
    OptimizationRequest,
    PoseSignal,
    SearchLimits,
    StepSchedule,
    Tolerances,
)
from constraints import SearchConstraints
from continuum_flow import ContinuumEvaluator, ContinuumFabric, ContinuumScenario
from ideal_flow import BlockerHyperedge, FabricObstacle, IdealFlowAnalysis, build_ideal_flow
from ideal_route_docking_placement import Drawing, Rectangle, geometry, state
from route_planner import (
    GRID_STEP_METERS,
    Hazard,
    build_routing_geometry,
    parse_exits,
    relocate_agents,
)
from shape_sensitivity import ShapeSignal, shape_signals

GRID_MODE: Final = "CONFIGURATION_SPACE_SHAPE_GRID"
PDE_MODE: Final = "CONFIGURATION_SPACE_SHAPE_PDE"
GRID_VERSION: Final = "CONFIGURATION_SPACE_SHAPE_V3_GRID"
PDE_VERSION: Final = "CONFIGURATION_SPACE_SHAPE_V3_PDE"
EVALUATIONS_PER_GROUP: Final = 128


@dataclass(frozen=True, slots=True)
class GridPlannerRequest:
    drawing: Drawing
    agents: tuple[tuple[float, float], ...]
    selected_exit_ids: tuple[int | str, ...]
    hazards: tuple[Hazard, ...]
    maximum_candidates: int
    generation_seconds: float
    constraints: SearchConstraints


@dataclass(frozen=True, slots=True)
class _SearchContext:
    request: GridPlannerRequest
    flow: IdealFlowAnalysis
    fabrics: dict[int, Rectangle]
    spaces: dict[FabricId, ConfigurationSpace]
    mode: str = GRID_MODE
    evaluator: ContinuumEvaluator | None = None


def _source_pose(fabric: Rectangle) -> Pose:
    return Pose(
        (float(fabric["startX"]) + float(fabric["endX"])) / 2.0,
        (float(fabric["startY"]) + float(fabric["endY"])) / 2.0,
        float(fabric.get("rotation", 0.0)),
    )


def _footprint(fabric: Rectangle) -> RectangleFootprint:
    return RectangleFootprint(
        abs(float(fabric["endX"]) - float(fabric["startX"])),
        abs(float(fabric["endY"]) - float(fabric["startY"])),
    )


def _outside(drawing: Drawing) -> Polygon:
    return Polygon(tuple((float(point["x"]), float(point["y"])) for point in drawing["outsideBoundary"]))


def _walls(drawing: Drawing) -> tuple[BaseGeometry, ...]:
    return tuple(
        LineString(
            (
                (float(wall["startX"]), float(wall["startY"])),
                (float(wall["endX"]), float(wall["endY"])),
            )
        )
        for wall in drawing.get("walls", [])
    )


def _exit_guards(drawing: Drawing) -> tuple[BaseGeometry, ...]:
    return tuple(
        LineString(
            (
                (float(exit_item["startX"]), float(exit_item["startY"])),
                (float(exit_item["endX"]), float(exit_item["endY"])),
            )
        ).buffer(GRID_STEP_METERS)
        for exit_item in drawing.get("exits", [])
    )


def _spaces(request: GridPlannerRequest, fabric_ids: tuple[int, ...]) -> dict[FabricId, ConfigurationSpace]:
    moved = set(fabric_ids)
    solids = tuple(geometry(state(item)) for item in request.drawing.get("pillars", []))
    solids += tuple(
        geometry(state(item))
        for item in request.drawing.get("fabrics", [])
        if int(item["id"]) not in moved
    )
    outside = _outside(request.drawing)
    walls = _walls(request.drawing)
    blocked = _exit_guards(request.drawing)
    fabrics = {int(item["id"]): item for item in request.drawing.get("fabrics", [])}
    return {
        FabricId(fabric_id): ConfigurationSpace(
            fabric_id,
            _source_pose(fabrics[fabric_id]),
            _footprint(fabrics[fabric_id]),
            outside,
            request.constraints,
            solids,
            walls,
            blocked,
        )
        for fabric_id in fabric_ids
    }


def _obstacles(context: _SearchContext, layout: Layout) -> tuple[FabricObstacle, ...]:
    return tuple(
        FabricObstacle(
            int(item.fabric_id),
            rectangle_at(context.spaces[item.fabric_id].footprint, item.pose),
        )
        for item in layout.poses
    )


def _signals(context: _SearchContext, layout: Layout) -> tuple[ShapeSignal, ...]:
    return shape_signals(_obstacles(context, layout), context.flow.edges, GRID_STEP_METERS)


def _objective(context: _SearchContext, layout: Layout) -> ObjectiveVector:
    if context.evaluator is not None:
        return context.evaluator(layout)
    signals = _signals(context, layout)
    total = sum(signal.obstruction for signal in signals)
    average = total / max(1, len(context.flow.routes))
    peak = max((signal.obstruction for signal in signals), default=0.0)
    return ObjectiveVector(total, average, (average + peak) / 2.0)


def _project(context: _SearchContext, layout: Layout) -> Layout | None:
    projected: list[LayoutPose] = []
    footprints: list[BaseGeometry] = []
    for item in layout.poses:
        space = context.spaces[item.fabric_id]
        pose = project_pose(space, item.pose)
        if pose is None:
            return None
        footprint = rectangle_at(space.footprint, pose)
        if any(footprint.intersects(existing) for existing in footprints):
            return None
        projected.append(LayoutPose(item.fabric_id, pose))
        footprints.append(footprint)
    return Layout.of(tuple(projected))


def _optimizer_signals(context: _SearchContext, layout: Layout) -> tuple[PoseSignal, ...]:
    if context.evaluator is not None:
        return context.evaluator.signals(layout)
    return tuple(
        PoseSignal(FabricId(item.fabric_id), item.force_x, item.force_y, item.torque)
        for item in _signals(context, layout)
    )


def _initial_layout(spaces: dict[FabricId, ConfigurationSpace]) -> Layout:
    return Layout.of(tuple(LayoutPose(fabric_id, space.source) for fabric_id, space in spaces.items()))


def _optimize_group(context: _SearchContext, deadline: float) -> OptimizationResult:
    callbacks = OptimizationCallbacks(
        lambda layout: _objective(context, layout),
        lambda layout: _optimizer_signals(context, layout),
        lambda layout: _project(context, layout),
        time.monotonic,
    )
    request = OptimizationRequest(
        _initial_layout(context.spaces),
        StepSchedule(0.5, 15.0, 0.125, 0.5),
        Tolerances(0.0001, 0.000001),
        SearchLimits(EVALUATIONS_PER_GROUP, max(8, context.request.maximum_candidates + 1), deadline),
    )
    return optimize(request, callbacks)


def _after_state(fabric: Rectangle, pose: Pose) -> dict[str, float]:
    footprint = _footprint(fabric)
    return {
        "startX": pose.x - footprint.width / 2.0,
        "startY": pose.y - footprint.height / 2.0,
        "endX": pose.x + footprint.width / 2.0,
        "endY": pose.y + footprint.height / 2.0,
        # Match the docking planner: a full turn is reported as 0, not 360.
        "rotation": pose.theta % 360.0,
    }


def _candidate(context: _SearchContext, result: OptimizationResult, layout: Layout) -> dict[str, Any]:
    initial = _initial_layout(context.spaces)
    baseline = _objective(context, initial)
    objective = _objective(context, layout)
    baseline_signals = _optimizer_signals(context, initial)
    return {
        "originFindingType": "IDEAL_FLOW",
        "operatorType": "CONFIGURATION_SPACE_SHAPE",
        "parentCandidateId": None,
        "proxyScore": round(baseline.balanced - objective.balanced, 6),
        "ops": [
            {
                "type": "MOVE_FABRIC",
                "fabricId": int(item.fabric_id),
                "before": state(context.fabrics[int(item.fabric_id)]),
                "after": _after_state(context.fabrics[int(item.fabric_id)], item.pose),
            }
            for item in layout.poses
        ],
        "rationale": {
            "plannerMode": context.mode,
            "objective": {
                "total": objective.total,
                "average": objective.average,
                "balanced": objective.balanced,
            },
            "blockers": [int(item.fabric_id) for item in layout.poses],
            "forceTorque": [
                {
                    "fabricId": int(signal.fabric_id),
                    "forceX": signal.force_x,
                    "forceY": signal.force_y,
                    "torque": signal.torque,
                }
                for signal in baseline_signals
            ],
            "feasibility": "EXACT_FINAL_PLACEMENT",
            "generation": {"evaluations": result.evaluations, "reason": result.reason.value},
        },
    }


def _continuum_evaluator(request: GridPlannerRequest, fabric_ids: tuple[int, ...]) -> ContinuumEvaluator:
    """Build the continuum scenario with only the group under optimization removed.

    ponytail: one full eikonal solve per objective, so PDE runs ~20x slower than
    GRID and leans on the generation deadline on large drawings. Coarsen the
    continuum cell size or reuse fields across neighbouring poses if that bites.
    """
    moved = set(fabric_ids)
    static: Drawing = {
        **request.drawing,
        "fabrics": [item for item in request.drawing.get("fabrics", []) if int(item["id"]) not in moved],
    }
    walkable = build_routing_geometry(static, GRID_STEP_METERS)
    agents, _ = relocate_agents(walkable, request.agents)
    exits = tuple(
        LineString((item.start, item.end))
        for item in parse_exits(request.drawing, request.selected_exit_ids)
    )
    fabrics = {int(item["id"]): item for item in request.drawing.get("fabrics", [])}
    return ContinuumEvaluator(
        ContinuumScenario(
            walkable,
            exits,
            tuple(agents),
            tuple(ContinuumFabric(FabricId(fabric_id), _footprint(fabrics[fabric_id])) for fabric_id in fabric_ids),
            GRID_STEP_METERS,
        )
    )


def _group_candidates(
    request: GridPlannerRequest,
    flow: IdealFlowAnalysis,
    fabrics: dict[int, Rectangle],
    mode: str,
    group: BlockerHyperedge,
    deadline: GenerationDeadline,
) -> list[dict[str, Any]]:
    """Optimize one blocker hyperedge into its own ranked candidate list."""
    spaces = _spaces(request, group.fabric_ids)
    evaluator = _continuum_evaluator(request, group.fabric_ids) if mode == PDE_MODE else None
    context = _SearchContext(request, flow, fabrics, spaces, mode, evaluator)
    initial = _initial_layout(spaces)
    result = _optimize_group(context, deadline.value)
    found: list[dict[str, Any]] = []
    for entry in result.archive.entries:
        if entry.layout == initial:
            continue
        candidate = _candidate(context, result, entry.layout)
        if candidate not in found:
            found.append(candidate)
    return found


def _interleave(groups: Sequence[Sequence[dict[str, Any]]], maximum: int) -> list[dict[str, Any]]:
    """Give every blocker group a trial before any group gets a second variant."""
    merged: list[dict[str, Any]] = []
    for rank in range(max((len(group) for group in groups), default=0)):
        for group in groups:
            if rank < len(group):
                merged.append(group[rank])
                if len(merged) >= maximum:
                    return merged
    return merged


def generate_candidates(request: GridPlannerRequest, mode: str = GRID_MODE) -> list[dict[str, Any]]:
    if request.maximum_candidates <= 0:
        return []
    deadline = GenerationDeadline.start(request.generation_seconds, time.monotonic)
    ranked: list[list[dict[str, Any]]] = []
    try:
        deadline.checkpoint()
        flow = build_ideal_flow(
            request.drawing,
            request.agents,
            request.selected_exit_ids,
            request.hazards,
            request.constraints,
            checkpoint=deadline.checkpoint,
        )
        deadline.checkpoint()
        fabrics = {int(item["id"]): item for item in request.drawing.get("fabrics", [])}
        groups = sorted(flow.blocker_hyperedges, key=lambda item: (-item.delay, -item.demand, item.fabric_ids))
        for group in groups:
            deadline.checkpoint()
            found = _group_candidates(request, flow, fabrics, mode, group, deadline)
            if found:
                ranked.append(found)
            if len(ranked) >= request.maximum_candidates:
                break
    except GenerationBudgetExpired:
        pass
    return _interleave(ranked, request.maximum_candidates)


def planner_result(version: str, candidates: Sequence[dict[str, Any]], status: str = "AVAILABLE") -> dict[str, Any]:
    return {
        "plannerVersion": version,
        "plannerStatus": status,
        "candidates": list(candidates),
        "rejected": [],
        "rejectedCounts": {},
        "generationMode": "BOUNDED",
        "roundIndex": 1,
        "rawCandidateCount": len(candidates),
        "surrogateHealth": {"status": "DISABLED", "reason": version},
    }
