"""Hazard-aware, deterministic grid routing for the JuPedSim runner."""

from __future__ import annotations

from dataclasses import dataclass, field
import heapq
import math
from types import MappingProxyType
from typing import Any, Callable, Iterable, Sequence

import numpy as np
from shapely import (
    clip_by_rect,
    contains_xy,
    covers,
    distance as geometry_distance,
    intersects,
    linestrings,
    points,
)
from shapely.affinity import rotate
from shapely.geometry import LineString, Point as ShapelyPoint, Polygon, box
from shapely.ops import nearest_points, unary_union
from shapely.prepared import prep
from shapely.strtree import STRtree


GRID_STEP_METERS = 0.25
WALL_TOTAL_WIDTH_METERS = 0.02
EXIT_SEED_MAX_DISTANCE_METERS = GRID_STEP_METERS * math.sqrt(2.0)
# Keep exit-only rounding repair aligned with LayoutGeometryValidator.EPSILON.
EXIT_OUTSIDE_SNAP_TOLERANCE_METERS = 0.1
# 문 크기의 짧은 출구는 기존 경로를 보존한다.
# endpoint-clamped seed 제외는 장출구 경계 구간에만 한정한다.
# 5.0m는 현재 회귀 fixture로 검증된 보수적 적용 경계다.
EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS = 5.0
HAZARD_BOUNDARY_MULTIPLIER = 5.0
HAZARD_CENTER_MULTIPLIER = 500.0
RELOCATION_MARGIN_METERS = 1e-6
_CONNECTOR_VISIBILITY_BATCH_SIZE = 4096
_EPSILON = 1e-9
_MOVES = (
    (-1, -1),
    (0, -1),
    (1, -1),
    (-1, 0),
    (1, 0),
    (-1, 1),
    (0, 1),
    (1, 1),
)

Point = tuple[float, float]
ExitSeed = tuple[int, Point, Point]


@dataclass(frozen=True)
class Hazard:
    center_x: float
    center_y: float
    radius: float


@dataclass(frozen=True)
class AgentRelocation:
    index: int
    origin: Point
    destination: Point


@dataclass(frozen=True)
class Exit:
    id: Any
    start: Point
    end: Point


@dataclass(frozen=True)
class RouteGridTrace:
    """Exact grid geometry needed to certify that an existing route stays open."""

    entry_connection: tuple[Point, Point]
    chain: tuple[Point, ...]
    diagonal_support_nodes: tuple[Point, ...]
    seed_to_approach: tuple[Point, Point]
    seed_to_exit: tuple[Point, Point]


@dataclass(frozen=True)
class Route:
    exit_id: Any
    waypoints: tuple[Point, ...]
    terminal_point: Point
    exit_start: Point
    exit_end: Point
    total_cost: float
    grid_trace: RouteGridTrace | None = field(
        default=None, compare=False, repr=False
    )


class AgentRouteUnreachableError(ValueError):
    """Raised only when an otherwise valid agent cannot connect to the route grid."""


def _point_array(values: Sequence[Point], count: int, label: str) -> np.ndarray:
    result = np.asarray(values, dtype=float)
    if result.shape != (count, 2):
        raise ValueError(f"{label} must have shape ({count}, 2)")
    return result


def hazard_multiplier(point: Point, hazards: Iterable[Hazard]) -> float:
    """Return the maximum radial multiplier at *point*."""
    value = 1.0
    x, y = point
    for hazard in hazards:
        distance = math.hypot(x - hazard.center_x, y - hazard.center_y)
        if distance > hazard.radius:
            continue
        depth = min(1.0, max(0.0, 1.0 - distance / hazard.radius))
        value = max(
            value,
            HAZARD_BOUNDARY_MULTIPLIER
            * (HAZARD_CENTER_MULTIPLIER / HAZARD_BOUNDARY_MULTIPLIER) ** depth,
        )
    return value


def hazard_multipliers(x: np.ndarray, y: np.ndarray, hazards: Sequence[Hazard]) -> np.ndarray:
    """`hazard_multiplier` over whole coordinate arrays, term for term."""
    values = np.ones(x.shape, dtype=float)
    for hazard in hazards:
        distance = np.hypot(x - hazard.center_x, y - hazard.center_y)
        depth = np.minimum(1.0, np.maximum(0.0, 1.0 - distance / hazard.radius))
        inside = HAZARD_BOUNDARY_MULTIPLIER * (
            HAZARD_CENTER_MULTIPLIER / HAZARD_BOUNDARY_MULTIPLIER
        ) ** depth
        values = np.maximum(values, np.where(distance > hazard.radius, 1.0, inside))
    return values


def edge_costs(
    start_x: np.ndarray,
    start_y: np.ndarray,
    end_x: np.ndarray,
    end_y: np.ndarray,
    hazards: Sequence[Hazard],
) -> np.ndarray:
    """`edge_cost` over whole coordinate arrays, term for term.

    The Simpson terms keep the scalar function's grouping so both agree to the
    last bit; `test_route_planner` pins that down.
    """
    length = np.hypot(end_x - start_x, end_y - start_y)
    if not hazards:
        return length
    return length / 6.0 * (
        hazard_multipliers(start_x, start_y, hazards)
        + 4.0 * hazard_multipliers((start_x + end_x) / 2.0, (start_y + end_y) / 2.0, hazards)
        + hazard_multipliers(end_x, end_y, hazards)
    )


def edge_cost(start: Point, end: Point, hazards: Iterable[Hazard]) -> float:
    """Integrate the radial cost over one short grid edge with Simpson's rule."""
    hazards = tuple(hazards)
    length = math.dist(start, end)
    if not hazards:
        return length
    midpoint = ((start[0] + end[0]) / 2.0, (start[1] + end[1]) / 2.0)
    return length / 6.0 * (
        hazard_multiplier(start, hazards)
        + 4.0 * hazard_multiplier(midpoint, hazards)
        + hazard_multiplier(end, hazards)
    )


def build_walkable_geometry(drawing: dict[str, Any]):
    """Build the outside polygon minus thin walls and rectangular obstacles."""
    return _build_geometry(drawing, clearance=0.0)


def build_routing_geometry(drawing: dict[str, Any], clearance: float):
    """Build center-point routing space with clearance for an agent disk."""
    if not math.isfinite(clearance) or clearance <= 0:
        raise ValueError("routing clearance must be positive")
    return _build_geometry(drawing, clearance=clearance)


def _build_geometry(drawing: dict[str, Any], clearance: float):
    outside = _outside_polygon(drawing)
    if clearance:
        outside = outside.buffer(-clearance)
        if outside.is_empty or outside.area <= 0:
            raise ValueError("drawing outside boundary leaves no routing area for agent clearance")

    obstacles = []
    for wall in drawing.get("walls", []):
        start, end = _line_points(wall, "drawing.walls")
        if start != end:
            obstacles.append(
                LineString((start, end)).buffer(
                    clearance or WALL_TOTAL_WIDTH_METERS / 2.0,
                    cap_style="round" if clearance else "flat",
                    join_style="round" if clearance else "mitre",
                )
            )
    for key in ("pillars", "fabrics"):
        rectangles = (_rectangle(item, f"drawing.{key}") for item in drawing.get(key, []))
        obstacles.extend(
            rectangle.buffer(clearance) if clearance else rectangle for rectangle in rectangles
        )

    walkable = outside if not obstacles else outside.difference(unary_union(obstacles))
    if walkable.is_empty or walkable.area <= 0:
        area = "routing area for agent clearance" if clearance else "walkable area"
        raise ValueError(f"drawing obstacles leave no {area}")
    return walkable


def _outside_polygon(drawing: dict[str, Any]):
    boundary = [_point(item, "drawing.outsideBoundary") for item in drawing.get("outsideBoundary", [])]
    if len(boundary) < 3:
        raise ValueError("drawing.outsideBoundary must contain at least three points")
    outside = Polygon(boundary)
    if not outside.is_valid or outside.is_empty or outside.area <= 0:
        raise ValueError("drawing.outsideBoundary must be a valid polygon")
    return outside


def select_accessible_component(
    walkable,
    agents: Sequence[Point],
    exits: Sequence[Exit],
):
    """Keep the one connected area containing every agent and selected exit."""
    selected = select_agent_component(walkable, agents)
    for exit_ in exits:
        if not selected.intersects(LineString((exit_.start, exit_.end))):
            raise ValueError(
                f"selected exit {exit_.id!r} is not connected to the agents' walkable area"
            )
    return selected


def select_agent_component(area, agents: Sequence[Point]):
    """Keep the one connected area containing every agent center."""
    components = (
        list(area.geoms) if area.geom_type == "MultiPolygon" else [area]
    )
    agent_component_indexes = set()
    for index, position in enumerate(agents):
        point = ShapelyPoint(position)
        matches = [
            component_index
            for component_index, component in enumerate(components)
            if component.covers(point)
        ]
        if len(matches) != 1:
            raise ValueError(f"agent {index} is outside the walkable area")
        agent_component_indexes.add(matches[0])

    if len(agent_component_indexes) != 1:
        raise ValueError("agents are distributed across disconnected walkable areas")
    return components[agent_component_indexes.pop()]


def split_agent_components(area, agents: Sequence[Point]):
    """Group indexed agents by the one routing component containing each center."""
    components = list(area.geoms) if area.geom_type == "MultiPolygon" else [area]
    grouped: dict[int, list[tuple[int, Point]]] = {}
    for index, position in enumerate(agents):
        point = ShapelyPoint(position)
        matches = [
            component_index
            for component_index, component in enumerate(components)
            if component.covers(point)
        ]
        if len(matches) != 1:
            raise ValueError(f"agent {index} is outside the walkable area")
        grouped.setdefault(matches[0], []).append((index, position))
    return tuple((components[index], tuple(grouped[index])) for index in sorted(grouped))


def relocate_agents(
    routing_area,
    agents: Sequence[Point],
    margin: float = RELOCATION_MARGIN_METERS,
) -> tuple[tuple[Point, ...], tuple[AgentRelocation, ...]]:
    """Move agents that overlap an obstacle to the nearest point inside *routing_area*.

    *routing_area* must come from ``build_routing_geometry``, so every point in it
    already clears each obstacle by the agent radius. Agents already inside keep
    their exact coordinates, which makes this a no-op for an original layout.
    """
    if not agents:
        return (), ()
    x_values = np.fromiter((point[0] for point in agents), float, len(agents))
    y_values = np.fromiter((point[1] for point in agents), float, len(agents))
    inside = np.asarray(covers(routing_area, points(x_values, y_values)), dtype=bool)
    positions = [(float(x), float(y)) for x, y in zip(x_values, y_values)]
    relocations = []
    for index in np.flatnonzero(~inside).tolist():
        origin = positions[index]
        boundary = nearest_points(routing_area, ShapelyPoint(origin))[0]
        destination = (boundary.x, boundary.y)
        # Step just past the boundary so later covers() calls are never boundary calls.
        length = math.dist(origin, destination)
        if length > _EPSILON:
            scale = (length + margin) / length
            nudged = (
                origin[0] + (destination[0] - origin[0]) * scale,
                origin[1] + (destination[1] - origin[1]) * scale,
            )
            if routing_area.covers(ShapelyPoint(nudged)):
                destination = nudged
        if not routing_area.covers(ShapelyPoint(destination)):
            raise ValueError(f"agent {index} cannot be relocated into the walkable area")
        positions[index] = destination
        relocations.append(AgentRelocation(index, origin, destination))
    return tuple(positions), tuple(relocations)


def containing_component(area, contained):
    """Return the physical component containing a routing component."""
    components = list(area.geoms) if area.geom_type == "MultiPolygon" else [area]
    marker = contained.representative_point()
    matches = [component for component in components if component.covers(marker)]
    if len(matches) != 1:
        raise ValueError("routing area is not contained in exactly one physical walkable area")
    return matches[0]


def parse_hazards(items: Sequence[dict[str, Any]]) -> tuple[Hazard, ...]:
    hazards = []
    for index, item in enumerate(items):
        try:
            hazard = Hazard(float(item["centerX"]), float(item["centerY"]), float(item["radius"]))
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError(f"hazards[{index}] must contain numeric centerX, centerY and radius") from exc
        if not all(math.isfinite(value) for value in (hazard.center_x, hazard.center_y, hazard.radius)):
            raise ValueError(f"hazards[{index}] values must be finite")
        if hazard.radius <= 0:
            raise ValueError(f"hazards[{index}].radius must be positive")
        hazards.append(hazard)
    return tuple(hazards)


def _snap_exit_point_into_outside(point: Point, outside) -> Point:
    target = ShapelyPoint(point)
    if (
        outside.covers(target)
        or outside.distance(target)
        > EXIT_OUTSIDE_SNAP_TOLERANCE_METERS + _EPSILON
    ):
        return point
    snapped = nearest_points(target, outside.boundary)[1]
    return (float(snapped.x), float(snapped.y))


def _exit_points(item: dict[str, Any], label: str, outside) -> tuple[Point, Point]:
    start, end = _line_points(item, label)
    start = _snap_exit_point_into_outside(start, outside)
    end = _snap_exit_point_into_outside(end, outside)
    if start == end:
        raise ValueError(f"{label} must have positive length")
    return start, end


def parse_exits(drawing: dict[str, Any], selected_exit_ids: Sequence[Any]) -> tuple[Exit, ...]:
    selected = {_id_key(value) for value in selected_exit_ids}
    outside = _outside_polygon(drawing)
    exits = []
    for index, item in enumerate(drawing.get("exits", [])):
        if "id" not in item:
            raise ValueError(f"drawing.exits[{index}].id is required")
        if _id_key(item["id"]) not in selected:
            continue
        start, end = _exit_points(item, f"drawing.exits[{index}]", outside)
        exits.append(Exit(item["id"], start, end))
    found = {_id_key(item.id) for item in exits}
    missing = sorted(selected - found)
    if missing:
        raise ValueError(f"selectedExitIds contain unknown exits: {', '.join(missing)}")
    if not exits:
        raise ValueError("at least one selected exit is required")
    exits.sort(key=lambda item: _id_key(item.id))
    return tuple(exits)


def parse_exit_segments(drawing: dict[str, Any]) -> tuple[tuple[Point, Point], ...]:
    """Parse every physical exit segment, including exits not selected for routing."""
    outside = _outside_polygon(drawing)
    segments = []
    for index, item in enumerate(drawing.get("exits", [])):
        start, end = _exit_points(item, f"drawing.exits[{index}]", outside)
        segments.append((start, end))
    return tuple(segments)


class GridRouter:
    """One global reverse-Dijkstra field; each planned route is immutable."""

    def __init__(
        self,
        walkable,
        hazards: Sequence[Hazard],
        exits: Sequence[Exit],
        step: float = GRID_STEP_METERS,
        physical_walkable=None,
        exit_clearance: float = 0.3,
    ) -> None:
        if not math.isfinite(step) or step <= 0:
            raise ValueError("grid step must be positive")
        if not exits:
            raise ValueError("at least one exit is required")
        self.walkable = walkable
        self._prepared_walkable = prep(walkable)
        self.physical_walkable = physical_walkable if physical_walkable is not None else walkable
        self._prepared_physical_walkable = prep(self.physical_walkable)
        self.hazards = tuple(hazards)
        self._edge_cost = (
            (lambda start, end: edge_cost(start, end, self.hazards))
            if self.hazards
            else math.dist
        )
        self._move_costs = {
            move: step * (math.sqrt(2.0) if move[0] and move[1] else 1.0)
            for move in _MOVES
        }
        self.exits = tuple(exits)
        self._exit_labels = MappingProxyType(
            {_id_key(exit_.id): label for label, exit_ in enumerate(self.exits)}
        )
        self.step = step
        self.exit_clearance = exit_clearance

        min_x, min_y, max_x, max_y = walkable.bounds
        self.origin_x = math.floor(min_x / step) * step
        self.origin_y = math.floor(min_y / step) * step
        self.width = int(math.ceil((max_x - self.origin_x) / step)) + 1
        self.height = int(math.ceil((max_y - self.origin_y) / step)) + 1
        if self.width * self.height > 10_000_000:
            raise ValueError("drawing is too large for the 0.25m routing grid")

        x_values = self.origin_x + np.arange(self.width, dtype=float) * step
        y_values = self.origin_y + np.arange(self.height, dtype=float) * step
        grid_x, grid_y = np.meshgrid(x_values, y_values)
        self._x = grid_x.ravel()
        self._y = grid_y.ravel()
        # contains_xy keeps steering targets off geometry boundaries. The covers
        # fallback retains valid points in extremely narrow numerical slivers.
        self.valid = np.asarray(contains_xy(walkable, self._x, self._y), dtype=bool)
        if not self.valid.any():
            self.valid = np.asarray(covers(walkable, points(self._x, self._y)), dtype=bool)
        self.distance = np.full(self.width * self.height, np.inf, dtype=float)
        self.next_node = np.full(self.width * self.height, -1, dtype=np.int64)
        self.exit_label = np.full(self.width * self.height, -1, dtype=np.int32)
        self.terminal_x = np.full(self.width * self.height, np.nan, dtype=float)
        self.terminal_y = np.full(self.width * self.height, np.nan, dtype=float)
        self.approach_x = np.full(self.width * self.height, np.nan, dtype=float)
        self.approach_y = np.full(self.width * self.height, np.nan, dtype=float)
        self._plan_cache: dict[Point, tuple[Any, ...]] = {}
        self._edge_costs = self._build_edge_costs()
        self._grid_edges = self._build_grid_edges()
        self._neighbor_nodes = self._build_neighbor_nodes()
        self._build_cost_field()
        self._reachable = self.valid & np.isfinite(self.distance)
        self._has_reachable = bool(self._reachable.any())

    def derive(self, walkable, *, physical_walkable=None, changed_bounds=None) -> GridRouter:
        """Reuse this routing field after obstacles are removed or added.

        The incremental path is exact for a pure expansion or contraction that
        keeps the same grid extent. Unsupported geometry changes use the normal
        constructor so callers never need two paths.

        *changed_bounds* is an optional ``(min_x, min_y, max_x, max_y)`` window
        that must contain every point where the new areas differ from the
        current ones. Comparing only that window gives the same answer as
        comparing the whole layout, at a fraction of the cost when the layout
        holds many obstacles and only one of them moved.
        """
        physical_walkable = (
            self.physical_walkable if physical_walkable is None else physical_walkable
        )

        min_x, min_y, max_x, max_y = walkable.bounds
        origin_x = math.floor(min_x / self.step) * self.step
        origin_y = math.floor(min_y / self.step) * self.step
        width = int(math.ceil((max_x - origin_x) / self.step)) + 1
        height = int(math.ceil((max_y - origin_y) / self.step)) + 1

        def cold() -> GridRouter:
            return GridRouter(
                walkable,
                self.hazards,
                self.exits,
                step=self.step,
                physical_walkable=physical_walkable,
                exit_clearance=self.exit_clearance,
            )

        if (
            origin_x != self.origin_x
            or origin_y != self.origin_y
            or width != self.width
            or height != self.height
        ):
            return cold()

        # Outside changed_bounds the areas are identical, so clipping every
        # comparison to that window leaves each result below unchanged.
        if changed_bounds is None:
            old_area, new_area = self.walkable, walkable
            old_physical, new_physical = self.physical_walkable, physical_walkable
        else:
            old_area = clip_by_rect(self.walkable, *changed_bounds)
            new_area = clip_by_rect(walkable, *changed_bounds)
            old_physical = clip_by_rect(self.physical_walkable, *changed_bounds)
            new_physical = clip_by_rect(physical_walkable, *changed_bounds)

        expands = (
            old_area.difference(new_area).area <= _EPSILON
            and old_physical.difference(new_physical).area <= _EPSILON
        )
        contracts = (
            new_area.difference(old_area).area <= _EPSILON
            and new_physical.difference(old_physical).area <= _EPSILON
        )
        mixed = not expands and not contracts
        changed = new_area.symmetric_difference(old_area)
        physical_changed = new_physical.symmetric_difference(old_physical)
        derived = object.__new__(GridRouter)
        derived.__dict__ = self.__dict__.copy()
        derived._plan_cache = self._plan_cache.copy()
        derived.walkable = walkable
        derived._prepared_walkable = prep(walkable)
        derived.physical_walkable = physical_walkable
        derived._prepared_physical_walkable = prep(physical_walkable)
        derived.valid = self.valid.copy()
        derived._grid_edges = {
            direction: values.copy() for direction, values in self._grid_edges.items()
        }
        derived._neighbor_nodes = self._neighbor_nodes.copy()
        for name in (
            "distance",
            "next_node",
            "exit_label",
            "terminal_x",
            "terminal_y",
            "approach_x",
            "approach_y",
        ):
            setattr(derived, name, getattr(self, name).copy())

        opened_edges: list[tuple[int, int]] = []
        closed_edges: list[tuple[int, int]] = []
        gained_nodes = np.empty(0, dtype=np.int64)
        lost_nodes = np.empty(0, dtype=np.int64)
        if not changed.is_empty:
            changed_min_x, changed_min_y, changed_max_x, changed_max_y = changed.bounds
            local = np.flatnonzero(
                (self._x >= changed_min_x - self.step - _EPSILON)
                & (self._x <= changed_max_x + self.step + _EPSILON)
                & (self._y >= changed_min_y - self.step - _EPSILON)
                & (self._y <= changed_max_y + self.step + _EPSILON)
            )
            new_valid = np.asarray(
                contains_xy(walkable, self._x[local], self._y[local]), dtype=bool
            )
            previous_valid = derived.valid[local]
            gained_nodes = local[new_valid & ~previous_valid]
            lost_nodes = local[previous_valid & ~new_valid]
            derived.valid[local] = new_valid

            move_indexes = {move: index for index, move in enumerate(_MOVES)}
            for (dx, dy), old_values in self._grid_edges.items():
                rows, columns = np.divmod(local, self.width)
                eligible = (
                    (columns + dx >= 0)
                    & (columns + dx < self.width)
                    & (rows + dy >= 0)
                    & (rows + dy < self.height)
                )
                sources = local[eligible]
                destinations = sources + dy * self.width + dx
                candidates = derived.valid[sources] & derived.valid[destinations]
                if dx and dy:
                    candidates &= derived.valid[sources + dx]
                    candidates &= derived.valid[sources + dy * self.width]

                clear = np.zeros(sources.size, dtype=bool)
                candidate_offsets = np.flatnonzero(candidates)
                if candidate_offsets.size:
                    candidate_sources = sources[candidate_offsets]
                    candidate_destinations = destinations[candidate_offsets]
                    coordinates = np.empty((candidate_offsets.size, 2, 2), dtype=float)
                    coordinates[:, 0, 0] = self._x[candidate_sources]
                    coordinates[:, 0, 1] = self._y[candidate_sources]
                    coordinates[:, 1, 0] = self._x[candidate_destinations]
                    coordinates[:, 1, 1] = self._y[candidate_destinations]
                    clear[candidate_offsets] = np.asarray(
                        covers(walkable, linestrings(coordinates)), dtype=bool
                    )

                previous = old_values[sources]
                derived._grid_edges[(dx, dy)][sources] = clear
                derived._neighbor_nodes[sources, move_indexes[(dx, dy)]] = np.where(
                    clear, destinations, -1
                )
                derived._neighbor_nodes[
                    destinations, move_indexes[(-dx, -dy)]
                ] = np.where(clear, sources, -1)
                for source, destination in zip(
                    sources[clear & ~previous], destinations[clear & ~previous]
                ):
                    opened_edges.append((int(source), int(destination)))
                for source, destination in zip(
                    sources[previous & ~clear], destinations[previous & ~clear]
                ):
                    closed_edges.append((int(source), int(destination)))

        if not derived.valid.any():
            return cold()
        if expands and (lost_nodes.size or closed_edges):
            return cold()
        if contracts and (gained_nodes.size or opened_edges):
            return cold()
        if mixed and derived.hazards:
            # ponytail: rebuilding only the hazard-weighted cost field preserves
            # byte-level determinism; specialize it if mixed hazard moves become hot.
            derived.distance.fill(np.inf)
            derived.next_node.fill(-1)
            derived.exit_label.fill(-1)
            derived.terminal_x.fill(np.nan)
            derived.terminal_y.fill(np.nan)
            derived.approach_x.fill(np.nan)
            derived.approach_y.fill(np.nan)
            derived._build_cost_field()
            return derived

        seeds: list[tuple[int, int, Point, Point, float]] = []
        seeds_by_label = []
        seeded_exit_ids = set()
        for label, exit_ in enumerate(derived.exits):
            start, end = usable_exit_segment(exit_, derived.exit_clearance)
            seed_region = LineString((start, end)).buffer(
                derived.exit_clearance
                + EXIT_SEED_MAX_DISTANCE_METERS
                + _EPSILON
            )
            if changed.disjoint(seed_region) and physical_changed.disjoint(seed_region):
                exit_seeds = self._exit_seed_records[label]
            else:
                exit_seeds = tuple(derived._exit_seeds(exit_))
            seeds_by_label.append(exit_seeds)
            if exit_seeds:
                seeded_exit_ids.add(_id_key(exit_.id))
            for node, target, approach in exit_seeds:
                seeds.append(
                    (
                        label,
                        node,
                        target,
                        approach,
                        derived._edge_cost(derived._point(node), target),
                    )
                )
        if not seeds:
            return cold()

        heap: list[tuple[float, int, int]] = []
        if expands:
            frontier = {
                node
                for edge in opened_edges
                for node in edge
                if math.isfinite(float(derived.distance[node]))
            }
            for node in frontier:
                heapq.heappush(
                    heap,
                    (float(derived.distance[node]), int(derived.exit_label[node]), node),
                )

            for label, node, target, approach, seed_cost in seeds:
                old_cost = float(derived.distance[node])
                old_label = int(derived.exit_label[node])
                improves = seed_cost + _EPSILON < old_cost
                ties_better = abs(seed_cost - old_cost) <= _EPSILON and (
                    old_label < 0
                    or label < old_label
                    or (label == old_label and int(derived.next_node[node]) >= 0)
                )
                same_seed = (
                    abs(seed_cost - old_cost) <= _EPSILON
                    and label == old_label
                    and int(derived.next_node[node]) == -1
                )
                if same_seed and (
                    target
                    != (
                        float(derived.terminal_x[node]),
                        float(derived.terminal_y[node]),
                    )
                    or approach
                    != (
                        float(derived.approach_x[node]),
                        float(derived.approach_y[node]),
                    )
                ):
                    return cold()
                if improves or ties_better:
                    derived.distance[node] = seed_cost
                    derived.exit_label[node] = label
                    derived.next_node[node] = -1
                    derived.terminal_x[node] = target[0]
                    derived.terminal_y[node] = target[1]
                    derived.approach_x[node] = approach[0]
                    derived.approach_y[node] = approach[1]
                    heapq.heappush(heap, (seed_cost, label, node))
            if not self.seeded_exit_ids.issubset(seeded_exit_ids):
                return cold()
        else:
            affected = np.zeros(self.valid.size, dtype=bool)
            affected[lost_nodes] = True
            for source, destination in closed_edges:
                if int(self.next_node[source]) == destination:
                    affected[source] = True
                if int(self.next_node[destination]) == source:
                    affected[destination] = True

            seed_by_node_and_label = {
                (node, label): (target, approach, seed_cost)
                for label, node, target, approach, seed_cost in seeds
            }
            old_seed_nodes = np.flatnonzero(
                np.isfinite(self.distance)
                & (self.next_node == -1)
                & (self.exit_label >= 0)
            )
            for node in old_seed_nodes:
                label = int(self.exit_label[node])
                candidate = seed_by_node_and_label.get((int(node), label))
                if candidate is None or candidate != (
                    (
                        float(self.terminal_x[node]),
                        float(self.terminal_y[node]),
                    ),
                    (
                        float(self.approach_x[node]),
                        float(self.approach_y[node]),
                    ),
                    float(self.distance[node]),
                ):
                    affected[node] = True

            while True:
                depends_on_affected = np.zeros(self.next_node.size, dtype=bool)
                has_next = self.next_node >= 0
                depends_on_affected[has_next] = affected[self.next_node[has_next]]
                newly_affected = depends_on_affected & ~affected
                if not newly_affected.any():
                    break
                affected |= newly_affected

            derived.distance[affected] = np.inf
            derived.next_node[affected] = -1
            derived.exit_label[affected] = -1
            derived.terminal_x[affected] = np.nan
            derived.terminal_y[affected] = np.nan
            derived.approach_x[affected] = np.nan
            derived.approach_y[affected] = np.nan

            for label, node, target, approach, seed_cost in seeds:
                current_cost = float(derived.distance[node])
                current_label = int(derived.exit_label[node])
                current_next = int(derived.next_node[node])
                if seed_cost + _EPSILON < current_cost or (
                    abs(seed_cost - current_cost) <= _EPSILON
                    and (
                        current_label < 0
                        or label < current_label
                        or (label == current_label and current_next >= 0)
                    )
                ):
                    derived.distance[node] = seed_cost
                    derived.exit_label[node] = label
                    derived.next_node[node] = -1
                    derived.terminal_x[node] = target[0]
                    derived.terminal_y[node] = target[1]
                    derived.approach_x[node] = approach[0]
                    derived.approach_y[node] = approach[1]
                    heapq.heappush(heap, (seed_cost, label, node))

            frontier = set()
            for (dx, dy), clear in derived._grid_edges.items():
                sources = np.flatnonzero(clear)
                destinations = sources + dy * self.width + dx
                crossing = affected[sources] ^ affected[destinations]
                for source, destination in zip(sources[crossing], destinations[crossing]):
                    node = int(destination if affected[source] else source)
                    if math.isfinite(float(derived.distance[node])):
                        frontier.add(node)
            for source, destination in opened_edges:
                if math.isfinite(float(derived.distance[source])):
                    frontier.add(source)
                if math.isfinite(float(derived.distance[destination])):
                    frontier.add(destination)
            for node in frontier:
                heapq.heappush(
                    heap,
                    (float(derived.distance[node]), int(derived.exit_label[node]), node),
                )

        derived._exit_seed_records = tuple(seeds_by_label)
        derived._exit_seed_cache = MappingProxyType(
            {
                _id_key(exit_.id): tuple(seeds_by_label[label])
                for label, exit_ in enumerate(derived.exits)
            }
        )
        derived.seeded_exit_ids = frozenset(seeded_exit_ids)
        derived._propagate_cost_field(heap)
        return derived

    def _entry(self, start: Point) -> tuple[Point, float, int, int]:
        """Where an agent joins the grid, and what the rest of its route costs."""
        point = (float(start[0]), float(start[1]))
        if not all(math.isfinite(value) for value in point):
            raise ValueError("agent coordinates must be finite")
        if not self._prepared_walkable.covers(ShapelyPoint(point)):
            raise ValueError(f"agent at {point} is outside the walkable area")

        reachable = self._reachable
        if not self._has_reachable:
            raise ValueError("no selected exit is reachable")
        local = self._local_nodes(point, reachable)
        best = self._best_connector(point, local)
        if best is None:
            candidates = np.flatnonzero(reachable)
            squared = (self._x[candidates] - point[0]) ** 2 + (self._y[candidates] - point[1]) ** 2
            count = min(64, candidates.size)
            best = self._best_connector(
                point, candidates[np.argpartition(squared, count - 1)[:count]].tolist()
            )
            if best is None:
                # A safe corridor can be narrower than one grid cell. Search farther
                # connectors in bounded-memory batches only after the 64-node path fails.
                best = self._expanded_connector(point, candidates, squared)
        if best is None:
            raise AgentRouteUnreachableError("agent cannot connect to the routing grid")

        total_cost, exit_label, route_node = best
        return point, total_cost, exit_label, route_node

    def plan_cost(self, start: Point) -> tuple[float, Any]:
        """What a route costs and where it leaves, without walking the path.

        A layout search compares thousands of layouts and reads only these two
        numbers per agent. Building the waypoints costs far more than finding
        them: every kept point is re-checked with a shapely visibility test.
        """
        _, total_cost, exit_label, _ = self._entry(start)
        return total_cost, self.exits[exit_label].id

    def plan(self, start: Point, *, include_grid_trace: bool = False) -> Route:
        point, total_cost, exit_label, route_node = self._entry(start)
        node = route_node
        chain = []
        visited = set()
        while node >= 0:
            if node in visited:
                raise RuntimeError("routing field contains a cycle")
            visited.add(node)
            chain.append(node)
            node = int(self.next_node[node])
        chain_key = tuple(chain)
        approach = (float(self.approach_x[route_node]), float(self.approach_y[route_node]))
        terminal = (float(self.terminal_x[route_node]), float(self.terminal_y[route_node]))
        cached = self._plan_cache.get(point)
        cache_key = (
            route_node,
            float(self.distance[route_node]),
            exit_label,
            chain_key,
            terminal,
            approach,
            total_cost,
        )
        if cached is not None and cached[:-1] == cache_key:
            cached_route = cached[-1]
            if not include_grid_trace or cached_route.grid_trace is not None:
                return cached_route

        path = [point]
        for node in chain:
            node_point = self._point(node)
            if math.dist(path[-1], node_point) > _EPSILON:
                path.append(node_point)
        if math.dist(path[-1], approach) > _EPSILON:
            path.append(approach)
        exit_start, exit_end = usable_exit_segment(
            self.exits[exit_label], self.exit_clearance
        )
        grid_trace = None
        if include_grid_trace:
            chain_points = tuple(self._point(node) for node in chain)
            diagonal_support_nodes = []
            for start_node, end_node in zip(chain, chain[1:]):
                start_row, start_column = divmod(start_node, self.width)
                end_row, end_column = divmod(end_node, self.width)
                if start_row != end_row and start_column != end_column:
                    diagonal_support_nodes.extend(
                        (
                            self._point(start_row * self.width + end_column),
                            self._point(end_row * self.width + start_column),
                        )
                    )
            grid_trace = RouteGridTrace(
                entry_connection=(point, chain_points[0]),
                chain=chain_points,
                diagonal_support_nodes=tuple(diagonal_support_nodes),
                seed_to_approach=(chain_points[-1], approach),
                seed_to_exit=(chain_points[-1], terminal),
            )
        route = Route(
            exit_id=self.exits[exit_label].id,
            waypoints=tuple(_simplify_collinear(path, self.can_connect)),
            terminal_point=terminal,
            exit_start=exit_start,
            exit_end=exit_end,
            total_cost=total_cost,
            grid_trace=grid_trace,
        )
        self._plan_cache[point] = (*cache_key, route)
        return route

    def recommended_position(
        self,
        start: Point,
        other_agents: Sequence[Point],
        exit_segments: Sequence[tuple[Point, Point]],
        agent_spacing: float,
    ) -> Point | None:
        """Return the deterministic nearest safe reachable grid point, if one exists."""
        if not math.isfinite(agent_spacing) or agent_spacing <= 0:
            raise ValueError("agent spacing must be positive")

        exit_values = np.asarray(exit_segments, dtype=float)
        exit_geometries = (
            linestrings(exit_values) if exit_values.size else np.empty(0, dtype=object)
        )
        exit_tree = STRtree(exit_geometries) if exit_geometries.size else None
        agent_values = np.asarray(other_agents, dtype=float)
        agent_geometries = (
            points(agent_values[:, 0], agent_values[:, 1])
            if agent_values.size
            else np.empty(0, dtype=object)
        )
        agent_tree = STRtree(agent_geometries) if agent_geometries.size else None
        try:
            numeric_exit_ids = [
                exit_.id
                for exit_ in self.exits
                if not isinstance(exit_.id, bool) and isinstance(exit_.id, (int, float))
            ]
            if len(numeric_exit_ids) != len(self.exits):
                raise TypeError
            numeric_order = {
                exit_id: rank for rank, exit_id in enumerate(sorted(set(numeric_exit_ids)))
            }
            exit_keys = np.asarray(
                [numeric_order[exit_id] for exit_id in numeric_exit_ids], dtype=np.int64
            )
        except (TypeError, ValueError):
            exit_keys = np.arange(len(self.exits), dtype=np.int64)

        start_x, start_y = float(start[0]), float(start[1])
        reachable = self.valid & np.isfinite(self.distance)
        best: tuple[float, float, int, int] | None = None
        # ponytail: keep this rare failure-path scan uncached; share trees/masks only
        # if production evidence shows multiple route failures per request are common.
        for offset in range(0, reachable.size, _CONNECTOR_VISIBILITY_BATCH_SIZE):
            end = min(offset + _CONNECTOR_VISIBILITY_BATCH_SIZE, reachable.size)
            nodes = np.flatnonzero(reachable[offset:end]) + offset
            if not nodes.size:
                continue
            candidate_points = points(self._x[nodes], self._y[nodes])
            allowed = np.ones(nodes.size, dtype=bool)

            if exit_tree is not None:
                pairs = exit_tree.query(
                    candidate_points,
                    predicate="dwithin",
                    distance=self.exit_clearance,
                )
                if pairs.shape[1]:
                    too_close = np.asarray(
                        geometry_distance(
                            candidate_points[pairs[0]], exit_geometries[pairs[1]]
                        )
                        < self.exit_clearance,
                        dtype=bool,
                    )
                    allowed[pairs[0][too_close]] = False

            if agent_tree is not None:
                pairs = agent_tree.query(
                    candidate_points,
                    predicate="dwithin",
                    distance=agent_spacing,
                )
                if pairs.shape[1]:
                    too_close = np.asarray(
                        geometry_distance(
                            candidate_points[pairs[0]], agent_geometries[pairs[1]]
                        )
                        < agent_spacing,
                        dtype=bool,
                    )
                    allowed[pairs[0][too_close]] = False

            nodes = nodes[allowed]
            if not nodes.size:
                continue
            squared = (self._x[nodes] - start_x) ** 2 + (self._y[nodes] - start_y) ** 2
            node_exit_keys = exit_keys[self.exit_label[nodes]]
            order = np.lexsort(
                (nodes, node_exit_keys, self.distance[nodes], squared)
            )
            position = int(order[0])
            node = int(nodes[position])
            candidate = (
                float(squared[position]),
                float(self.distance[node]),
                int(exit_keys[int(self.exit_label[node])]),
                node,
            )
            if best is None or candidate < best:
                best = candidate

        if best is None:
            return None
        recommendation = self._point(best[3])
        try:
            self.plan(recommendation)
        except AgentRouteUnreachableError:
            return None
        return recommendation

    def _build_cost_field(self) -> None:
        heap: list[tuple[float, int, int]] = []
        seed_count = 0
        seeds_by_exit: dict[str, tuple[ExitSeed, ...]] = {}
        seeds_by_label = []
        seeded_exit_ids = set()
        for label, exit_ in enumerate(self.exits):
            exit_seeds = tuple(self._exit_seeds(exit_))
            seeds_by_exit[_id_key(exit_.id)] = exit_seeds
            seeds_by_label.append(exit_seeds)
            if exit_seeds:
                seeded_exit_ids.add(_id_key(exit_.id))
            for node, target, approach in exit_seeds:
                seed_count += 1
                seed_cost = self._edge_cost(self._point(node), target)
                current = (float(self.distance[node]), int(self.exit_label[node]))
                if seed_cost + _EPSILON < current[0] or (
                    abs(seed_cost - current[0]) <= _EPSILON
                    and (current[1] < 0 or label < current[1])
                ):
                    self.distance[node] = seed_cost
                    self.exit_label[node] = label
                    self.terminal_x[node] = target[0]
                    self.terminal_y[node] = target[1]
                    self.approach_x[node] = approach[0]
                    self.approach_y[node] = approach[1]
                    heapq.heappush(heap, (seed_cost, label, node))

        self._exit_seed_records = tuple(seeds_by_label)
        self._exit_seed_cache = MappingProxyType(seeds_by_exit)
        self.seeded_exit_ids = frozenset(seeded_exit_ids)
        if seed_count == 0:
            raise ValueError("no selected exit is reachable from this walkable component")

        self._propagate_cost_field(heap)

    def _propagate_cost_field(self, heap: list[tuple[float, int, int]]) -> None:
        neighbor_nodes = self._neighbor_nodes
        edge_costs = self._edge_costs
        distance = self.distance
        next_node = self.next_node
        exit_label = self.exit_label
        terminal_x = self.terminal_x
        terminal_y = self.terminal_y
        approach_x = self.approach_x
        approach_y = self.approach_y
        heappop = heapq.heappop
        heappush = heapq.heappush

        while heap:
            current_cost, label, node = heappop(heap)
            if current_cost > distance[node] + _EPSILON or label != exit_label[node]:
                continue
            for move_index, step_cost in enumerate(edge_costs[node]):
                neighbor = int(neighbor_nodes[node, move_index])
                if neighbor < 0:
                    continue
                next_cost = current_cost + step_cost
                old_cost = float(distance[neighbor])
                update = next_cost + _EPSILON < old_cost
                if not update and abs(next_cost - old_cost) <= _EPSILON:
                    old_label = int(exit_label[neighbor])
                    old_next = int(next_node[neighbor])
                    update = (
                        old_label < 0
                        or label < old_label
                        or (label == old_label and node < old_next)
                        or (
                            label == old_label
                            and node == old_next
                            and (
                                terminal_x[neighbor] != terminal_x[node]
                                or terminal_y[neighbor] != terminal_y[node]
                                or approach_x[neighbor] != approach_x[node]
                                or approach_y[neighbor] != approach_y[node]
                            )
                        )
                    )
                if update:
                    distance[neighbor] = next_cost
                    exit_label[neighbor] = label
                    next_node[neighbor] = node
                    terminal_x[neighbor] = terminal_x[node]
                    terminal_y[neighbor] = terminal_y[node]
                    approach_x[neighbor] = approach_x[node]
                    approach_y[neighbor] = approach_y[node]
                    heappush(heap, (next_cost, label, neighbor))

    def recovery_seed_neighbors(
        self, exit_id: Any, current_approach: Point
    ) -> tuple[ExitSeed, ExitSeed, ExitSeed] | None:
        """Return the adjacent seed neighborhood of *current_approach* for *exit_id*.

        The exit id is canonicalized with the same rules as exit parsing. The
        result is ``(prev, current, next)`` where *current* is the cached seed
        whose target lies nearest to the axis projection of *current_approach*
        and *prev* / *next* are the immediately adjacent seeds along the exit.
        Neighbors missing at the row edges are ``None``; the whole result is
        ``None`` when the exit has fewer than three distinct seed targets. No
        seed order wraps around and no coordinates are invented. Reachability
        validation belongs to the caller (the runner), which can report the
        exact failure reason per agent.
        """
        point = (float(current_approach[0]), float(current_approach[1]))
        if not all(math.isfinite(value) for value in point):
            raise ValueError("approach coordinates must be finite")
        exit_key = _id_key(exit_id)
        seeds = self._exit_seed_cache.get(exit_key)
        if not seeds:
            return None
        exit_ = self.exits[self._exit_labels[exit_key]]
        ordered = self._order_seeds_by_projection(exit_, seeds)
        if len({seed[1] for seed in ordered}) < 3:
            return None
        start, end = usable_exit_segment(exit_, self.exit_clearance)
        vx, vy = end[0] - start[0], end[1] - start[1]
        length_squared = vx * vx + vy * vy
        query_projection = (
            (point[0] - start[0]) * vx + (point[1] - start[1]) * vy
        ) / length_squared
        current_index = min(
            range(len(ordered)),
            key=lambda index: (
                abs(
                    (
                        (ordered[index][1][0] - start[0]) * vx
                        + (ordered[index][1][1] - start[1]) * vy
                    )
                    / length_squared
                    - query_projection
                ),
                index,
            ),
        )
        return (
            ordered[current_index - 1] if current_index > 0 else None,
            ordered[current_index],
            ordered[current_index + 1] if current_index + 1 < len(ordered) else None,
        )

    def _order_seeds_by_projection(self, exit_: Exit, seeds: Sequence[ExitSeed]) -> list[ExitSeed]:
        """Return one representative per distinct seed position, ordered along the exit."""
        start, end = usable_exit_segment(exit_, self.exit_clearance)
        vx, vy = end[0] - start[0], end[1] - start[1]
        length_squared = vx * vx + vy * vy
        positions: dict[tuple[Point, Point], ExitSeed] = {}
        for seed in seeds:
            target, approach = seed[1], seed[2]
            positions.setdefault((target, approach), seed)
        return sorted(
            positions.values(),
            key=lambda seed: (
                ((seed[1][0] - start[0]) * vx + (seed[1][1] - start[1]) * vy)
                / length_squared,
                seed[0],
            ),
        )

    def _exit_seeds(self, exit_: Exit) -> list[ExitSeed]:
        candidates = np.flatnonzero(self.valid)
        start, end = usable_exit_segment(exit_, self.exit_clearance)
        ax, ay = start
        bx, by = end
        vx, vy = bx - ax, by - ay
        length_squared = vx * vx + vy * vy
        length = math.sqrt(length_squared)
        normal = (-vy / length, vx / length)
        raw_projection = (
            (self._x[candidates] - ax) * vx + (self._y[candidates] - ay) * vy
        ) / length_squared
        projection = np.clip(raw_projection, 0.0, 1.0)
        target_x = ax + projection * vx
        target_y = ay + projection * vy
        squared = (self._x[candidates] - target_x) ** 2 + (self._y[candidates] - target_y) ** 2
        maximum_seed_distance = self.exit_clearance + EXIT_SEED_MAX_DISTANCE_METERS
        nearby = np.flatnonzero(squared <= (maximum_seed_distance + _EPSILON) ** 2)
        seeds = []
        endpoint_clamped: set[int] = set()
        for offset in nearby:
            node = int(candidates[offset])
            target = (float(target_x[offset]), float(target_y[offset]))
            node_point = self._point(node)
            distance = math.dist(node_point, target)
            if distance <= _EPSILON or not self._physical_edge_is_walkable(node_point, target):
                continue
            approaches = (
                (
                    target[0] + normal[0] * self.exit_clearance,
                    target[1] + normal[1] * self.exit_clearance,
                ),
                (
                    target[0] - normal[0] * self.exit_clearance,
                    target[1] - normal[1] * self.exit_clearance,
                ),
            )
            valid_approaches = [
                approach
                for approach in approaches
                if self.contains(approach) and self.can_connect(node_point, approach)
            ]
            if valid_approaches:
                approach = min(valid_approaches, key=lambda item: (math.dist(node_point, item), item))
                seeds.append((node, target, approach))
                if raw_projection[offset] <= 0.0 or raw_projection[offset] >= 1.0:
                    endpoint_clamped.add(node)
        if (
            seeds
            and len(seeds) > len(endpoint_clamped)
            and length >= EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS
        ):
            seeds = [seed for seed in seeds if seed[0] not in endpoint_clamped]
        return seeds

    def contains(self, point: Point) -> bool:
        return self._prepared_walkable.covers(ShapelyPoint(point))

    def can_connect(self, start: Point, end: Point) -> bool:
        return self._prepared_walkable.covers(LineString((start, end)))

    def can_connect_many(self, starts: Sequence[Point], ends: Sequence[Point]) -> np.ndarray:
        if len(starts) != len(ends):
            raise ValueError("connection start and end counts must match")
        if len(starts) == 0:
            return np.empty(0, dtype=bool)
        count = len(starts)
        coordinates = np.stack(
            (
                _point_array(starts, count, "connection starts"),
                _point_array(ends, count, "connection ends"),
            ),
            axis=1,
        )
        return np.asarray(covers(self.walkable, linestrings(coordinates)), dtype=bool)

    def valid_moves(self, starts: Sequence[Point], ends: Sequence[Point]) -> np.ndarray:
        if len(starts) != len(ends):
            raise ValueError("movement start and end counts must match")
        if len(starts) == 0:
            return np.empty(0, dtype=bool)
        count = len(starts)
        start_values = _point_array(starts, count, "movement starts")
        end_values = _point_array(ends, count, "movement ends")
        coordinates = np.stack((start_values, end_values), axis=1)
        end_points = points(end_values[:, 0], end_values[:, 1])
        return np.asarray(
            covers(self.walkable, end_points) & covers(self.walkable, linestrings(coordinates)),
            dtype=bool,
        )

    def can_reach_exit(self, start: Point, end: Point) -> bool:
        return self._physical_edge_is_walkable(start, end)

    def can_reach_exits(self, starts: Sequence[Point], ends: Sequence[Point]) -> np.ndarray:
        if len(starts) != len(ends):
            raise ValueError("exit approach start and end counts must match")
        if len(starts) == 0:
            return np.empty(0, dtype=bool)
        count = len(starts)
        coordinates = np.stack(
            (
                _point_array(starts, count, "exit approach starts"),
                _point_array(ends, count, "exit approach ends"),
            ),
            axis=1,
        )
        return np.asarray(covers(self.physical_walkable, linestrings(coordinates)), dtype=bool)

    def crossed_exit(self, start: Point, end: Point, exit_start: Point, exit_end: Point) -> bool:
        if (
            max(start[0], end[0]) < min(exit_start[0], exit_end[0])
            or min(start[0], end[0]) > max(exit_start[0], exit_end[0])
            or max(start[1], end[1]) < min(exit_start[1], exit_end[1])
            or min(start[1], end[1]) > max(exit_start[1], exit_end[1])
        ):
            return False
        movement = LineString((start, end))
        return self._prepared_physical_walkable.covers(ShapelyPoint(start)) and movement.intersects(
            LineString((exit_start, exit_end))
        )

    def crossed_exits(
        self,
        starts: Sequence[Point],
        ends: Sequence[Point],
        exit_starts: Sequence[Point],
        exit_ends: Sequence[Point],
    ) -> np.ndarray:
        count = len(starts)
        if len(ends) != count or len(exit_starts) != count or len(exit_ends) != count:
            raise ValueError("movement and exit segment counts must match")
        result = np.zeros(count, dtype=bool)
        if count == 0:
            return result

        start_values = _point_array(starts, count, "movement starts")
        end_values = _point_array(ends, count, "movement ends")
        exit_start_values = _point_array(exit_starts, count, "exit starts")
        exit_end_values = _point_array(exit_ends, count, "exit ends")
        candidates = (
            (
                np.maximum(start_values[:, 0], end_values[:, 0])
                >= np.minimum(exit_start_values[:, 0], exit_end_values[:, 0])
            )
            & (
                np.minimum(start_values[:, 0], end_values[:, 0])
                <= np.maximum(exit_start_values[:, 0], exit_end_values[:, 0])
            )
            & (
                np.maximum(start_values[:, 1], end_values[:, 1])
                >= np.minimum(exit_start_values[:, 1], exit_end_values[:, 1])
            )
            & (
                np.minimum(start_values[:, 1], end_values[:, 1])
                <= np.maximum(exit_start_values[:, 1], exit_end_values[:, 1])
            )
        )
        if not candidates.any():
            return result

        movement = linestrings(
            np.stack((start_values[candidates], end_values[candidates]), axis=1)
        )
        exit_segments = linestrings(
            np.stack((exit_start_values[candidates], exit_end_values[candidates]), axis=1)
        )
        result[candidates] = np.asarray(
            covers(
                self.physical_walkable,
                points(start_values[candidates, 0], start_values[candidates, 1]),
            )
            & intersects(movement, exit_segments),
            dtype=bool,
        )
        return result

    def reached_exit(self, position: Point, exit_start: Point, exit_end: Point) -> bool:
        return geometry_distance(
            ShapelyPoint(position), LineString((exit_start, exit_end))
        ) <= self.exit_clearance + _EPSILON

    def reached_exits(
        self,
        positions: Sequence[Point],
        exit_starts: Sequence[Point],
        exit_ends: Sequence[Point],
    ) -> np.ndarray:
        count = len(positions)
        if len(exit_starts) != count or len(exit_ends) != count:
            raise ValueError("position and exit segment counts must match")
        if count == 0:
            return np.empty(0, dtype=bool)
        position_values = _point_array(positions, count, "exit positions")
        exit_start_values = _point_array(exit_starts, count, "exit starts")
        exit_end_values = _point_array(exit_ends, count, "exit ends")
        return np.asarray(
            geometry_distance(
                points(position_values[:, 0], position_values[:, 1]),
                linestrings(np.stack((exit_start_values, exit_end_values), axis=1)),
            )
            <= self.exit_clearance + _EPSILON,
            dtype=bool,
        )

    def clamp_to_walkable(self, point: Point) -> Point:
        target = ShapelyPoint(point)
        if self._prepared_walkable.covers(target):
            return point
        nearest = nearest_points(target, self.walkable)[1]
        return (float(nearest.x), float(nearest.y))

    def _physical_edge_is_walkable(self, start: Point, end: Point) -> bool:
        return self._prepared_physical_walkable.covers(LineString((start, end)))

    def _build_grid_edges(self) -> dict[tuple[int, int], np.ndarray]:
        result = {}
        for dx, dy in ((1, 0), (0, 1), (1, 1), (-1, 1)):
            rows = np.arange(max(0, -dy), min(self.height, self.height - dy))
            columns = np.arange(max(0, -dx), min(self.width, self.width - dx))
            grid_columns, grid_rows = np.meshgrid(columns, rows)
            source = (grid_rows * self.width + grid_columns).ravel()
            destination = source + dy * self.width + dx
            candidate = self.valid[source] & self.valid[destination]
            if dx and dy:
                candidate &= self.valid[source + dx] & self.valid[source + dy * self.width]
            source = source[candidate]
            clear = np.zeros(self.width * self.height, dtype=bool)
            if source.size:
                destination = source + dy * self.width + dx
                coordinates = np.empty((source.size, 2, 2), dtype=float)
                coordinates[:, 0, 0] = self._x[source]
                coordinates[:, 0, 1] = self._y[source]
                coordinates[:, 1, 0] = self._x[destination]
                coordinates[:, 1, 1] = self._y[destination]
                clear[source] = np.asarray(covers(self.walkable, linestrings(coordinates)), dtype=bool)
            result[(dx, dy)] = clear
        return result

    def _build_edge_costs(self) -> list[list[float]]:
        """Cost of every grid move, for the whole grid, once.

        Hazards and grid coordinates are fixed for the lifetime of a router, so
        each move costs the same however many times the propagation relaxes it.
        Every in-grid move is priced, walkable or not, because `derive` opens
        edges that were closed when this ran - and it shares this table, which
        stays right precisely because nothing it depends on can change.
        """
        costs = np.empty((self.valid.size, len(_MOVES)), dtype=float)
        for index, move in enumerate(_MOVES):
            costs[:, index] = self._move_costs[move]
        if not self.hazards:
            return costs.tolist()

        nodes = np.arange(self.valid.size)
        rows, columns = np.divmod(nodes, self.width)
        for index, (dx, dy) in enumerate(_MOVES):
            eligible = np.flatnonzero(
                (columns + dx >= 0)
                & (columns + dx < self.width)
                & (rows + dy >= 0)
                & (rows + dy < self.height)
            )
            targets = eligible + dy * self.width + dx
            # The scalar propagation charged `edge_cost(neighbour, node)`, so the
            # neighbour stays the start point and the Simpson terms keep their
            # grouping. `test_route_planner` holds both forms to the same bits.
            costs[eligible, index] = edge_costs(
                self._x[targets],
                self._y[targets],
                self._x[eligible],
                self._y[eligible],
                self.hazards,
            )
        return costs.tolist()

    def _build_neighbor_nodes(self) -> np.ndarray:
        result = np.full((self.valid.size, len(_MOVES)), -1, dtype=np.int32)
        move_indexes = {move: index for index, move in enumerate(_MOVES)}
        for (dx, dy), clear in self._grid_edges.items():
            sources = np.flatnonzero(clear)
            destinations = sources + dy * self.width + dx
            result[sources, move_indexes[(dx, dy)]] = destinations
            result[destinations, move_indexes[(-dx, -dy)]] = sources
        return result

    def _grid_edge_is_walkable(
        self, start: int, end: int, dx: int, dy: int
    ) -> bool:
        direction = (dx, dy)
        if direction in self._grid_edges:
            return bool(self._grid_edges[direction][start])
        reverse = (-direction[0], -direction[1])
        return bool(self._grid_edges[reverse][end])

    def _local_nodes(self, point: Point, reachable: np.ndarray) -> list[int]:
        center_column = round((point[0] - self.origin_x) / self.step)
        center_row = round((point[1] - self.origin_y) / self.step)
        maximum_squared = (self.step * math.sqrt(2.0) + _EPSILON) ** 2
        nodes = []
        for row in range(max(0, center_row - 2), min(self.height, center_row + 3)):
            for column in range(max(0, center_column - 2), min(self.width, center_column + 3)):
                node = row * self.width + column
                if reachable[node] and (
                    (self._x[node] - point[0]) ** 2 + (self._y[node] - point[1]) ** 2
                    <= maximum_squared
                ):
                    nodes.append(node)
        return nodes

    def _best_connector(
        self, point: Point, nodes: Iterable[int]
    ) -> tuple[float, int, int] | None:
        # Ranking before the geometry check keeps the same winner while normally
        # costing one can_connect call instead of one per nearby node.
        return next(
            (
                candidate
                for candidate in sorted(
                    (
                        self._edge_cost(point, self._point(node)) + float(self.distance[node]),
                        int(self.exit_label[node]),
                        int(node),
                    )
                    for node in nodes
                )
                if self.can_connect(point, self._point(candidate[2]))
            ),
            None,
        )

    def _expanded_connector(
        self, point: Point, candidates: np.ndarray, squared: np.ndarray
    ) -> tuple[float, int, int] | None:
        np.sqrt(squared, out=squared)
        squared += self.distance[candidates]
        # candidates are already in node order, so a stable sort also fixes tie order.
        order = np.argsort(squared, kind="stable")
        best: tuple[float, int, int] | None = None
        for offset in range(0, order.size, _CONNECTOR_VISIBILITY_BATCH_SIZE):
            if best is not None and squared[order[offset]] > best[0] + _EPSILON:
                break
            batch_order = order[
                offset : offset + _CONNECTOR_VISIBILITY_BATCH_SIZE
            ]
            batch = candidates[batch_order]
            batch_lower_bounds = squared[batch_order]
            coordinates = np.empty((batch.size, 2, 2), dtype=float)
            coordinates[:, 0, :] = point
            coordinates[:, 1, 0] = self._x[batch]
            coordinates[:, 1, 1] = self._y[batch]
            clear = np.asarray(
                covers(self.walkable, linestrings(coordinates)), dtype=bool
            )
            for position in np.flatnonzero(clear):
                if best is not None and batch_lower_bounds[position] > best[0] + _EPSILON:
                    break
                node = int(batch[position])
                total = self._expanded_connector_cost(
                    point, self._point(node)
                ) + float(self.distance[node])
                candidate = (total, int(self.exit_label[node]), node)
                if best is None or candidate < best:
                    best = candidate
        return best

    def _expanded_connector_cost(self, start: Point, end: Point) -> float:
        length = math.dist(start, end)
        if not self.hazards:
            return length
        if length <= self.step:
            return self._edge_cost(start, end)
        segment_count = math.ceil(length / self.step)
        total = 0.0
        previous = start
        for index in range(1, segment_count + 1):
            ratio = index / segment_count
            current = (
                start[0] + (end[0] - start[0]) * ratio,
                start[1] + (end[1] - start[1]) * ratio,
            )
            total += self._edge_cost(previous, current)
            previous = current
        return total

    def _point(self, flat_index: int) -> Point:
        return (float(self._x[flat_index]), float(self._y[flat_index]))


def _point(item: Any, label: str) -> Point:
    try:
        if isinstance(item, dict):
            result = (float(item["x"]), float(item["y"]))
        else:
            result = (float(item[0]), float(item[1]))
    except (KeyError, IndexError, TypeError, ValueError) as exc:
        raise ValueError(f"{label} must contain numeric x and y") from exc
    if not all(math.isfinite(value) for value in result):
        raise ValueError(f"{label} coordinates must be finite")
    return result


def _line_points(item: dict[str, Any], label: str) -> tuple[Point, Point]:
    try:
        start = (float(item["startX"]), float(item["startY"]))
        end = (float(item["endX"]), float(item["endY"]))
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"{label} must contain numeric start/end coordinates") from exc
    if not all(math.isfinite(value) for value in (*start, *end)):
        raise ValueError(f"{label} coordinates must be finite")
    return start, end


def _rectangle(item: dict[str, Any], label: str):
    start, end = _line_points(item, label)
    min_x, max_x = sorted((start[0], end[0]))
    min_y, max_y = sorted((start[1], end[1]))
    if max_x - min_x <= 0 or max_y - min_y <= 0:
        raise ValueError(f"{label} rectangles must have positive width and height")
    try:
        angle = float(item.get("rotation", 0.0))
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label}.rotation must be numeric") from exc
    if not math.isfinite(angle):
        raise ValueError(f"{label}.rotation must be finite")
    center = ((min_x + max_x) / 2.0, (min_y + max_y) / 2.0)
    return rotate(box(min_x, min_y, max_x, max_y), angle, origin=center, use_radians=False)


def _id_key(value: Any) -> str:
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise ValueError("exit ids must be non-null numbers or strings")
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("exit ids must be finite")
        if value.is_integer():
            value = int(value)
    return str(value)


def usable_exit_segment(exit_: Exit, clearance: float) -> tuple[Point, Point]:
    if not math.isfinite(clearance) or clearance <= 0:
        raise ValueError("exit clearance must be positive")
    length = math.dist(exit_.start, exit_.end)
    if length <= 2.0 * clearance + _EPSILON:
        raise ValueError(
            f"exit {exit_.id!r} must be wider than {2.0 * clearance:g}m for agent clearance"
        )
    ratio = clearance / length
    start = (
        exit_.start[0] + (exit_.end[0] - exit_.start[0]) * ratio,
        exit_.start[1] + (exit_.end[1] - exit_.start[1]) * ratio,
    )
    end = (
        exit_.end[0] + (exit_.start[0] - exit_.end[0]) * ratio,
        exit_.end[1] + (exit_.start[1] - exit_.end[1]) * ratio,
    )
    return start, end


def _simplify_collinear(
    path: Sequence[Point], can_connect: Callable[[Point, Point], bool]
) -> list[Point]:
    if len(path) < 3:
        return list(path)
    result = [path[0]]
    for index in range(1, len(path) - 1):
        previous = result[-1]
        current = path[index]
        following = path[index + 1]
        first = (current[0] - previous[0], current[1] - previous[1])
        second = (following[0] - current[0], following[1] - current[1])
        cross = first[0] * second[1] - first[1] * second[0]
        scale = max(math.hypot(*first) * math.hypot(*second), _EPSILON)
        same_direction = (
            abs(cross) <= _EPSILON * scale
            and first[0] * second[0] + first[1] * second[1] > 0
        )
        if not same_direction or not can_connect(previous, following):
            result.append(current)
    result.append(path[-1])
    return result
