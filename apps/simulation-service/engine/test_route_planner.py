import math
from types import MappingProxyType
import unittest
from unittest.mock import patch

import numpy as np
from shapely.geometry import LineString, Point, Polygon, box

import route_planner
from route_planner import (
    AgentRouteUnreachableError,
    Exit,
    GridRouter,
    Hazard,
    build_routing_geometry,
    build_walkable_geometry,
    edge_cost,
    hazard_multiplier,
    select_accessible_component,
    select_agent_component,
    split_agent_components,
    usable_exit_segment,
    _simplify_collinear,
)


class HazardCostTest(unittest.TestCase):
    def test_radial_exponential_values_and_outside(self):
        hazard = Hazard(0.0, 0.0, 2.0)

        self.assertEqual(hazard_multiplier((2.01, 0.0), [hazard]), 1.0)
        self.assertAlmostEqual(hazard_multiplier((2.0, 0.0), [hazard]), 5.0)
        self.assertAlmostEqual(hazard_multiplier((1.0, 0.0), [hazard]), 50.0)
        self.assertAlmostEqual(hazard_multiplier((0.0, 0.0), [hazard]), 500.0)

    def test_overlapping_hazards_use_maximum(self):
        hazards = [Hazard(0.0, 0.0, 2.0), Hazard(1.0, 0.0, 2.0)]
        expected = max(hazard_multiplier((0.25, 0.0), [hazard]) for hazard in hazards)

        self.assertAlmostEqual(hazard_multiplier((0.25, 0.0), hazards), expected)

    def test_edge_cost_uses_start_midpoint_and_end(self):
        hazard = Hazard(0.0, 0.0, 2.0)
        start, end = (0.0, 0.0), (2.0, 0.0)
        expected = 2.0 / 6.0 * (500.0 + 4.0 * 50.0 + 5.0)

        self.assertAlmostEqual(edge_cost(start, end, [hazard]), expected)
        self.assertAlmostEqual(edge_cost((0.0, 0.0), (0.25, 0.0), []), 0.25)


class VectorCostTest(unittest.TestCase):
    """The array forms feed the cost field, so they must agree to the last bit."""

    HAZARDS = (Hazard(1.0, 1.0, 2.0), Hazard(3.5, 0.5, 1.25))

    def _points(self):
        values = np.arange(0.0, 4.0, 0.25)
        x, y = np.meshgrid(values, values)
        return x.ravel(), y.ravel()

    def test_hazard_multipliers_match_the_scalar_function(self):
        x, y = self._points()
        for hazards in ((), self.HAZARDS[:1], self.HAZARDS):
            with self.subTest(count=len(hazards)):
                expected = np.array(
                    [hazard_multiplier((px, py), hazards) for px, py in zip(x, y)]
                )

                np.testing.assert_array_equal(
                    route_planner.hazard_multipliers(x, y, hazards), expected
                )

    def test_edge_costs_match_the_scalar_function(self):
        x, y = self._points()
        for dx, dy in ((0.25, 0.0), (0.0, 0.25), (0.25, 0.25), (-0.25, 0.25)):
            for hazards in ((), self.HAZARDS):
                with self.subTest(move=(dx, dy), count=len(hazards)):
                    expected = np.array(
                        [
                            edge_cost((px, py), (px + dx, py + dy), hazards)
                            for px, py in zip(x, y)
                        ]
                    )

                    np.testing.assert_array_equal(
                        route_planner.edge_costs(x, y, x + dx, y + dy, hazards), expected
                    )


class PlanCostTest(unittest.TestCase):
    def test_plan_cost_matches_the_full_route(self):
        walkable = box(0, 0, 6, 4).difference(box(2, 1, 3, 3))
        router = GridRouter(walkable, [Hazard(4.0, 3.0, 1.0)], [Exit(1, (6, 1.5), (6, 2.5))])

        for start in ((0.4, 0.4), (1.0, 2.0), (4.5, 3.5), (5.5, 0.5)):
            with self.subTest(start=start):
                route = router.plan(start)

                self.assertEqual(router.plan_cost(start), (route.total_cost, route.exit_id))

    def test_plan_cost_rejects_what_plan_rejects(self):
        router = GridRouter(box(0, 0, 4, 4), [], [Exit(1, (4, 1), (4, 3))])

        with self.assertRaises(ValueError):
            router.plan_cost((9.0, 9.0))
        with self.assertRaises(ValueError):
            router.plan_cost((float("nan"), 1.0))


class GeometryTest(unittest.TestCase):
    def test_routing_geometry_reserves_agent_radius_without_changing_physical_geometry(self):
        drawing = {
            "outsideBoundary": [
                {"x": 0, "y": 0},
                {"x": 5, "y": 0},
                {"x": 5, "y": 5},
                {"x": 0, "y": 5},
            ],
            "walls": [{"startX": 2.5, "startY": 1, "endX": 2.5, "endY": 4}],
            "pillars": [
                {"startX": 0.5, "startY": 0.5, "endX": 1, "endY": 1, "rotation": 0}
            ],
            "fabrics": [],
        }

        physical = build_walkable_geometry(drawing)
        routing = build_routing_geometry(drawing, 0.3)

        self.assertTrue(physical.covers(Point(0.1, 2.5)))
        self.assertFalse(routing.covers(Point(0.29, 2.5)))
        self.assertTrue(routing.covers(Point(0.31, 2.5)))
        self.assertFalse(routing.covers(Point(2.21, 2.5)))
        self.assertTrue(routing.covers(Point(2.19, 2.5)))
        self.assertFalse(routing.covers(Point(1.29, 0.75)))
        self.assertTrue(routing.covers(Point(1.31, 0.75)))

    def test_clearance_closes_passage_narrower_than_agent_diameter(self):
        drawing = {
            "outsideBoundary": [
                {"x": 0, "y": 0},
                {"x": 6, "y": 0},
                {"x": 6, "y": 4},
                {"x": 0, "y": 4},
            ],
            "walls": [
                {"startX": 3, "startY": 0, "endX": 3, "endY": 1.75},
                {"startX": 3, "startY": 2.25, "endX": 3, "endY": 4},
            ],
            "pillars": [],
            "fabrics": [],
        }

        routing = build_routing_geometry(drawing, 0.3)

        with self.assertRaisesRegex(ValueError, "distributed across disconnected"):
            select_agent_component(routing, [(1, 2), (5, 2)])

    def test_walkable_subtracts_thin_wall_and_rotated_rectangles(self):
        drawing = {
            "outsideBoundary": [
                {"x": 0, "y": 0},
                {"x": 4, "y": 0},
                {"x": 4, "y": 4},
                {"x": 0, "y": 4},
            ],
            "walls": [{"startX": 2, "startY": 0, "endX": 2, "endY": 4}],
            "pillars": [
                {"startX": 0.5, "startY": 0.5, "endX": 1.5, "endY": 1.5, "rotation": 45}
            ],
            "fabrics": [],
        }

        walkable = build_walkable_geometry(drawing)

        self.assertFalse(walkable.covers(LineString(((1.0, 2.0), (3.0, 2.0)))))
        self.assertFalse(walkable.covers(box(0.99, 0.99, 1.01, 1.01)))

    def test_selects_the_only_component_containing_agents_and_exits(self):
        drawing = self._drawing_with_enclosed_room()
        walkable = build_walkable_geometry(drawing)

        selected = select_accessible_component(
            walkable,
            [(1.0, 1.0)],
            [Exit(1, (0.0, 2.5), (0.0, 3.5))],
        )

        self.assertEqual(selected.geom_type, "Polygon")
        self.assertTrue(selected.covers(box(0.9, 0.9, 1.1, 1.1)))
        self.assertFalse(selected.covers(box(2.9, 2.9, 3.1, 3.1)))

    def test_rejects_agents_in_disconnected_components(self):
        walkable = build_walkable_geometry(self._drawing_with_enclosed_room())

        with self.assertRaisesRegex(ValueError, "distributed across disconnected"):
            select_accessible_component(
                walkable,
                [(1.0, 1.0), (3.0, 3.0)],
                [Exit(1, (0.0, 2.5), (0.0, 3.5))],
            )

    def test_groups_agents_in_disconnected_components(self):
        walkable = build_walkable_geometry(self._drawing_with_enclosed_room())

        groups = split_agent_components(walkable, [(1.0, 1.0), (3.0, 3.0)])

        self.assertEqual(len(groups), 2)
        self.assertEqual(
            sorted(index for _component, agents in groups for index, _position in agents),
            [0, 1],
        )

    @staticmethod
    def _drawing_with_enclosed_room():
        return {
            "outsideBoundary": [
                {"x": 0, "y": 0},
                {"x": 6, "y": 0},
                {"x": 6, "y": 6},
                {"x": 0, "y": 6},
            ],
            "walls": [
                {"startX": 2, "startY": 2, "endX": 4, "endY": 2},
                {"startX": 4, "startY": 2, "endX": 4, "endY": 4},
                {"startX": 4, "startY": 4, "endX": 2, "endY": 4},
                {"startX": 2, "startY": 4, "endX": 2, "endY": 2},
            ],
            "pillars": [],
            "fabrics": [],
        }


class GridRoutingTest(unittest.TestCase):
    def test_path_simplification_keeps_bends_and_blocked_shortcuts(self):
        bent = [(0.0, 0.0), (1.0, 0.6), (2.0, 1.0)]
        self.assertEqual(_simplify_collinear(bent, lambda _start, _end: True), bent)

        collinear = [(0.0, 0.0), (1.0, 1.0), (2.0, 2.0)]
        self.assertEqual(
            _simplify_collinear(collinear, lambda start, end: (start, end) != (collinear[0], collinear[2])),
            collinear,
        )

    def test_exit_seed_has_bounded_distance_and_clear_physical_connector(self):
        routing = box(0, 0, 1, 1)
        physical = box(0, 0, 1.2, 1)
        router = GridRouter(
            routing,
            [],
            [Exit(1, (1.2, 0.1), (1.2, 0.9))],
            physical_walkable=physical,
        )

        route = router.plan((0.25, 0.5))

        self.assertLessEqual(LineString((route.waypoints[-1], route.terminal_point)).length, 0.25 * 2**0.5)
        self.assertTrue(physical.covers(LineString((route.waypoints[-1], route.terminal_point))))
        self.assertAlmostEqual(route.waypoints[-1][1], route.terminal_point[1])
        self.assertGreaterEqual(route.terminal_point[1], 0.4)
        self.assertLessEqual(route.terminal_point[1], 0.6)

    def test_exit_seed_does_not_fall_back_to_a_distant_grid_point(self):
        with self.assertRaisesRegex(ValueError, "reachable from this walkable component"):
            GridRouter(
                box(0, 0, 1, 1),
                [],
                [Exit(1, (1.4, 0.1), (1.4, 0.9))],
                physical_walkable=box(0, 0, 1.4, 1),
            )

    def test_exit_seed_rejects_connector_crossing_physical_wall(self):
        physical = box(0, 0, 1.2, 1).difference(
            LineString(((1, 0), (1, 1))).buffer(0.01, cap_style="flat")
        )

        with self.assertRaisesRegex(ValueError, "reachable from this walkable component"):
            GridRouter(
                box(0, 0, 0.9, 1),
                [],
                [Exit(1, (1.2, 0.1), (1.2, 0.9))],
                physical_walkable=physical,
            )

    def _seed_coordinates(self, router, exit_):
        return {
            (round(target[0], 6), round(target[1], 6), round(approach[0], 6), round(approach[1], 6))
            for _node, target, approach in router._exit_seeds(exit_)
        }

    def test_exit_seeds_exclude_endpoint_clamped_for_long_exit_with_interior_seeds(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )
        exit_ = Exit(1, (10, 1), (10, 7))
        usable_start, usable_end = usable_exit_segment(exit_, 0.3)
        with patch("route_planner.EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS", 1e9):
            complete = self._seed_coordinates(router, exit_)
        default = self._seed_coordinates(router, exit_)

        self.assertGreater(len(default), 0)
        self.assertLess(len(default), len(complete))
        self.assertTrue(default.issubset(complete))
        removed = complete - default
        self.assertGreater(len(removed), 0)
        for _target_x, _target_y, _approach_x, _approach_y in removed:
            self.assertIn((_target_x, _target_y), (usable_start, usable_end))

    def test_exit_seeds_keep_all_when_no_interior_seed_exists(self):
        router = GridRouter(
            box(0, 6.7, 2, 8),
            [],
            [Exit(1, (2, 1), (2, 7))],
            physical_walkable=box(0, 6.7, 2, 8),
        )
        exit_ = Exit(1, (2, 1), (2, 7))
        with patch("route_planner.EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS", 1e9):
            complete = self._seed_coordinates(router, exit_)
        default = self._seed_coordinates(router, exit_)

        self.assertGreater(len(default), 0)
        self.assertEqual(default, complete)

    def test_exit_seeds_are_reversal_symmetric(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )
        forward = self._seed_coordinates(router, Exit(1, (10, 1), (10, 7)))
        reversed_router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 7), (10, 1))],
            physical_walkable=box(0, 0, 10, 8),
        )
        reversed_seeds = self._seed_coordinates(reversed_router, Exit(1, (10, 7), (10, 1)))

        self.assertEqual(forward, reversed_seeds)

    def test_exit_seeds_keep_all_for_short_exit(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 4))],
            physical_walkable=box(0, 0, 10, 8),
        )
        exit_ = Exit(1, (10, 1), (10, 4))
        usable_start, usable_end = usable_exit_segment(exit_, 0.3)
        self.assertLess(
            math.dist(usable_start, usable_end),
            route_planner.EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS,
        )
        with patch("route_planner.EXIT_SEED_ENDPOINT_EXCLUSION_MIN_USABLE_LENGTH_METERS", 1e9):
            complete = self._seed_coordinates(router, exit_)
        default = self._seed_coordinates(router, exit_)

        self.assertGreater(len(complete), 0)
        clamped = {
            coordinate
            for coordinate in complete
            if math.dist((coordinate[0], coordinate[1]), usable_start) <= 1e-6
            or math.dist((coordinate[0], coordinate[1]), usable_end) <= 1e-6
        }
        self.assertGreater(
            len(clamped), 0, "short-exit seed set must contain endpoint-clamped seeds"
        )
        self.assertEqual(default, complete)

    def test_exit_must_be_wider_than_agent_diameter(self):
        with self.assertRaisesRegex(ValueError, "wider than 0.6m"):
            usable_exit_segment(Exit(1, (1, 0.2), (1, 0.8)), 0.3)

    def test_exit_crossing_uses_only_the_trimmed_gate(self):
        exit_ = Exit(1, (2, 0.5), (2, 3.5))
        router = GridRouter(box(0, 0, 4, 4), [], [exit_])
        start, end = usable_exit_segment(exit_, 0.3)

        self.assertTrue(router.crossed_exit((1.9, 2), (2.1, 2), start, end))
        self.assertFalse(router.crossed_exit((1.9, 0.6), (2.1, 0.6), start, end))

    def test_exit_crossing_rejects_disjoint_bounds_before_geometry_checks(self):
        class UnexpectedGeometryCheck:
            def covers(self, _movement):
                raise AssertionError("disjoint segment must not reach GEOS")

        router = GridRouter(box(0, 0, 4, 4), [], [Exit(1, (4, 1), (4, 3))])
        router._prepared_physical_walkable = UnexpectedGeometryCheck()

        self.assertFalse(router.crossed_exit((0, 2), (1, 2), (3, 1), (3, 3)))

    def test_batch_geometry_predicates_match_scalar_results(self):
        walkable = box(0, 0, 4, 4).difference(box(1.5, 1.5, 2.5, 2.5))
        router = GridRouter(walkable, [], [Exit(1, (4, 1), (4, 3))])
        starts = np.asarray(((0.5, 0.5), (0.5, 2.0), (1.5, 1.0)))
        ends = np.asarray(((3.5, 0.5), (3.5, 2.0), (2.5, 1.0)))

        np.testing.assert_array_equal(
            router.can_connect_many(starts, ends),
            [router.can_connect(tuple(start), tuple(end)) for start, end in zip(starts, ends)],
        )
        np.testing.assert_array_equal(
            router.can_reach_exits(starts, ends),
            [router.can_reach_exit(tuple(start), tuple(end)) for start, end in zip(starts, ends)],
        )

        movement_starts = np.asarray(((2, 1), (3, 0.5), (0, 0.5), (0.5, 2)))
        movement_ends = np.asarray(((3, 1), (3, 1.5), (1, 0.5), (3.5, 2)))
        exit_starts = np.asarray(((3, 1), (3, 1), (3, 1), (3, 1)))
        exit_ends = np.asarray(((3, 3), (3, 3), (3, 3), (3, 3)))

        np.testing.assert_array_equal(
            router.crossed_exits(
                movement_starts, movement_ends, exit_starts, exit_ends
            ),
            [
                router.crossed_exit(tuple(start), tuple(end), tuple(exit_start), tuple(exit_end))
                for start, end, exit_start, exit_end in zip(
                    movement_starts, movement_ends, exit_starts, exit_ends
                )
            ],
        )
        np.testing.assert_array_equal(
            router.reached_exits(movement_ends, exit_starts, exit_ends),
            [
                router.reached_exit(tuple(position), tuple(exit_start), tuple(exit_end))
                for position, exit_start, exit_end in zip(
                    movement_ends, exit_starts, exit_ends
                )
            ],
        )

    def test_batch_geometry_predicates_validate_counts_and_accept_empty_inputs(self):
        router = GridRouter(box(0, 0, 4, 4), [], [Exit(1, (4, 1), (4, 3))])

        for result in (
            router.can_connect_many([], []),
            router.can_reach_exits([], []),
            router.crossed_exits([], [], [], []),
            router.reached_exits([], [], []),
        ):
            self.assertEqual(result.shape, (0,))
            self.assertEqual(result.dtype, np.dtype(bool))

        with self.assertRaisesRegex(ValueError, "counts must match"):
            router.can_connect_many([(0, 0)], [])
        with self.assertRaisesRegex(ValueError, "counts must match"):
            router.can_reach_exits([(0, 0)], [])
        with self.assertRaisesRegex(ValueError, "counts must match"):
            router.crossed_exits([(0, 0)], [(1, 0)], [], [])
        with self.assertRaisesRegex(ValueError, "counts must match"):
            router.reached_exits([(0, 0)], [], [])

        malformed = np.asarray((0.0, 0.0))
        with self.assertRaisesRegex(ValueError, r"shape \(2, 2\)"):
            router.can_connect_many(malformed, malformed)
        with self.assertRaisesRegex(ValueError, r"shape \(2, 2\)"):
            router.can_reach_exits(malformed, malformed)
        with self.assertRaisesRegex(ValueError, r"shape \(2, 2\)"):
            router.crossed_exits(malformed, malformed, malformed, malformed)
        with self.assertRaisesRegex(ValueError, r"shape \(2, 2\)"):
            router.reached_exits(malformed, malformed, malformed)

    def test_batch_exit_crossing_rejects_disjoint_bounds_before_geometry_checks(self):
        router = GridRouter(box(0, 0, 4, 4), [], [Exit(1, (4, 1), (4, 3))])

        with patch("route_planner.linestrings", side_effect=AssertionError("unexpected GEOS call")):
            result = router.crossed_exits(
                [(0, 2), (0, 3)],
                [(1, 2), (1, 3)],
                [(3, 1), (3, 1)],
                [(3, 3), (3, 3)],
            )

        np.testing.assert_array_equal(result, [False, False])

    def test_route_avoids_hazard_when_lower_total_cost_exists(self):
        walkable = Polygon(((0, 0), (6, 0), (6, 4), (0, 4)))
        hazard = Hazard(3.0, 2.0, 1.0)
        router = GridRouter(walkable, [hazard], [Exit(1, (6, 1.5), (6, 2.5))])

        route = router.plan((0.5, 2.0))

        self.assertEqual(route.exit_id, 1)
        self.assertTrue(any(abs(y - 2.0) >= 1.0 for _, y in route.waypoints))

    def test_agent_starting_in_hazard_initially_moves_to_lower_cost(self):
        walkable = Polygon(((0, 0), (6, 0), (6, 4), (0, 4)))
        hazard = Hazard(2.0, 2.0, 1.0)
        router = GridRouter(walkable, [hazard], [Exit(1, (6, 1.5), (6, 2.5))])

        route = router.plan((2.25, 2.0))

        self.assertGreater(len(route.waypoints), 1)
        self.assertLess(
            hazard_multiplier(route.waypoints[1], [hazard]),
            hazard_multiplier(route.waypoints[0], [hazard]),
        )

    def test_unavoidable_hazard_crossing_stays_away_from_center(self):
        walkable = Polygon(((0, 0), (6, 0), (6, 2), (0, 2)))
        hazard = Hazard(3.0, 1.0, 2.0)
        router = GridRouter(walkable, [hazard], [Exit(1, (6, 0.6), (6, 1.4))])

        route = router.plan((0.5, 1.0))

        crossing_y = next(
            y1 + (3.0 - x1) / (x2 - x1) * (y2 - y1)
            for (x1, y1), (x2, y2) in zip(route.waypoints, route.waypoints[1:])
            if x1 <= 3.0 <= x2 and x1 != x2
        )
        self.assertGreaterEqual(abs(crossing_y - hazard.center_y), 0.5)

    def test_global_minimum_selects_safe_exit_deterministically(self):
        walkable = Polygon(((0, 0), (8, 0), (8, 4), (0, 4)))
        exits = [Exit("left", (0, 1.5), (0, 2.5)), Exit("right", (8, 1.5), (8, 2.5))]
        router = GridRouter(walkable, [Hazard(1.5, 2.0, 2.0)], exits)
        reachable = router._reachable

        first = router.plan((3.5, 2.0))
        second = router.plan((3.5, 2.0))

        self.assertEqual(first.exit_id, "right")
        self.assertEqual(first, second)
        self.assertIs(router._reachable, reachable)

    def test_wide_exit_is_seeded_along_its_full_length(self):
        walkable = Polygon(((0, 0), (6, 0), (6, 4), (0, 4)))
        router = GridRouter(walkable, [], [Exit(1, (6, 0.5), (6, 3.5))])

        route = router.plan((0.5, 3.0))

        self.assertGreaterEqual(route.waypoints[-1][1], 2.5)

    def test_diagonal_cannot_cut_between_blocked_cardinal_cells(self):
        outer = Polygon(((0, 0), (1, 0), (1, 1), (0, 1)))
        right_block = box(0.375, 0.1, 0.625, 0.4)
        upper_block = box(0.1, 0.375, 0.4, 0.625)
        walkable = outer.difference(right_block.union(upper_block))
        router = GridRouter(walkable, [], [Exit(1, (1, 0.1), (1, 0.9))])

        with self.assertRaises(AgentRouteUnreachableError):
            router.plan((0.25, 0.25))

    def test_recommended_position_is_the_deterministic_safe_minimum(self):
        exit_ = Exit(1, (4, 1), (4, 3))
        router = GridRouter(box(0, 0, 4, 4), [], [exit_])
        start = (1.13, 1.12)
        other_agents = ((1.25, 1.25), (2.5, 2.5))
        exit_segments = (((4, 1), (4, 3)), ((0, 0.5), (0, 3.5)))
        eligible = []
        reachable = router.valid & np.isfinite(router.distance)
        for node in reachable.nonzero()[0]:
            node = int(node)
            position = router._point(node)
            if any(math.dist(position, agent) < 0.6 for agent in other_agents):
                continue
            if any(
                LineString(segment).distance(Point(position)) < 0.3
                for segment in exit_segments
            ):
                continue
            eligible.append(
                (
                    (
                        math.dist(start, position) ** 2,
                        float(router.distance[node]),
                        int(router.exit_label[node]),
                        node,
                    ),
                    position,
                )
            )
        expected = min(eligible)[1]

        with patch("route_planner._CONNECTOR_VISIBILITY_BATCH_SIZE", 3):
            first = router.recommended_position(
                start, other_agents, exit_segments, agent_spacing=0.6
            )
            second = router.recommended_position(
                start, other_agents, exit_segments, agent_spacing=0.6
            )

        self.assertEqual(first, expected)
        self.assertEqual(second, expected)
        self.assertEqual(router.plan(first).exit_id, 1)
        self.assertTrue(all(math.dist(first, agent) >= 0.6 for agent in other_agents))
        self.assertTrue(
            all(
                LineString(segment).distance(Point(first)) >= 0.3
                for segment in exit_segments
            )
        )

    def test_recommended_position_is_none_when_every_reachable_node_is_occupied(self):
        router = GridRouter(box(0, 0, 2, 2), [], [Exit(1, (2, 0.5), (2, 1.5))])
        reachable = router.valid & np.isfinite(router.distance)
        occupied = tuple(
            router._point(int(node)) for node in reachable.nonzero()[0]
        )

        recommendation = router.recommended_position(
            (0.5, 0.5), occupied, (((2, 0.5), (2, 1.5)),), agent_spacing=0.6
        )

        self.assertIsNone(recommendation)

    def test_recommended_position_tie_uses_numeric_exit_id_not_internal_label(self):
        router = GridRouter(
            box(0, 0, 4, 4),
            [],
            [Exit(10, (0, 1), (0, 3)), Exit(2, (4, 1), (4, 3))],
        )
        by_point = {
            router._point(int(node)): int(node)
            for node in (router.valid & np.isfinite(router.distance)).nonzero()[0]
        }
        left = by_point[(1.0, 2.0)]
        right = by_point[(3.0, 2.0)]
        self.assertEqual(
            (int(router.exit_label[left]), int(router.exit_label[right])), (0, 1)
        )
        self.assertAlmostEqual(router.distance[left], router.distance[right])
        router.valid[:] = False
        router.valid[[left, right]] = True

        recommendation = router.recommended_position(
            (2.0, 2.0),
            (),
            (((0, 1), (0, 3)), ((4, 1), (4, 3))),
            agent_spacing=0.6,
        )

        self.assertEqual(recommendation, (3.0, 2.0))
        self.assertEqual(router.plan(recommendation).exit_id, 2)

    def test_recommended_position_mixed_exit_ids_fall_back_to_internal_label(self):
        router = GridRouter(
            box(0, 0, 4, 4),
            [],
            [Exit(10, (0, 1), (0, 3)), Exit("2", (4, 1), (4, 3))],
        )
        by_point = {
            router._point(int(node)): int(node)
            for node in (router.valid & np.isfinite(router.distance)).nonzero()[0]
        }
        left = by_point[(1.0, 2.0)]
        right = by_point[(3.0, 2.0)]
        router.valid[:] = False
        router.valid[[left, right]] = True

        recommendation = router.recommended_position(
            (2.0, 2.0),
            (),
            (((0, 1), (0, 3)), ((4, 1), (4, 3))),
            agent_spacing=0.6,
        )

        self.assertEqual(recommendation, (1.0, 2.0))
        self.assertEqual(router.plan(recommendation).exit_id, 10)

    def test_agent_in_sub_grid_corridor_searches_past_nearest_128_nodes(self):
        drawing = {
            "outsideBoundary": [
                {"x": 0, "y": 0},
                {"x": 30, "y": 0},
                {"x": 30, "y": 10},
                {"x": 0, "y": 10},
            ],
            "walls": [],
            "pillars": [],
            "fabrics": [
                {
                    "startX": 5,
                    "startY": 4,
                    "endX": 25,
                    "endY": 4.7,
                    "rotation": 0,
                },
                {
                    "startX": 5,
                    "startY": 5.5,
                    "endX": 25,
                    "endY": 6.2,
                    "rotation": 0,
                },
            ],
        }
        physical = build_walkable_geometry(drawing)
        routing = build_routing_geometry(drawing, 0.3)
        router = GridRouter(
            routing,
            [],
            [Exit(1, (30, 3), (30, 7))],
            physical_walkable=physical,
        )
        start = (15.0212, 5.1965)

        reachable = router.valid & np.isfinite(router.distance)
        self.assertEqual(router._local_nodes(start, reachable), [])
        candidates = reachable.nonzero()[0]
        squared = (router._x[candidates] - start[0]) ** 2 + (
            router._y[candidates] - start[1]
        ) ** 2
        nearest = candidates[squared.argpartition(63)[:64]]
        self.assertFalse(
            any(router.can_connect(start, router._point(int(node))) for node in nearest)
        )
        expanded = candidates[squared.argpartition(127)[:128]]
        self.assertFalse(
            any(router.can_connect(start, router._point(int(node))) for node in expanded)
        )

        route = router.plan(start)

        self.assertEqual(route.exit_id, 1)
        self.assertTrue(router.can_connect(route.waypoints[0], route.waypoints[1]))
        self.assertEqual(router.plan(start), route)

    def test_mixed_boundary_and_interior_exits_are_both_routable(self):
        walkable = Polygon(((0, 0), (20, 0), (20, 10), (0, 10)))
        exits = [
            Exit(1, (20, 4), (20, 6)),
            Exit(2, (10, 4), (10, 6)),
        ]
        router = GridRouter(walkable, [], exits)

        first = router.plan((16.0, 5.0))
        second = router.plan((3.0, 5.0))

        self.assertEqual(first.exit_id, 1)
        self.assertEqual(second.exit_id, 2)
        self.assertEqual(router.plan((16.0, 5.0)), first)

    def test_long_expanded_connector_samples_hazard_at_grid_step_intervals(self):
        router = GridRouter(
            box(0, 0, 10, 2),
            [Hazard(2.5, 1.0, 0.2)],
            [Exit(1, (10, 0.5), (10, 1.5))],
        )
        start = (0.5, 1.0)
        end = (9.5, 1.0)

        three_sample_cost = router._edge_cost(start, end)
        expanded_cost = router._expanded_connector_cost(start, end)

        self.assertAlmostEqual(three_sample_cost, math.dist(start, end))
        self.assertGreater(expanded_cost, three_sample_cost)

    def test_expanded_connector_uses_total_cost_past_nearby_hazard(self):
        router = GridRouter(
            box(0, 0, 10, 4),
            [Hazard(2.5, 2.0, 1.0)],
            [Exit(1, (10, 1), (10, 3))],
        )
        start = (0.5, 2.0)
        reachable = router.valid & np.isfinite(router.distance)
        candidates = reachable.nonzero()[0]
        squared = (router._x[candidates] - start[0]) ** 2 + (
            router._y[candidates] - start[1]
        ) ** 2
        by_lower_bound = sorted(
            (int(node) for node in candidates),
            key=lambda node: (
                math.dist(start, router._point(node)) + float(router.distance[node]),
                node,
            ),
        )
        expected = min(
            (
                router._expanded_connector_cost(start, router._point(node))
                + float(router.distance[node]),
                int(router.exit_label[node]),
                node,
            )
            for node in by_lower_bound
        )

        self.assertGreater(by_lower_bound.index(expected[2]), 64)
        with patch("route_planner._CONNECTOR_VISIBILITY_BATCH_SIZE", 64):
            actual = router._expanded_connector(start, candidates, squared)

        self.assertEqual(actual, expected)


class RecoverySeedCacheTest(unittest.TestCase):
    @staticmethod
    def _rounded(point):
        return (round(point[0], 9), round(point[1], 9))

    def test_build_cost_field_computes_exit_seeds_once_per_exit(self):
        original = GridRouter._exit_seeds
        seen = []

        def counting_exit_seeds(self, exit_):
            seen.append(exit_)
            return original(self, exit_)

        exits = [Exit(1, (10, 1), (10, 7)), Exit(2, (0, 1), (0, 3))]
        with patch.object(GridRouter, "_exit_seeds", new=counting_exit_seeds):
            router = GridRouter(
                box(0, 0, 10, 8), [], exits, physical_walkable=box(0, 0, 10, 8)
            )

        self.assertEqual([exit_.id for exit_ in seen], [1, 2])
        self.assertEqual(list(router._exit_seed_cache), ["1", "2"])
        self.assertEqual(
            router._exit_seed_cache["1"],
            tuple(original(router, Exit(1, (10, 1), (10, 7)))),
        )

    def test_zero_seed_exit_is_cached_empty_and_has_no_neighbors(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7)), Exit(2, (0, 8.5), (2, 8.5))],
            physical_walkable=box(0, 0, 10, 8),
        )

        self.assertEqual(router._exit_seed_cache["2"], ())
        self.assertIsNone(router.recovery_seed_neighbors(2, (9.7, 4.0)))
        self.assertIsNone(router.recovery_seed_neighbors(99, (9.7, 4.0)))

    def test_seed_cache_and_label_map_are_frozen(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        self.assertIsInstance(router._exit_seed_cache, MappingProxyType)
        self.assertIsInstance(router._exit_labels, MappingProxyType)
        self.assertEqual(router._exit_labels, {"1": 0})
        with self.assertRaises(TypeError):
            router._exit_seed_cache["1"] = ()
        with self.assertRaises(TypeError):
            router._exit_labels["1"] = 1

    def test_recovery_neighbors_return_adjacent_seeds_along_exit(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        prev, current, nxt = router.recovery_seed_neighbors(1, (9.7, 4.0))

        self.assertEqual(self._rounded(prev[1]), (10.0, 3.75))
        self.assertEqual(self._rounded(prev[2]), (9.7, 3.75))
        self.assertEqual(self._rounded(current[1]), (10.0, 4.0))
        self.assertEqual(self._rounded(current[2]), (9.7, 4.0))
        self.assertEqual(self._rounded(nxt[1]), (10.0, 4.25))
        self.assertEqual(self._rounded(nxt[2]), (9.7, 4.25))

    def test_recovery_neighbors_no_wrap_at_first_and_last_seed(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        prev, current, nxt = router.recovery_seed_neighbors(1, (9.7, 1.5))
        self.assertIsNone(prev)
        self.assertEqual(self._rounded(current[2]), (9.7, 1.5))
        self.assertEqual(self._rounded(nxt[2]), (9.7, 1.75))

        prev, current, nxt = router.recovery_seed_neighbors(1, (9.7, 6.5))
        self.assertEqual(self._rounded(prev[2]), (9.7, 6.25))
        self.assertEqual(self._rounded(current[2]), (9.7, 6.5))
        self.assertIsNone(nxt)

    def test_recovery_neighbors_pick_nearest_approach_and_distinguish_target(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        _prev, current, _nxt = router.recovery_seed_neighbors(1, (9.7, 4.1))
        self.assertEqual(self._rounded(current[2]), (9.7, 4.0))
        self.assertEqual(self._rounded(current[1]), (10.0, 4.0))
        self.assertNotEqual(current[1], current[2])

        _prev, current, _nxt = router.recovery_seed_neighbors(1, (10.0, 4.0))
        self.assertEqual(self._rounded(current[2]), (9.7, 4.0))
        self.assertEqual(self._rounded(current[1]), (10.0, 4.0))

    def test_recovery_neighbors_leave_reachability_to_the_caller(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )
        original = router.can_connect

        def blocked_connector(start, end):
            if abs(end[1] - 4.25) < 1e-9:
                return False
            return original(start, end)

        router.can_connect = blocked_connector

        prev, current, nxt = router.recovery_seed_neighbors(1, (9.7, 4.0))

        self.assertEqual(self._rounded(prev[2]), (9.7, 3.75))
        self.assertEqual(self._rounded(current[2]), (9.7, 4.0))
        self.assertEqual(self._rounded(nxt[2]), (9.7, 4.25))

    def test_recovery_neighbors_reject_non_finite_approach(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(1, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        with self.assertRaisesRegex(ValueError, "finite"):
            router.recovery_seed_neighbors(1, (float("nan"), 4.0))
        with self.assertRaisesRegex(ValueError, "finite"):
            router.recovery_seed_neighbors(1, (9.7, float("inf")))

    def test_recovery_neighbors_resolve_exit_ids_by_id_key(self):
        router = GridRouter(
            box(0, 0, 10, 8),
            [],
            [Exit(10, (10, 1), (10, 7))],
            physical_walkable=box(0, 0, 10, 8),
        )

        for exit_id in (10, 10.0, "10"):
            _prev, current, _nxt = router.recovery_seed_neighbors(exit_id, (9.7, 4.0))
            self.assertEqual(self._rounded(current[2]), (9.7, 4.0))
        self.assertIsNone(router.recovery_seed_neighbors("9", (9.7, 4.0)))

    def test_single_seed_exit_has_insufficient_neighbors(self):
        original = GridRouter._exit_seeds

        def single_seed(self, exit_):
            return original(self, exit_)[:1]

        with patch.object(GridRouter, "_exit_seeds", new=single_seed):
            router = GridRouter(
                box(0, 0, 10, 8),
                [],
                [Exit(1, (10, 1), (10, 7))],
                physical_walkable=box(0, 0, 10, 8),
            )

        seed = router._exit_seed_cache["1"][0]
        self.assertIsNone(router.recovery_seed_neighbors(1, seed[2]))

    def test_id_key_canonicalizes_and_rejects_invalid_ids(self):
        self.assertEqual(route_planner._id_key(1), "1")
        self.assertEqual(route_planner._id_key(1.0), "1")
        self.assertEqual(route_planner._id_key("1"), "1")
        self.assertEqual(route_planner._id_key(2.5), "2.5")

        for invalid in (True, None, object()):
            with self.assertRaisesRegex(ValueError, "non-null numbers or strings"):
                route_planner._id_key(invalid)
class DeriveEquivalenceTest(unittest.TestCase):
    """`derive` must land on the same field a fresh GridRouter builds.

    layout_search evaluates one candidate per moved obstacle, so every
    difference here would silently change an evacuation route.
    """

    EXIT = Exit(1, (8, 2.5), (8, 3.5))
    HAZARDS = (Hazard(3.0, 4.5, 1.5),)
    OUTER = box(0, 0, 8, 6)
    LEFT = box(2.0, 1.0, 3.0, 2.0)
    RIGHT = box(5.0, 3.0, 6.0, 4.0)

    def _router(self, obstacles, hazards):
        walkable = self.OUTER.difference(
            box(0, 0, 0, 0).union(*obstacles) if obstacles else box(0, 0, 0, 0)
        )
        return GridRouter(walkable, hazards, [self.EXIT]), walkable

    def _assert_same_field(self, derived, fresh):
        # Everything that decides a route must match exactly. `distance` is the
        # one exception: both propagators skip a stale heap entry only when it
        # exceeds the settled cost by more than _EPSILON, so the field is defined
        # to 1e-9 and the incremental order can settle a node one ULP off. The
        # chain is unaffected, which `next_node` below pins down.
        np.testing.assert_allclose(
            derived.distance, fresh.distance, rtol=1e-12, atol=0.0, err_msg="distance"
        )
        for name in (
            "next_node",
            "exit_label",
            "terminal_x",
            "terminal_y",
            "approach_x",
            "approach_y",
            "valid",
        ):
            np.testing.assert_array_equal(
                getattr(derived, name), getattr(fresh, name), err_msg=name
            )
        np.testing.assert_array_equal(derived._neighbor_nodes, fresh._neighbor_nodes)
        for direction, values in fresh._grid_edges.items():
            np.testing.assert_array_equal(derived._grid_edges[direction], values)
        self.assertEqual(derived.seeded_exit_ids, fresh.seeded_exit_ids)

    @staticmethod
    def _bounds(*geometries):
        boxes = [geometry.bounds for geometry in geometries]
        return (
            min(item[0] for item in boxes),
            min(item[1] for item in boxes),
            max(item[2] for item in boxes),
            max(item[3] for item in boxes),
        )

    def _check(self, before_obstacles, after_obstacles, hazards, changed_bounds):
        base, _ = self._router(before_obstacles, hazards)
        fresh, after_area = self._router(after_obstacles, hazards)

        derived = base.derive(
            after_area, physical_walkable=after_area, changed_bounds=changed_bounds
        )

        self._assert_same_field(derived, fresh)
        return derived

    def test_removing_an_obstacle_matches_a_fresh_router(self):
        for hazards in ((), self.HAZARDS):
            with self.subTest(hazards=bool(hazards)):
                self._check([self.LEFT], [], hazards, self._bounds(self.LEFT))

    def test_adding_an_obstacle_matches_a_fresh_router(self):
        for hazards in ((), self.HAZARDS):
            with self.subTest(hazards=bool(hazards)):
                self._check([], [self.LEFT], hazards, self._bounds(self.LEFT))

    def test_moving_an_obstacle_matches_a_fresh_router(self):
        for hazards in ((), self.HAZARDS):
            with self.subTest(hazards=bool(hazards)):
                self._check(
                    [self.LEFT],
                    [self.RIGHT],
                    hazards,
                    self._bounds(self.LEFT, self.RIGHT),
                )

    def test_moving_an_obstacle_into_the_hazard_matches_a_fresh_router(self):
        inside_hazard = box(2.5, 4.0, 3.5, 5.0)
        self._check(
            [self.LEFT],
            [inside_hazard],
            self.HAZARDS,
            self._bounds(self.LEFT, inside_hazard),
        )

    def test_changed_bounds_does_not_change_the_result(self):
        for before, after in (
            ([self.LEFT], []),
            ([], [self.LEFT]),
            ([self.LEFT], [self.RIGHT]),
        ):
            for hazards in ((), self.HAZARDS):
                with self.subTest(after=bool(after), hazards=bool(hazards)):
                    base, _ = self._router(before, hazards)
                    _, after_area = self._router(after, hazards)
                    windowed = base.derive(
                        after_area,
                        physical_walkable=after_area,
                        changed_bounds=self._bounds(*(before + after)),
                    )
                    whole = base.derive(after_area, physical_walkable=after_area)

                    self._assert_same_field(windowed, whole)

    def test_derived_router_plans_the_same_routes(self):
        starts = [(0.6, 0.6), (4.0, 5.0), (7.0, 1.0), (2.5, 3.0)]
        base, _ = self._router([self.LEFT], self.HAZARDS)
        fresh, after_area = self._router([self.RIGHT], self.HAZARDS)

        derived = base.derive(
            after_area,
            physical_walkable=after_area,
            changed_bounds=self._bounds(self.LEFT, self.RIGHT),
        )

        for start in starts:
            with self.subTest(start=start):
                actual, expected = derived.plan(start), fresh.plan(start)
                self.assertEqual(actual.exit_id, expected.exit_id)
                self.assertEqual(actual.waypoints, expected.waypoints)
                self.assertEqual(actual.terminal_point, expected.terminal_point)
                self.assertAlmostEqual(actual.total_cost, expected.total_cost, places=9)


if __name__ == "__main__":
    unittest.main()
