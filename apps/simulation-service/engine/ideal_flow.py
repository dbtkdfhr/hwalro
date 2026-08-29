from __future__ import annotations

from collections.abc import Callable, Sequence
from collections import defaultdict
from dataclasses import dataclass
import math

from shapely import get_parts
from shapely.geometry import LineString
from shapely.geometry import Point as ShapelyPoint
from shapely.geometry.base import BaseGeometry

from constraints import SearchConstraints
from ideal_route_docking_placement import Drawing, geometry, state
from route_planner import (
    GRID_STEP_METERS,
    GridRouter,
    Hazard,
    Route,
    build_routing_geometry,
    build_walkable_geometry,
    parse_exits,
    relocate_agents,
)

Point = tuple[float, float]
EdgeKey = tuple[Point, Point]


@dataclass(frozen=True, slots=True)
class AgentIdealPath:
    agent_index: int
    exit_id: str
    points: tuple[Point, ...]
    ideal_cost: float


@dataclass(frozen=True, slots=True)
class FabricObstacle:
    fabric_id: int
    geometry: BaseGeometry


@dataclass(frozen=True, slots=True)
class FlowEdge:
    start: Point
    end: Point
    demand: int
    capacity: float
    delay: float


@dataclass(frozen=True, slots=True)
class BlockerHyperedge:
    fabric_ids: tuple[int, ...]
    agent_indices: tuple[int, ...]
    demand: int
    delay: float


@dataclass(frozen=True, slots=True)
class IdealFlowAnalysis:
    routes: tuple[AgentIdealPath, ...]
    edges: tuple[FlowEdge, ...]
    blocker_hyperedges: tuple[BlockerHyperedge, ...]


@dataclass(frozen=True, slots=True)
class InvalidClearanceError(ValueError):
    clearance: float

    def __str__(self) -> str:
        return f"clearance must be positive and finite, got {self.clearance}"


def _rounded_point(point: Point) -> Point:
    return (round(float(point[0]), 9), round(float(point[1]), 9))


def _edge_key(start: Point, end: Point) -> EdgeKey:
    pair = (_rounded_point(start), _rounded_point(end))
    return pair if pair[0] <= pair[1] else (pair[1], pair[0])


def _route_edge_keys(route: AgentIdealPath) -> tuple[EdgeKey, ...]:
    return tuple(
        _edge_key(start, end)
        for start, end in zip(route.points, route.points[1:], strict=False)
        if math.dist(start, end) > 1e-9
    )


def _local_clear_width(
    edge: EdgeKey, walkable: BaseGeometry, clearance: float
) -> float:
    start, end = edge
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    length = math.hypot(dx, dy)
    normal = (-dy / length, dx / length)
    midpoint = ((start[0] + end[0]) * 0.5, (start[1] + end[1]) * 0.5)
    min_x, min_y, max_x, max_y = walkable.bounds
    reach = math.hypot(max_x - min_x, max_y - min_y) + 2.0 * clearance
    cross_section = LineString(
        (
            (midpoint[0] - normal[0] * reach, midpoint[1] - normal[1] * reach),
            (midpoint[0] + normal[0] * reach, midpoint[1] + normal[1] * reach),
        )
    ).intersection(walkable)
    tolerance = max(1e-9, clearance * 1e-8)
    origin = ShapelyPoint(midpoint)
    intervals: list[tuple[float, float]] = []
    for part in get_parts(cross_section):
        if (
            not isinstance(part, LineString)
            or part.is_empty
            or part.distance(origin) > tolerance
        ):
            continue
        offsets = tuple(
            (x - midpoint[0]) * normal[0] + (y - midpoint[1]) * normal[1]
            for x, y in part.coords
        )
        lower, upper = min(offsets), max(offsets)
        if lower <= tolerance and upper >= -tolerance:
            intervals.append((lower, upper))
    if not intervals:
        return 2.0 * clearance
    connected_width = max(upper for _, upper in intervals) - min(
        lower for lower, _ in intervals
    )
    return max(connected_width, 2.0 * clearance)


def _flow_edges(
    routes: Sequence[AgentIdealPath], walkable: BaseGeometry, clearance: float
) -> tuple[FlowEdge, ...]:
    demand_by_edge: dict[EdgeKey, int] = defaultdict(int)
    for route in routes:
        for key in _route_edge_keys(route):
            demand_by_edge[key] += 1
    result: list[FlowEdge] = []
    for (start, end), demand in sorted(demand_by_edge.items()):
        clear_width = _local_clear_width((start, end), walkable, clearance)
        capacity = clear_width / (2.0 * clearance)
        length = math.dist(start, end)
        delay = length * demand * (1.0 + (demand / capacity) ** 2)
        result.append(FlowEdge(start, end, demand, capacity, delay))
    return tuple(result)


def _blocker_hyperedges(
    routes: Sequence[AgentIdealPath],
    edges: Sequence[FlowEdge],
    obstacles: Sequence[FabricObstacle],
    clearance: float,
) -> tuple[BlockerHyperedge, ...]:
    delay_by_edge = {_edge_key(edge.start, edge.end): edge.delay for edge in edges}
    agents_by_blockers: dict[tuple[int, ...], list[int]] = defaultdict(list)
    delay_by_blockers: dict[tuple[int, ...], float] = defaultdict(float)
    for route in routes:
        corridor = LineString(route.points).buffer(clearance, cap_style="round", join_style="round")
        blocker_ids = tuple(
            obstacle.fabric_id
            for obstacle in sorted(obstacles, key=lambda item: item.fabric_id)
            if corridor.intersects(obstacle.geometry)
        )
        if not blocker_ids:
            continue
        agents_by_blockers[blocker_ids].append(route.agent_index)
        delay_by_blockers[blocker_ids] += sum(
            delay_by_edge[key] for key in _route_edge_keys(route)
        )
    return tuple(
        BlockerHyperedge(
            blocker_ids,
            tuple(sorted(agents_by_blockers[blocker_ids])),
            len(agents_by_blockers[blocker_ids]),
            delay_by_blockers[blocker_ids],
        )
        for blocker_ids in sorted(agents_by_blockers)
    )


def aggregate_flow(
    routes: Sequence[AgentIdealPath],
    walkable: BaseGeometry,
    obstacles: Sequence[FabricObstacle],
    clearance: float,
) -> IdealFlowAnalysis:
    if not math.isfinite(clearance) or clearance <= 0.0:
        raise InvalidClearanceError(clearance)
    ordered_routes = tuple(sorted(routes, key=lambda route: route.agent_index))
    edges = _flow_edges(ordered_routes, walkable, clearance)
    hyperedges = _blocker_hyperedges(ordered_routes, edges, obstacles, clearance)
    return IdealFlowAnalysis(ordered_routes, edges, hyperedges)


def _path_points(route: Route) -> tuple[Point, ...]:
    trace = route.grid_trace
    if trace is None:
        return tuple((*route.waypoints, route.terminal_point))
    raw = (
        trace.entry_connection[0],
        *trace.chain,
        trace.seed_to_approach[1],
        trace.seed_to_exit[1],
    )
    points: list[Point] = []
    for point in raw:
        value = _rounded_point(point)
        if not points or value != points[-1]:
            points.append(value)
    return tuple(points)


def build_ideal_flow(
    drawing: Drawing,
    agents: Sequence[Point],
    selected_exit_ids: Sequence[int | str],
    hazards: Sequence[Hazard],
    constraints: SearchConstraints,
    *,
    checkpoint: Callable[[], None] | None = None,
) -> IdealFlowAnalysis:
    active_checkpoint = checkpoint or (lambda: None)
    ideal: Drawing = {
        **drawing,
        "fabrics": [
            fabric
            for fabric in drawing.get("fabrics", [])
            if not constraints.can_move(fabric["id"])
        ],
    }
    active_checkpoint()
    ideal_area = build_routing_geometry(ideal, GRID_STEP_METERS)
    relocated_agents, _ = relocate_agents(ideal_area, agents)
    router = GridRouter(
        ideal_area,
        hazards,
        parse_exits(ideal, selected_exit_ids),
        step=GRID_STEP_METERS,
        physical_walkable=build_walkable_geometry(ideal),
    )
    routes: list[AgentIdealPath] = []
    for agent_index, position in enumerate(relocated_agents):
        active_checkpoint()
        route = router.plan(position, include_grid_trace=True)
        routes.append(
            AgentIdealPath(
                agent_index,
                str(route.exit_id),
                _path_points(route),
                float(route.total_cost),
            )
        )
    obstacles = tuple(
        FabricObstacle(fabric["id"], geometry(state(fabric)))
        for fabric in drawing.get("fabrics", [])
        if constraints.can_move(fabric["id"])
    )
    active_checkpoint()
    return aggregate_flow(routes, ideal_area, obstacles, GRID_STEP_METERS)
