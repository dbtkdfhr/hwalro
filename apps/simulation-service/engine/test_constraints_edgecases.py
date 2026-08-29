from shapely.geometry import box

from constraints import FIXED, FREE, WITHIN_ZONE, SearchConstraints, parse_constraints


def zone(x: float, y: float, width: float, height: float) -> dict[str, float]:
    return {"x": x, "y": y, "width": width, "height": height}


def test_parse_three_movement_policies() -> None:
    constraints = parse_constraints(
        {"movementPolicies": {"1": FREE, "2": WITHIN_ZONE, "3": FIXED}}
    )

    assert constraints.movement_policy_of(1) == FREE
    assert constraints.movement_policy_of(2) == WITHIN_ZONE
    assert constraints.movement_policy_of(3) == FIXED


def test_missing_engine_constraints_remain_unrestricted() -> None:
    constraints = SearchConstraints()

    assert constraints.movement_policy_of(1) == FREE
    assert constraints.can_move(1) is True


def test_free_policy_can_move_without_a_zone() -> None:
    constraints = SearchConstraints({1: FREE})

    assert constraints.can_move(1) is True
    assert constraints.allows_placement(1, box(100, 100, 101, 101)) is True


def test_fixed_policy_never_allows_a_placement() -> None:
    constraints = SearchConstraints({1: FIXED})

    assert constraints.can_move(1) is False
    assert constraints.allows_placement(1, box(0, 0, 1, 1)) is False


def test_within_zone_requires_the_whole_geometry_to_fit() -> None:
    constraints = SearchConstraints({1: WITHIN_ZONE}, {1: zone(0, 0, 10, 10)})

    assert constraints.can_move(1) is True
    assert constraints.allows_placement(1, box(1, 1, 9, 9)) is True
    assert constraints.allows_placement(1, box(9, 9, 11, 11)) is False


def test_within_zone_without_membership_is_fail_closed() -> None:
    constraints = SearchConstraints({1: WITHIN_ZONE})

    assert constraints.can_move(1) is False
    assert constraints.allows_placement(1, box(0, 0, 1, 1)) is False


def test_invalid_policy_is_ignored_and_uses_unrestricted_engine_default() -> None:
    constraints = parse_constraints({"movementPolicies": {"1": "TELEPORT"}})

    assert constraints.movement_policy_of(1) == FREE


def test_forbidden_zone_intersection_is_detected() -> None:
    constraints = SearchConstraints(forbidden_zones=[zone(5, 5, 2, 2)])

    assert constraints.intersects_forbidden_zone(box(6, 6, 8, 8)) is True
    assert constraints.intersects_forbidden_zone(box(0, 0, 1, 1)) is False


def test_round_trip_preserves_policies_zones_and_exclusions() -> None:
    original = SearchConstraints(
        {1: WITHIN_ZONE, 2: FREE, 3: FIXED},
        {1: zone(0, 0, 10, 12)},
        [zone(5, 5, 2, 2)],
    )

    restored = parse_constraints(original.to_dict())

    assert restored.to_dict() == original.to_dict()
