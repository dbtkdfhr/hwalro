from __future__ import annotations

import math

import pytest
from shapely.geometry import LineString
from shapely.geometry import box

from constraints import SearchConstraints
import ideal_flow
import shape_sensitivity


def test_aggregates_exact_shared_edges_without_route_clustering() -> None:
    # Given: two agent paths that share exactly one directed grid edge.
    routes = (
        ideal_flow.AgentIdealPath(0, "west", ((-1.0, 0.0), (0.0, 1.0), (0.0, 2.0)), 3.0),
        ideal_flow.AgentIdealPath(1, "east", ((1.0, 0.0), (0.0, 1.0), (0.0, 2.0)), 3.0),
    )

    # When: demand is aggregated by the exact canonical edge key.
    analysis = ideal_flow.aggregate_flow(routes, box(-2.0, -1.0, 2.0, 3.0), (), 0.25)

    # Then: only the shared edge carries both agents' demand.
    shared = next(edge for edge in analysis.edges if edge.start == (0.0, 1.0))
    assert shared.end == (0.0, 2.0)
    assert shared.demand == 2


def test_forms_one_hyperedge_for_structures_jointly_blocking_a_route() -> None:
    # Given: a single ideal route crossing two movable structures in sequence.
    routes = (ideal_flow.AgentIdealPath(7, "north", ((0.0, 0.0), (0.0, 3.0)), 3.0),)
    blockers = (
        ideal_flow.FabricObstacle(10, box(-0.4, 0.6, 0.4, 1.2)),
        ideal_flow.FabricObstacle(11, box(-0.4, 1.8, 0.4, 2.4)),
    )

    # When: blocker incidence is accumulated from route-corridor intersections.
    analysis = ideal_flow.aggregate_flow(routes, box(-2.0, -1.0, 2.0, 4.0), blockers, 0.25)

    # Then: the joint cut remains one exact hyperedge rather than two guesses.
    hyperedge = analysis.blocker_hyperedges[0]
    transverse_width = 4.0
    capacity = transverse_width / (2.0 * 0.25)
    expected_delay = 3.0 * (1.0 + (1.0 / capacity) ** 2)
    assert hyperedge.fabric_ids == (10, 11)
    assert hyperedge.agent_indices == (7,)
    assert hyperedge.demand == 1
    assert hyperedge.delay == pytest.approx(expected_delay)


def test_local_delay_increases_when_the_same_demand_has_less_capacity() -> None:
    # Given: one shared route with equal demand in a narrow and a wide corridor.
    routes = (
        ideal_flow.AgentIdealPath(0, "north", ((0.0, 0.0), (0.0, 2.0)), 2.0),
        ideal_flow.AgentIdealPath(1, "north", ((0.0, 0.0), (0.0, 2.0)), 2.0),
    )

    # When: exact flow is evaluated against each local clear width.
    narrow = ideal_flow.aggregate_flow(routes, box(-0.5, -1.0, 0.5, 3.0), (), 0.25)
    wide = ideal_flow.aggregate_flow(routes, box(-2.0, -1.0, 2.0, 3.0), (), 0.25)

    # Then: the narrow edge has lower capacity and strictly higher delay.
    assert narrow.edges[0].capacity < wide.edges[0].capacity
    assert narrow.edges[0].delay > wide.edges[0].delay


def test_transverse_capacity_ignores_a_nearby_longitudinal_end_wall() -> None:
    # Given: a short horizontal route edge near the end of a 20-metre-wide corridor.
    route = ideal_flow.AgentIdealPath(
        0, "east", ((0.25, 0.0), (1.25, 0.0)), 1.0
    )

    # When: local flow capacity is measured at that edge.
    analysis = ideal_flow.aggregate_flow(
        (route,), box(0.0, -10.0, 100.0, 10.0), (), 0.25
    )

    # Then: the transverse 20-metre section, not the nearby end wall, sets capacity.
    assert analysis.edges[0].capacity == pytest.approx(20.0 / (2.0 * 0.25))


def test_transverse_capacity_stops_at_a_fixed_obstacle() -> None:
    # Given: a horizontal route whose upward ray meets a fixed obstacle at y=2.
    route = ideal_flow.AgentIdealPath(
        0, "east", ((10.0, 0.0), (11.0, 0.0)), 1.0
    )
    walkable = box(0.0, -10.0, 100.0, 10.0).difference(
        box(0.0, 2.0, 100.0, 4.0)
    )

    # When: local flow capacity is measured through the remaining connected section.
    analysis = ideal_flow.aggregate_flow((route,), walkable, (), 0.25)

    # Then: only the 12-metre connected span from y=-10 to y=2 is available.
    assert analysis.edges[0].capacity == pytest.approx(12.0 / (2.0 * 0.25))


def test_transverse_capacity_uses_minimum_when_cross_section_is_empty() -> None:
    # Given: an exit-connector edge whose midpoint lies outside routing geometry.
    route = ideal_flow.AgentIdealPath(
        0, "east", ((-1.0, 0.0), (-0.5, 0.0)), 0.5
    )

    # When: its normal ray has no intersection with the walkable area.
    analysis = ideal_flow.aggregate_flow(
        (route,), box(0.0, -1.0, 2.0, 1.0), (), 0.25
    )

    # Then: capacity uses one agent diameter instead of evaluating an empty span.
    assert analysis.edges[0].capacity == pytest.approx(1.0)


def test_builds_agent_weighted_ideal_routes_to_multiple_exits() -> None:
    # Given: agents at opposite sides and a movable structure between them.
    drawing = {
        "outsideBoundary": [
            {"x": 0.0, "y": 0.0},
            {"x": 10.0, "y": 0.0},
            {"x": 10.0, "y": 6.0},
            {"x": 0.0, "y": 6.0},
        ],
        "walls": [],
        "pillars": [],
        "fabrics": [
            {"id": 10, "startX": 4.5, "startY": 2.0, "endX": 5.5, "endY": 4.0, "rotation": 0.0}
        ],
        "exits": [
            {"id": 1, "startX": 0.0, "startY": 2.0, "endX": 0.0, "endY": 4.0},
            {"id": 2, "startX": 10.0, "startY": 2.0, "endX": 10.0, "endY": 4.0},
        ],
    }

    # When: ideal flow is solved with every movable structure absent.
    analysis = ideal_flow.build_ideal_flow(
        drawing, ((2.0, 3.0), (8.0, 3.0)), (1, 2), (), SearchConstraints()
    )

    # Then: each agent contributes once and independently selects its nearest exit.
    assert tuple(route.agent_index for route in analysis.routes) == (0, 1)
    assert tuple(route.exit_id for route in analysis.routes) == ("1", "2")
    assert sum(edge.demand for edge in analysis.edges) >= 2


def test_build_ideal_flow_removes_movable_obstacle_before_routing() -> None:
    # Given: the only ideal path to the exit crosses one movable structure.
    movable = box(4.5, 2.0, 5.5, 4.0)
    drawing = {
        "outsideBoundary": [
            {"x": 0.0, "y": 0.0},
            {"x": 10.0, "y": 0.0},
            {"x": 10.0, "y": 6.0},
            {"x": 0.0, "y": 6.0},
        ],
        "walls": [],
        "pillars": [],
        "fabrics": [
            {
                "id": 10,
                "startX": 4.5,
                "startY": 2.0,
                "endX": 5.5,
                "endY": 4.0,
                "rotation": 0.0,
            }
        ],
        "exits": [
            {
                "id": 1,
                "startX": 10.0,
                "startY": 2.0,
                "endX": 10.0,
                "endY": 4.0,
            }
        ],
    }

    # When: the ideal route is solved under the default movable constraint.
    analysis = ideal_flow.build_ideal_flow(
        drawing, ((2.0, 3.0),), (1,), (), SearchConstraints()
    )

    # Then: the route crosses the structure's former footprint, proving it was absent.
    assert LineString(analysis.routes[0].points).intersects(movable)


def test_build_ideal_flow_checks_cancellation_through_preprocessing() -> None:
    # Given: one route and a checkpoint callback that records every invocation.
    drawing = {
        "outsideBoundary": [
            {"x": 0.0, "y": 0.0},
            {"x": 4.0, "y": 0.0},
            {"x": 4.0, "y": 4.0},
            {"x": 0.0, "y": 4.0},
        ],
        "walls": [],
        "pillars": [],
        "fabrics": [],
        "exits": [
            {
                "id": 1,
                "startX": 4.0,
                "startY": 1.0,
                "endX": 4.0,
                "endY": 3.0,
            }
        ],
    }
    checkpoints: list[int] = []

    def checkpoint() -> None:
        checkpoints.append(len(checkpoints) + 1)

    # When: preprocessing builds and aggregates the ideal route.
    ideal_flow.build_ideal_flow(
        drawing,
        ((1.0, 2.0),),
        (1,),
        (),
        SearchConstraints(),
        checkpoint=checkpoint,
    )

    # Then: work is interruptible before geometry, agent routing, and aggregation.
    assert checkpoints == [1, 2, 3]


def test_shape_force_is_normal_to_parallel_and_perpendicular_blockers() -> None:
    # Given: a vertical high-delay flow and two blockers offset to its right.
    edge = ideal_flow.FlowEdge((0.0, -2.0), (0.0, 2.0), 12, 1.0, 80.0)
    blockers = (
        ideal_flow.FabricObstacle(10, box(0.1, -1.0, 0.5, 1.0)),
        ideal_flow.FabricObstacle(11, box(0.1, -0.2, 2.1, 0.2)),
    )

    # When: translation and rotation derivatives share one obstruction objective.
    signals = shape_sensitivity.shape_signals(blockers, (edge,), 0.5)

    # Then: both translation forces point away from the route, not along it.
    assert all(signal.force_x > 0.0 for signal in signals)
    assert all(abs(signal.force_y) < signal.force_x * 0.01 for signal in signals)


def test_shape_torque_is_zero_at_symmetry_and_turns_an_oblique_blocker_parallel() -> None:
    # Given: a vertical route, one aligned blocker, and one 30-degree blocker.
    edge = ideal_flow.FlowEdge((0.0, -2.0), (0.0, 2.0), 8, 1.0, 40.0)
    aligned = ideal_flow.FabricObstacle(10, box(-0.2, -1.0, 0.2, 1.0))
    oblique = ideal_flow.FabricObstacle(
        11, shape_sensitivity.rotate_about_center(box(-1.0, -0.2, 1.0, 0.2), 30.0)
    )

    # When: angular sensitivity is evaluated symmetrically.
    aligned_signal, oblique_signal = shape_sensitivity.shape_signals(
        (aligned, oblique), (edge,), 0.5
    )

    # Then: symmetry has zero torque and the oblique pose turns toward 90 degrees.
    assert math.isclose(aligned_signal.torque, 0.0, abs_tol=1e-6)
    assert oblique_signal.torque > 0.0


def test_rejects_non_positive_clearance_at_the_flow_boundary() -> None:
    # Given: a zero-width agent corridor at the public aggregation boundary.
    routes = (ideal_flow.AgentIdealPath(0, "north", ((0.0, 0.0), (0.0, 1.0)), 1.0),)

    # When: flow aggregation parses the invalid clearance.
    with pytest.raises(ideal_flow.InvalidClearanceError) as captured:
        ideal_flow.aggregate_flow(routes, box(-1.0, -1.0, 1.0, 2.0), (), 0.0)

    # Then: the typed error preserves the rejected numeric value.
    assert captured.value.clearance == 0.0
