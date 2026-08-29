from __future__ import annotations

import math
from dataclasses import dataclass
from typing import NotRequired, Sequence, TypedDict

from shapely.affinity import rotate
from shapely.geometry import LineString, Point, Polygon, box
from shapely.geometry.base import BaseGeometry

from constraints import SearchConstraints
from route_planner import GRID_STEP_METERS
from search_precision import decimal_value, preserved_span_bounds, quantized


class Coordinate(TypedDict):
    x: float
    y: float


class Rectangle(TypedDict):
    id: int
    startX: float
    startY: float
    endX: float
    endY: float
    rotation: NotRequired[float]


class Segment(TypedDict):
    startX: float
    startY: float
    endX: float
    endY: float


class ExitSegment(Segment):
    id: int


class Drawing(TypedDict):
    outsideBoundary: list[Coordinate]
    walls: list[Segment]
    pillars: list[Rectangle]
    fabrics: list[Rectangle]
    exits: list[ExitSegment]


class RectState(TypedDict):
    startX: float
    startY: float
    endX: float
    endY: float
    rotation: float


@dataclass(frozen=True, slots=True)
class Placement:
    state: RectState
    geometry: BaseGeometry
    travel: float


def state(fabric: Rectangle) -> RectState:
    return {
        "startX": float(fabric["startX"]),
        "startY": float(fabric["startY"]),
        "endX": float(fabric["endX"]),
        "endY": float(fabric["endY"]),
        "rotation": float(fabric.get("rotation", 0.0)),
    }


def geometry(rectangle: RectState) -> BaseGeometry:
    bounds = box(rectangle["startX"], rectangle["startY"], rectangle["endX"], rectangle["endY"])
    center = ((rectangle["startX"] + rectangle["endX"]) / 2.0, (rectangle["startY"] + rectangle["endY"]) / 2.0)
    return rotate(bounds, rectangle["rotation"], origin=center, use_radians=False)


def _boundary_segments(
    drawing: Drawing, constraints: SearchConstraints, fabric_id: int
) -> list[tuple[tuple[float, float], tuple[float, float]]]:
    movement_area = constraints.movement_area_of(fabric_id)
    if movement_area is not None:
        coordinates = list(movement_area.exterior.coords)[:-1]
        return list(zip(coordinates, [*coordinates[1:], coordinates[0]], strict=True))
    points = [(float(item["x"]), float(item["y"])) for item in drawing["outsideBoundary"]]
    boundary = list(zip(points, [*points[1:], points[0]], strict=True))
    walls = [
        ((float(item["startX"]), float(item["startY"])), (float(item["endX"]), float(item["endY"])))
        for item in drawing.get("walls", [])
    ]
    return [*boundary, *walls]


def _inward_normal(outside: Polygon, start: tuple[float, float], end: tuple[float, float]) -> tuple[float, float]:
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = math.hypot(dx, dy)
    left = (-dy / length, dx / length)
    middle = ((start[0] + end[0]) / 2.0, (start[1] + end[1]) / 2.0)
    return left if outside.covers(Point(middle[0] + left[0] * 0.05, middle[1] + left[1] * 0.05)) else (-left[0], -left[1])


def placements(
    drawing: Drawing,
    fabric: Rectangle,
    static_obstacles: Sequence[BaseGeometry],
    constraints: SearchConstraints,
) -> list[Placement]:
    outside = Polygon([(item["x"], item["y"]) for item in drawing["outsideBoundary"]])
    before = state(fabric)
    width = float(quantized(decimal_value(abs(before["endX"] - before["startX"]))))
    height = float(quantized(decimal_value(abs(before["endY"] - before["startY"]))))
    long_side, short_side = max(width, height), min(width, height)
    source_center = ((before["startX"] + before["endX"]) / 2.0, (before["startY"] + before["endY"]) / 2.0)
    exit_guard = [LineString(((item["startX"], item["startY"]), (item["endX"], item["endY"]))).buffer(GRID_STEP_METERS) for item in drawing.get("exits", [])]
    walls = [
        LineString(((item["startX"], item["startY"]), (item["endX"], item["endY"])))
        for item in drawing.get("walls", [])
    ]
    movement_area = constraints.movement_area_of(fabric["id"])
    docking_area = movement_area if movement_area is not None else outside
    result: list[Placement] = []
    for start, end in _boundary_segments(drawing, constraints, fabric["id"]):
        dx, dy = end[0] - start[0], end[1] - start[1]
        length = math.hypot(dx, dy)
        if length + 1e-9 < long_side:
            continue
        tangent = (dx / length, dy / length)
        normal = _inward_normal(docking_area, start, end)
        rotation = math.degrees(math.atan2(dy, dx)) - (90.0 if height > width else 0.0)
        for along in (long_side / 2.0, length / 2.0, length - long_side / 2.0):
            center = (start[0] + tangent[0] * along + normal[0] * (short_side / 2.0 + 1e-4), start[1] + tangent[1] * along + normal[1] * (short_side / 2.0 + 1e-4))
            start_x, end_x = preserved_span_bounds(center[0], width)
            start_y, end_y = preserved_span_bounds(center[1], height)
            after: RectState = {
                "startX": start_x,
                "startY": start_y,
                "endX": end_x,
                "endY": end_y,
                "rotation": float(quantized(decimal_value(rotation % 360.0))),
            }
            placed = geometry(after)
            travel = math.dist(source_center, center)
            if travel > constraints.move_radius_of(fabric["id"]) + 1e-9:
                continue
            if not outside.covers(placed) or any(placed.intersects(item) for item in static_obstacles):
                continue
            if any(placed.crosses(wall) or placed.contains(wall) for wall in walls):
                continue
            if not constraints.allows_placement(fabric["id"], placed):
                continue
            if any(placed.intersects(item) for item in exit_guard) or constraints.intersects_forbidden_zone(placed):
                continue
            result.append(Placement(after, placed, travel))
    return sorted(result, key=lambda item: (item.travel, item.state["rotation"], item.state["startX"], item.state["startY"]))


def push_placements(
    drawing: Drawing,
    fabric: Rectangle,
    static_obstacles: Sequence[BaseGeometry],
    constraints: SearchConstraints,
    route_angles: Sequence[float],
) -> list[Placement]:
    outside = Polygon([(item["x"], item["y"]) for item in drawing["outsideBoundary"]])
    before = state(fabric)
    source_center = (
        (before["startX"] + before["endX"]) / 2.0,
        (before["startY"] + before["endY"]) / 2.0,
    )
    width = abs(before["endX"] - before["startX"])
    height = abs(before["endY"] - before["startY"])
    exit_guard = [
        LineString(((item["startX"], item["startY"]), (item["endX"], item["endY"]))).buffer(
            GRID_STEP_METERS
        )
        for item in drawing.get("exits", [])
    ]
    walls = [
        LineString(((item["startX"], item["startY"]), (item["endX"], item["endY"])))
        for item in drawing.get("walls", [])
    ]
    movement_area = constraints.movement_area_of(fabric["id"])
    travel_area = movement_area if movement_area is not None else outside
    min_x, min_y, max_x, max_y = travel_area.bounds
    maximum_travel = math.hypot(max_x - min_x, max_y - min_y)

    def valid(candidate: RectState) -> bool:
        placed = geometry(candidate)
        return (
            outside.covers(placed)
            and not any(placed.intersects(item) for item in static_obstacles)
            and not any(placed.crosses(wall) or placed.contains(wall) for wall in walls)
            and constraints.allows_placement(fabric["id"], placed)
            and not any(placed.intersects(item) for item in exit_guard)
            and not constraints.intersects_forbidden_zone(placed)
        )

    def shifted(base: RectState, direction: tuple[float, float], distance: float) -> RectState:
        dx, dy = direction[0] * distance, direction[1] * distance
        return {
            "startX": float(quantized(decimal_value(base["startX"] + dx))),
            "startY": float(quantized(decimal_value(base["startY"] + dy))),
            "endX": float(quantized(decimal_value(base["endX"] + dx))),
            "endY": float(quantized(decimal_value(base["endY"] + dy))),
            "rotation": base["rotation"],
        }

    def furthest(base: RectState, direction: tuple[float, float]) -> RectState:
        step = GRID_STEP_METERS / 2.0
        distance = 0.0
        last = base
        while distance + step <= maximum_travel + 1e-9:
            distance += step
            candidate = shifted(base, direction, distance)
            if not valid(candidate):
                low, high = distance - step, distance
                for _ in range(12):
                    middle = (low + high) / 2.0
                    refined = shifted(base, direction, middle)
                    if valid(refined):
                        low, last = middle, refined
                    else:
                        high = middle
                return last
            last = candidate
        return last

    result: list[Placement] = []
    seen: set[tuple[float, float, float, float, float]] = set()
    for angle in route_angles:
        radians = math.radians(angle)
        directions = ((-math.sin(radians), math.cos(radians)), (math.sin(radians), -math.cos(radians)))
        for direction in directions:
            translated = furthest(before, direction)
            states = [translated]
            if constraints.rotation_allowed_of(fabric["id"]):
                aligned_rotation = angle - (90.0 if height > width else 0.0)
                rotated_state: RectState = {
                    **translated,
                    "rotation": float(quantized(decimal_value(aligned_rotation % 360.0))),
                }
                if valid(rotated_state):
                    states.append(furthest(rotated_state, direction))
            for candidate in states:
                key = (
                    candidate["startX"], candidate["startY"], candidate["endX"],
                    candidate["endY"], candidate["rotation"],
                )
                if key in seen or candidate == before:
                    continue
                seen.add(key)
                placed = geometry(candidate)
                center = placed.centroid
                result.append(Placement(candidate, placed, math.dist(source_center, (center.x, center.y))))
    return result
