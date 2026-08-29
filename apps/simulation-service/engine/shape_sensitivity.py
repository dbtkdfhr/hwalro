from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from shapely.affinity import rotate, translate
from shapely.geometry import LineString
from shapely.geometry.base import BaseGeometry

from ideal_flow import FabricObstacle, FlowEdge, InvalidClearanceError


@dataclass(frozen=True, slots=True)
class ShapeSignal:
    fabric_id: int
    force_x: float
    force_y: float
    torque: float
    obstruction: float


def rotate_about_center(geometry: BaseGeometry, angle_degrees: float) -> BaseGeometry:
    return rotate(geometry, angle_degrees, origin="centroid", use_radians=False)


def _projected_width(geometry: BaseGeometry, normal_x: float, normal_y: float) -> float:
    coordinates = geometry.convex_hull.exterior.coords
    projections = tuple(x * normal_x + y * normal_y for x, y in coordinates)
    return max(projections) - min(projections)


def _edge_obstruction(
    geometry: BaseGeometry, edge: FlowEdge, clearance: float
) -> float:
    dx = edge.end[0] - edge.start[0]
    dy = edge.end[1] - edge.start[1]
    length = math.hypot(dx, dy)
    if length <= 1e-9:
        return 0.0
    normal_x, normal_y = -dy / length, dx / length
    center = geometry.centroid
    distance = LineString((edge.start, edge.end)).distance(center)
    if distance >= 6.0 * clearance:
        return 0.0
    proximity = math.exp(-0.5 * (distance / clearance) ** 2)
    return edge.delay * _projected_width(geometry, normal_x, normal_y) * proximity


def _obstruction(
    geometry: BaseGeometry, edges: Sequence[FlowEdge], clearance: float
) -> float:
    return sum(_edge_obstruction(geometry, edge, clearance) for edge in edges)


def _signal(
    obstacle: FabricObstacle, edges: Sequence[FlowEdge], clearance: float
) -> ShapeSignal:
    translation_step = max(0.01, clearance * 0.1)
    angle_step = 1.0
    x_plus = _obstruction(
        translate(obstacle.geometry, xoff=translation_step), edges, clearance
    )
    x_minus = _obstruction(
        translate(obstacle.geometry, xoff=-translation_step), edges, clearance
    )
    y_plus = _obstruction(
        translate(obstacle.geometry, yoff=translation_step), edges, clearance
    )
    y_minus = _obstruction(
        translate(obstacle.geometry, yoff=-translation_step), edges, clearance
    )
    angle_plus = _obstruction(
        rotate_about_center(obstacle.geometry, angle_step), edges, clearance
    )
    angle_minus = _obstruction(
        rotate_about_center(obstacle.geometry, -angle_step), edges, clearance
    )
    return ShapeSignal(
        obstacle.fabric_id,
        -(x_plus - x_minus) / (2.0 * translation_step),
        -(y_plus - y_minus) / (2.0 * translation_step),
        -(angle_plus - angle_minus) / math.radians(2.0 * angle_step),
        _obstruction(obstacle.geometry, edges, clearance),
    )


def shape_signals(
    obstacles: Sequence[FabricObstacle],
    edges: Sequence[FlowEdge],
    clearance: float,
) -> tuple[ShapeSignal, ...]:
    if not math.isfinite(clearance) or clearance <= 0.0:
        raise InvalidClearanceError(clearance)
    return tuple(
        _signal(obstacle, edges, clearance)
        for obstacle in sorted(obstacles, key=lambda item: item.fabric_id)
    )
