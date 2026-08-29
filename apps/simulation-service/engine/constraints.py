from __future__ import annotations

from typing import Any, Sequence

from shapely.geometry import LineString, box

FREE = "FREE"
WITHIN_ZONE = "WITHIN_ZONE"
FIXED = "FIXED"

WALL_TOUCH_TOLERANCE_METERS = 0.05


class SearchConstraints:
    __slots__ = ("movement_policies", "movement_zones", "forbidden_zones")

    def __init__(
        self,
        movement_policies: dict[Any, str] | None = None,
        movement_zones: dict[Any, dict[str, float]] | None = None,
        forbidden_zones: Sequence[dict[str, float]] = (),
    ) -> None:
        self.movement_policies = movement_policies or {}
        self.movement_zones = {
            fabric_id: box(
                float(zone["x"]),
                float(zone["y"]),
                float(zone["x"]) + float(zone["width"]),
                float(zone["y"]) + float(zone["height"]),
            )
            for fabric_id, zone in (movement_zones or {}).items()
            if float(zone.get("width", 0.0)) > 0 and float(zone.get("height", 0.0)) > 0
        }
        self.forbidden_zones = [
            box(float(z["x"]), float(z["y"]), float(z["x"]) + float(z["width"]), float(z["y"]) + float(z["height"]))
            for z in forbidden_zones
            if float(z.get("width", 0.0)) > 0 and float(z.get("height", 0.0)) > 0
        ]

    def movement_policy_of(self, fabric_id: Any) -> str:
        return self.movement_policies.get(fabric_id, FREE)

    def can_move(self, fabric_id: Any) -> bool:
        policy = self.movement_policy_of(fabric_id)
        return policy == FREE or (policy == WITHIN_ZONE and fabric_id in self.movement_zones)

    def movement_area_of(self, fabric_id: Any) -> Any | None:
        if self.movement_policy_of(fabric_id) == FREE:
            return None
        return self.movement_zones.get(fabric_id)

    def allows_placement(self, fabric_id: Any, geometry: Any) -> bool:
        policy = self.movement_policy_of(fabric_id)
        if policy == FIXED:
            return False
        if policy == FREE:
            return True
        zone = self.movement_zones.get(fabric_id)
        return zone is not None and zone.covers(geometry)

    def move_radius_of(self, fabric_id: Any) -> float:
        return float("inf") if self.can_move(fabric_id) else 0.0

    def rotation_allowed_of(self, fabric_id: Any) -> bool:
        return self.can_move(fabric_id)

    def is_wall_anchored(self, fabric_id: Any) -> bool:
        return False

    def intersects_forbidden_zone(self, geometry: Any) -> bool:
        return any(geometry.intersects(zone) for zone in self.forbidden_zones)

    def to_dict(self) -> dict[str, Any]:
        return {
            "movementPolicies": {str(k): v for k, v in self.movement_policies.items()},
            "movementZones": {
                str(fabric_id): {
                    "x": zone.bounds[0],
                    "y": zone.bounds[1],
                    "width": zone.bounds[2] - zone.bounds[0],
                    "height": zone.bounds[3] - zone.bounds[1],
                }
                for fabric_id, zone in self.movement_zones.items()
            },
            "forbiddenZones": [
                {
                    "x": zone.bounds[0],
                    "y": zone.bounds[1],
                    "width": zone.bounds[2] - zone.bounds[0],
                    "height": zone.bounds[3] - zone.bounds[1],
                }
                for zone in self.forbidden_zones
            ],
        }


def _key_to_int(value: Any) -> int:
    if isinstance(value, int):
        return value
    return int(float(value))


def parse_constraints(raw: dict[str, Any] | None) -> SearchConstraints:
    if not raw:
        return SearchConstraints()
    movement_policies = {
        _key_to_int(k): str(v).strip().upper()
        for k, v in (raw.get("movementPolicies") or {}).items()
        if str(v).strip().upper() in {FREE, WITHIN_ZONE, FIXED}
    }
    movement_zones = {_key_to_int(k): v for k, v in (raw.get("movementZones") or {}).items()}
    forbidden = list(raw.get("forbiddenZones") or [])
    return SearchConstraints(movement_policies, movement_zones, forbidden)


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
