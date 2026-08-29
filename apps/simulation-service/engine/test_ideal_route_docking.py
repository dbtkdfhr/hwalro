from __future__ import annotations

from decimal import Decimal
from unittest.mock import patch

from shapely.geometry import LineString

from constraints import SearchConstraints, WITHIN_ZONE
from ideal_route_docking import (
    Drawing,
    Rectangle,
    RouteOpportunity,
    _route_interference,
    generate_docking_candidates,
)
from ideal_route_docking_placement import Placement, geometry, push_placements
import layout_search
from route_planner import (
    GRID_STEP_METERS,
    GridRouter,
    build_routing_geometry,
    build_walkable_geometry,
    parse_exits,
    relocate_agents,
)


def room_drawing(fabrics: list[Rectangle]) -> Drawing:
    return {
        "outsideBoundary": [
            {"x": 0.0, "y": 0.0},
            {"x": 12.0, "y": 0.0},
            {"x": 12.0, "y": 10.0},
            {"x": 0.0, "y": 10.0},
        ],
        "walls": [],
        "pillars": [],
        "fabrics": fabrics,
        "exits": [{"id": 1, "startX": 5.0, "startY": 10.0, "endX": 7.0, "endY": 10.0}],
    }


def fabric(fabric_id: int, start_x: float, start_y: float, end_x: float, end_y: float) -> Rectangle:
    return {
        "id": fabric_id,
        "startX": start_x,
        "startY": start_y,
        "endX": end_x,
        "endY": end_y,
        "rotation": 0.0,
    }


def test_prefers_storage_positions_outside_the_ideal_route_flow() -> None:
    opportunity = RouteOpportunity(
        LineString(((6.0, 0.0), (6.0, 10.0))).buffer(0.5),
        100.0,
        10.0,
    )
    on_route = Placement(
        {"startX": 5.5, "startY": 0.0, "endX": 6.5, "endY": 2.0, "rotation": 0.0},
        LineString(((5.5, 0.0), (6.5, 2.0))).envelope,
        1.0,
    )
    off_route = Placement(
        {"startX": 0.0, "startY": 0.0, "endX": 1.0, "endY": 2.0, "rotation": 0.0},
        LineString(((0.0, 0.0), (1.0, 2.0))).envelope,
        5.0,
    )

    ranked = sorted(
        [on_route, off_route],
        key=lambda placement: (_route_interference(placement, [opportunity]), placement.travel),
    )

    assert ranked == [off_route, on_route]


def test_pushes_fabric_to_the_limit_along_both_route_normals() -> None:
    drawing = room_drawing([fabric(10, 5.5, 4.0, 6.5, 6.0)])

    result = push_placements(drawing, drawing["fabrics"][0], [], SearchConstraints(), [90.0])

    centers = sorted(round(placement.geometry.centroid.x, 3) for placement in result)
    assert centers[0] == 0.5
    assert centers[-1] == 11.5
    assert all(round(placement.geometry.centroid.y, 3) == 5.0 for placement in result)


def test_rotates_long_side_parallel_to_route_when_that_pushes_farther() -> None:
    drawing = room_drawing([fabric(10, 4.5, 4.5, 7.5, 5.5)])

    result = push_placements(drawing, drawing["fabrics"][0], [], SearchConstraints(), [90.0])

    left = min(result, key=lambda placement: placement.geometry.centroid.x)
    assert left.state["rotation"] == 90.0
    assert round(left.geometry.centroid.x, 3) == 0.5
    assert geometry(left.state).bounds[0] == left.geometry.bounds[0]


def test_docks_blocking_fabric_parallel_to_boundary() -> None:
    drawing = room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)])

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=3,
        constraints=None,
    )

    assert result
    operation = result[0]["ops"][0]
    assert operation["fabricId"] == 10
    assert operation["after"]["rotation"] in (0.0, 90.0, 180.0, 270.0)
    assert operation["before"] != operation["after"]


def test_combines_multiple_blockers_in_one_complete_layout() -> None:
    drawing = room_drawing(
        [
            fabric(10, 5.0, 3.0, 7.0, 4.0),
            fabric(11, 5.0, 6.0, 7.0, 7.0),
        ]
    )

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=3,
        constraints=None,
    )

    assert result
    assert all({operation["fabricId"] for operation in candidate["ops"]} == {10, 11} for candidate in result)


def test_keeps_independent_ideal_routes_as_separate_complete_layouts() -> None:
    drawing = room_drawing(
        [
            fabric(10, 2.5, 3.0, 3.5, 7.0),
            fabric(11, 8.5, 3.0, 9.5, 7.0),
        ]
    )
    opportunities = [
        RouteOpportunity(LineString(((3.0, 1.0), (3.0, 9.0))).buffer(0.25), 100.0, 10.0),
        RouteOpportunity(LineString(((9.0, 1.0), (9.0, 9.0))).buffer(0.25), 100.0, 10.0),
    ]

    with patch("ideal_route_docking._ideal_opportunities", return_value=opportunities):
        result = generate_docking_candidates(
            drawing=drawing,
            agents=[(3.0, 1.0), (9.0, 1.0)],
            selected_exit_ids=[1],
            hazards=(),
            max_candidates=3,
            constraints=None,
        )

    assert result
    operation_sets = {
        frozenset(operation["fabricId"] for operation in candidate["ops"])
        for candidate in result
    }
    assert frozenset({10}) in operation_sets
    assert frozenset({11}) in operation_sets
    assert frozenset({10, 11}) not in operation_sets


def test_uses_the_trial_budget_for_distinct_route_groups_before_position_variants() -> None:
    drawing = room_drawing(
        [
            fabric(10, 1.5, 3.0, 2.5, 7.0),
            fabric(11, 4.0, 3.0, 5.0, 7.0),
            fabric(12, 7.0, 3.0, 8.0, 7.0),
            fabric(13, 9.5, 3.0, 10.5, 7.0),
        ]
    )
    dominant = RouteOpportunity(LineString(((2.0, 1.0), (2.0, 9.0))).buffer(0.25), 100.0, 10.0)
    opportunities = [
        dominant,
        dominant,
        dominant,
        RouteOpportunity(LineString(((4.5, 1.0), (4.5, 9.0))).buffer(0.25), 100.0, 1.0),
        RouteOpportunity(LineString(((7.5, 1.0), (7.5, 9.0))).buffer(0.25), 100.0, 1.0),
        RouteOpportunity(LineString(((10.0, 1.0), (10.0, 9.0))).buffer(0.25), 100.0, 1.0),
    ]

    with patch("ideal_route_docking._ideal_opportunities", return_value=opportunities):
        result = generate_docking_candidates(
            drawing=drawing,
            agents=[(2.0, 1.0), (2.0, 1.0), (2.0, 1.0), (4.5, 1.0), (7.5, 1.0), (10.0, 1.0)],
            selected_exit_ids=[1],
            hazards=(),
            max_candidates=3,
            constraints=None,
        )

    operation_sets = [
        frozenset(operation["fabricId"] for operation in candidate["ops"])
        for candidate in result
    ]
    assert frozenset({10}) in operation_sets
    assert len(set(operation_sets)) == 3


def test_candidate_limit_bounds_complete_layouts() -> None:
    drawing = room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)])

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=1,
        constraints=None,
    )

    assert len(result) == 1


def test_scores_each_completed_layout_by_its_realized_routes() -> None:
    drawing = room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)])
    agents = [(6.0, 1.0)]

    result = generate_docking_candidates(
        drawing=drawing,
        agents=agents,
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=3,
        constraints=None,
    )

    current_area = build_routing_geometry(drawing, GRID_STEP_METERS)
    current_agents, _ = relocate_agents(current_area, agents)
    current_router = GridRouter(
        current_area,
        (),
        parse_exits(drawing, [1]),
        step=GRID_STEP_METERS,
        physical_walkable=build_walkable_geometry(drawing),
    )
    current_cost = current_router.plan_cost(current_agents[0])[0]
    for candidate in result:
        after_by_id = {operation["fabricId"]: operation["after"] for operation in candidate["ops"]}
        completed: Drawing = {
            **drawing,
            "fabrics": [{**item, **after_by_id.get(item["id"], {})} for item in drawing["fabrics"]],
        }
        completed_area = build_routing_geometry(completed, GRID_STEP_METERS)
        completed_agents, _ = relocate_agents(completed_area, agents)
        completed_router = GridRouter(
            completed_area,
            (),
            parse_exits(completed, [1]),
            step=GRID_STEP_METERS,
            physical_walkable=build_walkable_geometry(completed),
        )
        realized_saving = current_cost - completed_router.plan_cost(completed_agents[0])[0]

        assert candidate["recoveredRouteCost"] == round(realized_saving, 6)
        assert candidate["recoveredRouteCost"] > 0.0


def test_candidate_preserves_exact_database_fabric_area() -> None:
    drawing = room_drawing([fabric(10, 5.9057, 3.1115, 6.0942, 6.3)])

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=1,
        constraints=None,
    )

    operation = result[0]["ops"][0]
    before = operation["before"]
    after = operation["after"]
    before_area = (Decimal(str(before["endX"])) - Decimal(str(before["startX"]))) * (
        Decimal(str(before["endY"])) - Decimal(str(before["startY"]))
    )
    after_area = (Decimal(str(after["endX"])) - Decimal(str(after["startX"]))) * (
        Decimal(str(after["endY"])) - Decimal(str(after["startY"]))
    )

    assert after_area == before_area


def test_docking_never_places_a_fabric_across_an_internal_wall() -> None:
    drawing = room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)])
    drawing["walls"] = [{"startX": 1.0, "startY": 0.0, "endX": 1.0, "endY": 10.0}]
    wall = LineString(((1.0, 0.0), (1.0, 10.0)))

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=3,
        constraints=None,
    )

    assert result
    for candidate in result:
        for operation in candidate["ops"]:
            geometry = layout_search._rect_geometry(operation["after"])
            assert not geometry.crosses(wall)
            assert not geometry.contains(wall)


def test_within_zone_docks_only_inside_its_assigned_zone() -> None:
    drawing = room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)])
    constraints = SearchConstraints(
        {10: WITHIN_ZONE},
        {10: {"x": 4.0, "y": 2.0, "width": 4.0, "height": 6.0}},
    )

    result = generate_docking_candidates(
        drawing=drawing,
        agents=[(6.0, 1.0)],
        selected_exit_ids=[1],
        hazards=(),
        max_candidates=3,
        constraints=constraints,
    )

    assert result
    for candidate in result:
        for operation in candidate["ops"]:
            assert constraints.allows_placement(
                operation["fabricId"],
                layout_search._rect_geometry(operation["after"]),
            )


def test_layout_search_routes_production_mode_to_docking_planner() -> None:
    result = layout_search.generate(
        {
            "plannerMode": "IDEAL_ROUTE_DOCKING",
            "drawing": room_drawing([fabric(10, 5.25, 3.5, 6.75, 6.5)]),
            "agents": [{"x": 6.0, "y": 1.0}],
            "hazards": [],
            "selectedExitIds": [1],
            "maxCandidates": 1,
            "constraints": None,
        }
    )

    assert result["plannerVersion"] == "IDEAL_ROUTE_DOCKING_V2"
    assert result["candidates"][0]["operatorType"] == "BOUNDARY_DOCKING"
    assert len(result["candidates"][0]["ops"]) == 1
