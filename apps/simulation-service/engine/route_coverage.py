from __future__ import annotations

import math
from typing import Sequence, TypedDict

try:
    from route_planner import Exit, GridRouter, _id_key
except ModuleNotFoundError:
    from .route_planner import Exit, GridRouter, _id_key


class RouteCoverage(TypedDict):
    originX: float
    originY: float
    step: float
    columns: int
    rows: int
    labels: list[int]
    exitIds: list[int]


class RoutePreviewZone(TypedDict):
    zoneId: int
    x: float
    y: float
    width: float
    height: float
    defaultExitId: int | None


def serialize_route_coverage(
    routers: Sequence[GridRouter], exits: Sequence[Exit], step: float = 1.0
) -> RouteCoverage:
    origin_x = math.floor(min(router.origin_x for router in routers) / step) * step
    origin_y = math.floor(min(router.origin_y for router in routers) / step) * step
    max_x = max(router.origin_x + (router.width - 1) * router.step for router in routers)
    max_y = max(router.origin_y + (router.height - 1) * router.step for router in routers)
    columns = int(math.ceil((max_x - origin_x) / step)) + 1
    rows = int(math.ceil((max_y - origin_y) / step)) + 1
    exit_ids = [int(exit_.id) for exit_ in exits]
    exit_indexes = {_id_key(exit_id): index for index, exit_id in enumerate(exit_ids)}
    labels = []
    for row in range(rows):
        y = origin_y + row * step
        for column in range(columns):
            x = origin_x + column * step
            label = -1
            for router in routers:
                source_column = round((x - router.origin_x) / router.step)
                source_row = round((y - router.origin_y) / router.step)
                if not (0 <= source_column < router.width and 0 <= source_row < router.height):
                    continue
                source_label = int(router.exit_label[source_row * router.width + source_column])
                if source_label >= 0:
                    label = exit_indexes[_id_key(router.exits[source_label].id)]
                    break
            labels.append(label)
    return {
        "originX": round(float(origin_x), 6),
        "originY": round(float(origin_y), 6),
        "step": step,
        "columns": columns,
        "rows": rows,
        "labels": labels,
        "exitIds": exit_ids,
    }


def zone_branch_origins(
    coverage: RouteCoverage, zone: RoutePreviewZone
) -> list[tuple[int, tuple[float, float]]]:
    min_x = zone["x"]
    min_y = zone["y"]
    max_x = min_x + zone["width"]
    max_y = min_y + zone["height"]
    points_by_label: dict[int, list[tuple[float, float]]] = {}
    for row in range(coverage["rows"]):
        y = coverage["originY"] + row * coverage["step"]
        if y < min_y or y > max_y:
            continue
        for column in range(coverage["columns"]):
            x = coverage["originX"] + column * coverage["step"]
            if x < min_x or x > max_x:
                continue
            label = coverage["labels"][row * coverage["columns"] + column]
            if label >= 0:
                points_by_label.setdefault(label, []).append((x, y))

    origins = []
    zone_center = ((min_x + max_x) / 2.0, (min_y + max_y) / 2.0)

    def distance_to_zone_center(point: tuple[float, float]) -> float:
        return (point[0] - zone_center[0]) ** 2 + (point[1] - zone_center[1]) ** 2

    primary_label = None
    primary_point = None
    for label, points in points_by_label.items():
        candidate = min(points, key=distance_to_zone_center)
        if primary_point is None or distance_to_zone_center(candidate) < distance_to_zone_center(primary_point):
            primary_label = label
            primary_point = candidate
    if primary_label is not None:
        origins.append((coverage["exitIds"][primary_label], primary_point))

    for label, points in points_by_label.items():
        if label == primary_label:
            continue
        center_x = sum(point[0] for point in points) / len(points)
        center_y = sum(point[1] for point in points) / len(points)
        representative = min(
            points,
            key=lambda point: (point[0] - center_x) ** 2
            + (point[1] - center_y) ** 2,
        )
        origins.append((coverage["exitIds"][label], representative))
    return origins
