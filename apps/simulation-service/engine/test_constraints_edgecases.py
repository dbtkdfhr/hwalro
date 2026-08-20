"""Edge-case verification for constraints.py (SCRUM-98 constraint model).

Covers >= 25 distinct constraint scenarios:
- parse_constraints normalization (empty/null/partial/unknown keys/string keys)
- moveRadii: free, fixed, half-meter, one-meter, negative, zero-width fabrics
- forbiddenZones: overlap, containment, adjacency, zero-size, negative, outside-drawing, multiple zones
- rotationAllowed: default true, explicit false, mixed, unknown fabric id
- wallAnchored: default false, explicit true, fabric touching wall vs not
- to_dict round-trip fidelity
"""

import math

import pytest

from constraints import (
    DEFAULT_MOVE_RADIUS,
    SearchConstraints,
    parse_constraints,
    touches_wall,
)

SIMPLE_WALLS = [
    {"startX": 0.0, "startY": 0.0, "endX": 10.0, "endY": 0.0},
    {"startX": 0.0, "startY": 10.0, "endX": 10.0, "endY": 10.0},
]

FABRIC = {"id": 1, "name": "f", "startX": 4.0, "startY": 5.0, "endX": 6.0, "endY": 6.0, "rotation": 0.0}

WALL_TOUCHING_FABRIC = {
    "id": 2,
    "name": "g",
    "startX": 8.0,
    "startY": 0.0,
    "endX": 9.0,
    "endY": 1.0,
    "rotation": 0.0,
}

NON_TOUCHING_FABRIC = {
    "id": 3,
    "name": "h",
    "startX": 2.0,
    "startY": 4.0,
    "endX": 3.0,
    "endY": 5.0,
    "rotation": 0.0,
}


def zone(x, y, w, h):
    return {"x": x, "y": y, "width": w, "height": h}


class TestParseConstraints:
    def test_01_null_returns_permissive(self):
        c = parse_constraints(None)
        assert c.move_radii == {}
        assert c.forbidden_zones == []
        assert c.rotation_allowed == {}
        assert c.wall_anchored == {}

    def test_02_empty_dict_returns_permissive(self):
        c = parse_constraints({})
        assert c.move_radii == {}
        assert c.forbidden_zones == []

    def test_03_unknown_keys_ignored(self):
        c = parse_constraints({"bogus": 1, "moveRadii": {"1": 0.0}})
        assert c.move_radii == {1: 0.0}

    def test_04_string_keys_coerced_to_int(self):
        c = parse_constraints({"moveRadii": {"1": 0.5}, "rotationAllowed": {"2": False}})
        assert c.move_radii == {1: 0.5}
        assert c.rotation_allowed == {2: False}

    def test_05_float_keys_coerced_to_int(self):
        c = parse_constraints({"moveRadii": {"1.0": 0.5}})
        assert c.move_radii == {1: 0.5}

    def test_06_partial_sections_default(self):
        c = parse_constraints({"moveRadii": {"1": 0.0}})
        assert c.rotation_allowed == {}
        assert c.wall_anchored == {}


class TestMoveRadii:
    def test_07_default_move_radius_is_infinite(self):
        c = SearchConstraints()
        assert c.move_radius_of(1) == DEFAULT_MOVE_RADIUS

    def test_08_unknown_fabric_defaults_to_free(self):
        c = SearchConstraints(move_radii={1: 0.0})
        assert c.move_radius_of(2) == DEFAULT_MOVE_RADIUS

    def test_09_fixed_radius_zero(self):
        c = SearchConstraints(move_radii={1: 0.0})
        assert c.move_radius_of(1) == 0.0

    def test_10_half_meter(self):
        c = SearchConstraints(move_radii={1: 0.5})
        assert c.move_radius_of(1) == 0.5

    def test_11_one_meter(self):
        c = SearchConstraints(move_radii={1: 1.0})
        assert c.move_radius_of(1) == 1.0

    def test_12_negative_radius_stored_as_is(self):
        c = SearchConstraints(move_radii={1: -1.0})
        assert c.move_radius_of(1) == -1.0

    def test_13_very_large_radius(self):
        c = SearchConstraints(move_radii={1: 1e9})
        assert c.move_radius_of(1) == 1e9


class TestForbiddenZones:
    def test_14_zone_intersects(self):
        c = SearchConstraints(forbidden_zones=[zone(5, 5, 2, 2)])
        assert c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_15_zone_not_intersecting(self):
        c = SearchConstraints(forbidden_zones=[zone(0, 0, 1, 1)])
        assert not c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_16_zone_contained_inside_fabric(self):
        c = SearchConstraints(forbidden_zones=[zone(4.5, 5.2, 0.2, 0.2)])
        assert c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_17_touching_edges_intersects(self):
        c = SearchConstraints(forbidden_zones=[zone(6, 5, 1, 1)])
        assert c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_18_zero_size_zone(self):
        c = SearchConstraints(forbidden_zones=[zone(5, 5, 0, 0)])
        assert not c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_19_negative_size_zone_clamped_to_point(self):
        c = SearchConstraints(forbidden_zones=[zone(5, 5, -1, -1)])
        assert not c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_20_zone_outside_drawing(self):
        c = SearchConstraints(forbidden_zones=[zone(100, 100, 2, 2)])
        assert not c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_21_multiple_zones_any_matches(self):
        c = SearchConstraints(forbidden_zones=[zone(0, 0, 1, 1), zone(5.5, 5.2, 1, 1)])
        assert c.intersects_forbidden_zone(box(4, 5, 6, 6))

    def test_22_empty_zone_list(self):
        c = SearchConstraints(forbidden_zones=[])
        assert not c.intersects_forbidden_zone(box(4, 5, 6, 6))


class TestRotationAllowed:
    def test_23_default_rotation_allowed(self):
        c = SearchConstraints()
        assert c.rotation_allowed_of(1) is True

    def test_24_explicit_false(self):
        c = SearchConstraints(rotation_allowed={1: False})
        assert c.rotation_allowed_of(1) is False

    def test_25_explicit_true(self):
        c = SearchConstraints(rotation_allowed={1: True})
        assert c.rotation_allowed_of(1) is True

    def test_26_mixed_fabrics(self):
        c = SearchConstraints(rotation_allowed={1: False, 2: True})
        assert c.rotation_allowed_of(1) is False
        assert c.rotation_allowed_of(2) is True

    def test_27_unknown_fabric_defaults_true(self):
        c = SearchConstraints(rotation_allowed={1: False})
        assert c.rotation_allowed_of(99) is True


class TestWallAnchored:
    def test_28_default_not_wall_anchored(self):
        c = SearchConstraints()
        assert c.is_wall_anchored(1) is False

    def test_29_explicit_wall_anchored(self):
        c = SearchConstraints(wall_anchored={1: True})
        assert c.is_wall_anchored(1) is True

    def test_30_touching_fabric_detected(self):
        assert touches_wall(WALL_TOUCHING_FABRIC, SIMPLE_WALLS) is True

    def test_31_non_touching_fabric_detected(self):
        assert touches_wall(NON_TOUCHING_FABRIC, SIMPLE_WALLS) is False

    def test_32_zero_width_wall(self):
        walls = [{"startX": 5, "startY": 0, "endX": 5, "endY": 0}]
        assert touches_wall(WALL_TOUCHING_FABRIC, walls) is False


class TestRoundTrip:
    def test_33_to_dict_round_trip_fidelity(self):
        original = SearchConstraints(
            move_radii={1: 0.0, 2: 0.5, 3: 1.0},
            forbidden_zones=[zone(1, 1, 2, 2), zone(3, 3, 4, 4)],
            rotation_allowed={1: False, 4: True},
            wall_anchored={5: True},
        )
        parsed = parse_constraints(original.to_dict())
        assert parsed.move_radii == {1: 0.0, 2: 0.5, 3: 1.0}
        assert parsed.rotation_allowed == {1: False, 4: True}
        assert parsed.wall_anchored == {5: True}
        assert len(parsed.forbidden_zones) == 2

    def test_34_string_keys_round_trip(self):
        d = {"moveRadii": {"7": 0.0}, "forbiddenZones": [zone(1, 1, 2, 2)], "rotationAllowed": {}, "wallAnchored": {}}
        c = parse_constraints(d)
        assert c.move_radii == {7: 0.0}

    def test_35_zone_bounds_normalized(self):
        c = SearchConstraints(forbidden_zones=[zone(2, 2, 1, 1)])
        assert c.forbidden_zones[0].bounds == (2.0, 2.0, 3.0, 3.0)


from shapely.geometry import box  # noqa: E402
