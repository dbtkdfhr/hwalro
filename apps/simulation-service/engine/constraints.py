"""User-supplied constraints for layout search candidates.

Constraint model (all optional, all defaults to permissive):

* moveRadii: {fabricId: meters} — 0.0 fixes the fabric, infinity allows free
  movement. Default: infinity (free).
* forbiddenZones: [{x, y, width, height}] — rectangles in drawing coordinates
  that no fabric may intersect after a move.
* rotationAllowed: {fabricId: bool} — default True.
* wallAnchored: {fabricId: bool} — fabric must keep touching at least one wall
  segment after a move. Only meaningful for fabrics already touching a wall.

The module is pure: it parses and judges constraints, it never generates
candidates. Candidate generation that respects these constraints lives in
layout_search.py, which imports this module.
"""

from __future__ import annotations

import math
from typing import Any, Sequence

from shapely.geometry import LineString, box

DEFAULT_MOVE_RADIUS = math.inf

WALL_TOUCH_TOLERANCE_METERS = 0.05


class SearchConstraints:
    __slots__ = ("move_radii", "forbidden_zones", "rotation_allowed", "wall_anchored")

    def __init__(
        self,
        move_radii: dict[Any, float] | None = None,
        forbidden_zones: Sequence[dict[str, float]] = (),
        rotation_allowed: dict[Any, bool] | None = None,
        wall_anchored: dict[Any, bool] | None = None,
    ) -> None:
        self.move_radii = move_radii or {}
        self.forbidden_zones = [
            box(float(z["x"]), float(z["y"]), float(z["x"]) + float(z["width"]), float(z["y"]) + float(z["height"]))
            for z in forbidden_zones
            if float(z.get("width", 0.0)) > 0 and float(z.get("height", 0.0)) > 0
        ]
        self.rotation_allowed = rotation_allowed or {}
        self.wall_anchored = wall_anchored or {}

    def move_radius_of(self, fabric_id: Any) -> float:
        return self.move_radii.get(fabric_id, DEFAULT_MOVE_RADIUS)

    def rotation_allowed_of(self, fabric_id: Any) -> bool:
        return self.rotation_allowed.get(fabric_id, True)

    def is_wall_anchored(self, fabric_id: Any) -> bool:
        return self.wall_anchored.get(fabric_id, False)

    def intersects_forbidden_zone(self, geometry: Any) -> bool:
        return any(geometry.intersects(zone) for zone in self.forbidden_zones)

    def to_dict(self) -> dict[str, Any]:
        return {
            "moveRadii": {str(k): v for k, v in self.move_radii.items()},
            "forbiddenZones": [
                {
                    "x": zone.bounds[0],
                    "y": zone.bounds[1],
                    "width": zone.bounds[2] - zone.bounds[0],
                    "height": zone.bounds[3] - zone.bounds[1],
                }
                for zone in self.forbidden_zones
            ],
            "rotationAllowed": {str(k): v for k, v in self.rotation_allowed.items()},
            "wallAnchored": {str(k): v for k, v in self.wall_anchored.items()},
        }


def _key_to_int(value: Any) -> int:
    if isinstance(value, int):
        return value
    return int(float(value))


def parse_constraints(raw: dict[str, Any] | None) -> SearchConstraints:
    if not raw:
        return SearchConstraints()
    move_radii = {_key_to_int(k): float(v) for k, v in (raw.get("moveRadii") or {}).items()}
    forbidden = list(raw.get("forbiddenZones") or [])
    rotation_allowed = {_key_to_int(k): bool(v) for k, v in (raw.get("rotationAllowed") or {}).items()}
    wall_anchored = {_key_to_int(k): bool(v) for k, v in (raw.get("wallAnchored") or {}).items()}
    return SearchConstraints(move_radii, forbidden, rotation_allowed, wall_anchored)


def touches_wall(fabric: dict[str, Any], walls: Sequence[dict[str, Any]]) -> bool:
    """True when the fabric rectangle touches any wall segment within tolerance."""
    geometry = _fabric_geometry(fabric)
    for wall in walls:
        segment = LineString(
            [(float(wall["startX"]), float(wall["startY"])), (float(wall["endX"]), float(wall["endY"]))]
        )
        if geometry.distance(segment) <= WALL_TOUCH_TOLERANCE_METERS:
            return True
    return False


def _fabric_geometry(fabric: dict[str, Any]) -> Any:
    min_x, max_x = sorted((float(fabric["startX"]), float(fabric["endX"])))
    min_y, max_y = sorted((float(fabric["startY"]), float(fabric["endY"])))
    rotation = float(fabric.get("rotation", 0.0))
    if rotation == 0.0:
        return box(min_x, min_y, max_x, max_y)
    from shapely.affinity import rotate

    center = ((min_x + max_x) / 2.0, (min_y + max_y) / 2.0)
    return rotate(box(min_x, min_y, max_x, max_y), rotation, origin=center, use_radians=False)
