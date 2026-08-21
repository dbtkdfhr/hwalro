from contextlib import redirect_stderr
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import numpy as np
from shapely.geometry import LineString, box

from runner import (
    AGENT_RADIUS_METERS,
    DT_SECONDS,
    WAYPOINT_REACHED_DISTANCE_METERS,
    AgentRouteState,
    HeatmapWriter,
    NoReachableSelectedExitRunnerError,
    RecoveryMutationRollbackRunnerError,
    RunnerError,
    SimulationContext,
    TimelineWriter,
    _activate_due_agents,
    _add_agent_with_spacing,
    _advance_context,
    _build_recovery_summary,
    _clamp_overspeed_moves,
    _create_context,
    _initialize_targets,
    _positive_float_from_environment,
    _rollback_invalid_moves,
    _sample_initial_response_times,
    _snapshot,
    _start_iteration,
    _termination_detail,
    _update_progress,
    _update_targets,
    _waypoint_reached,
    main,
    mid_route_recovery_scan,
    recovery_scan,
    run,
)
from route_planner import AgentRouteUnreachableError, Route


class TimelineWriterTest(unittest.TestCase):
    def test_chunks_use_schema_v1_and_twenty_frames(self):
        with tempfile.TemporaryDirectory() as directory:
            writer = TimelineWriter(Path(directory), total_agents=1)
            for second in range(21):
                writer.add(
                    {
                        "timeSeconds": second,
                        "agents": [{"agentId": 1, "x": float(second), "y": 0.0}],
                    }
                )
            writer.flush()

            first = json.loads((Path(directory) / "timeline" / "000000.json").read_text("utf-8"))
            second = json.loads((Path(directory) / "timeline" / "000001.json").read_text("utf-8"))
            self.assertEqual(first["schemaVersion"], 1)
            self.assertEqual(first["chunkSequence"], 0)
            self.assertEqual(first["startFrame"], 0)
            self.assertEqual(first["endFrame"], 19)
            self.assertEqual(len(first["frames"]), 20)
            self.assertEqual(second["chunkSequence"], 1)
            self.assertEqual(second["frames"][0]["frameIndex"], 20)
            self.assertEqual(writer.last_time_seconds, 20.0)

    def test_exit_event_uses_stable_agent_and_layout_exit_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            writer = TimelineWriter(Path(directory), total_agents=1)
            writer.add({"timeSeconds": 0, "agents": [{"agentId": 1, "x": 1.0, "y": 1.0}]})
            writer.add_exit_event(0.4, stable_id=1, exit_id=501)
            writer.add({"timeSeconds": 0.4, "agents": []})
            writer.flush()

            chunk = json.loads((Path(directory) / "timeline" / "000000.json").read_text("utf-8"))
            self.assertEqual(
                chunk["exitEvents"],
                [{"frameIndex": 1, "timeSeconds": 0.4, "agentId": 1, "exitId": 501}],
            )
            self.assertEqual(chunk["frames"][-1]["evacuatedCount"], 1)

    def test_finish_rejects_exit_events_without_a_matching_frame(self):
        with tempfile.TemporaryDirectory() as directory:
            writer = TimelineWriter(Path(directory), total_agents=1)
            writer.add({"timeSeconds": 0, "agents": [{"agentId": 1, "x": 1.0, "y": 1.0}]})
            writer.add_exit_event(20.0, stable_id=1, exit_id=501)

            with self.assertRaisesRegex(RuntimeError, "no matching timeline frame"):
                writer.finish()


class InitialResponseTimeTest(unittest.TestCase):
    def test_sampling_is_nonnegative_seed_deterministic_and_starts_at_zero(self):
        first = _sample_initial_response_times(np, 10_000, 2.0, -17)
        repeated = _sample_initial_response_times(np, 10_000, 2.0, -17)
        other_seed = _sample_initial_response_times(np, 10_000, 2.0, -18)

        np.testing.assert_array_equal(first, repeated)
        self.assertTrue(np.all(first >= 0.0))
        self.assertEqual(float(np.min(first)), 0.0)
        self.assertFalse(np.array_equal(first, other_seed))
        self.assertAlmostEqual(float(np.std(first)), 2.0, delta=0.1)

    def test_zero_std_dev_starts_every_agent_immediately(self):
        self.assertEqual(_sample_initial_response_times(np, 3, 0.0, 7), [0.0] * 3)
        np.testing.assert_array_equal(_sample_initial_response_times(np, 1, 2.0, 7), [0.0])
        self.assertEqual(_start_iteration(0.0), 1)
        self.assertEqual(_start_iteration(2.0), 201)
        self.assertEqual(_start_iteration(float("inf")), 60_001)

    def test_waiting_agent_is_not_removed_and_activates_at_scheduled_iteration(self):
        agent = BulkAgentAccessTest.BackingAgent(1, (0.0, 0.0))
        agent.model.desired_speed = 0.0

        class ActivationSimulation(BulkAgentAccessTest.Simulation):
            def agent(self, agent_id):
                return self._agents[agent_id]

        context = SimulationContext(
            ActivationSimulation([agent]),
            BulkAgentAccessTest.Router(),
            {1: BulkAgentAccessTest._state(1, (0.0, 0.0))},
            numpy=np,
        )
        context.start_iterations[:] = 3
        context.waiting[:] = True
        context.waiting_count = 1
        context.walking_speed = 1.25
        context.router.proximity_labels = [0]

        self.assertEqual(_initialize_targets(context), [])
        self.assertEqual(context.simulation.pending_removals, set())
        _activate_due_agents(context, 2)
        self.assertEqual(agent.model.desired_speed, 0.0)
        self.assertTrue(context.waiting[0])

        _activate_due_agents(context, 3)
        self.assertEqual(agent.model.desired_speed, 1.25)
        self.assertFalse(context.waiting[0])
        self.assertEqual(context.waiting_count, 0)


class HeatmapWriterTest(unittest.TestCase):
    def test_writes_sparse_grid_cells_matching_timeline_frames(self):
        with tempfile.TemporaryDirectory() as directory:
            writer = HeatmapWriter(Path(directory), (0.0, 0.0, 3.0, 2.0))
            writer.add(
                {
                    "frameIndex": 0,
                    "timeSeconds": 0.0,
                    "agents": [
                        {"agentId": 1, "x": 0.2, "y": 0.3},
                        {"agentId": 2, "x": 0.8, "y": 0.7},
                        {"agentId": 3, "x": 1.2, "y": 0.7},
                    ],
                }
            )
            writer.flush()

            chunk = json.loads((Path(directory) / "heatmap" / "000000.json").read_text("utf-8"))
            self.assertEqual(chunk["densityMethod"], "GRID_COUNT")
            self.assertEqual(chunk["grid"]["cellOrder"], "ROW_COLUMN_VALUE")
            self.assertEqual(chunk["frames"][0]["cells"], [[0, 0, 2.0], [0, 1, 1.0]])
            self.assertEqual(writer.max_density, 2.0)


class AgentRouteErrorContractTest(unittest.TestCase):
    @staticmethod
    def _payload():
        return {
            "model": {
                "modelProfile": "SFM_DEFAULT_V2",
                "routingProfile": "HAZARD_RADIAL_EXP_V3",
                "walkingSpeed": 1.2,
                "reactionTime": 0.5,
            },
            "maxSimulationTimeSeconds": 0.01,
            "frameIntervalSeconds": 0.01,
            "drawing": {
                "outsideBoundary": [
                    {"x": 0, "y": 0},
                    {"x": 4, "y": 0},
                    {"x": 4, "y": 4},
                    {"x": 0, "y": 4},
                ],
                "walls": [],
                "pillars": [],
                "fabrics": [],
                "exits": [
                    {"id": 1, "startX": 4, "startY": 1, "endX": 4, "endY": 3},
                    {"id": 2, "startX": 0, "startY": 1, "endX": 0, "endY": 3},
                ],
            },
            "agents": [{"x": 1, "y": 1}, {"x": 2, "y": 2}],
            "hazards": [],
            "selectedExitIds": [1],
        }

    def test_first_route_failure_writes_safe_typed_error_and_returns_three(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "private-input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(self._payload()), encoding="utf-8")
            stderr = io.StringIO()
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter.plan",
                    side_effect=[object(), AgentRouteUnreachableError("hidden position")],
                ) as plan,
                patch(
                    "route_planner.GridRouter.recommended_position",
                    return_value=(2.123456789, 1.987654321),
                ) as recommend,
                redirect_stderr(stderr),
            ):
                exit_code = main([str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 3)
            self.assertEqual(stderr.getvalue(), "runner error: AGENT_ROUTE_UNREACHABLE\n")
            self.assertNotIn(str(input_path), stderr.getvalue())
            self.assertNotIn("hidden position", stderr.getvalue())
            self.assertEqual(plan.call_count, 2)
            recommend.assert_called_once()
            self.assertEqual(len(recommend.call_args.kwargs["exit_segments"]), 2)
            self.assertEqual(len(recommend.call_args.kwargs["other_agents"]), 1)
            self.assertEqual(
                json.loads((output_dir / "error.json").read_text("utf-8")),
                {
                    "schemaVersion": 1,
                    "code": "AGENT_ROUTE_UNREACHABLE",
                    "agentId": 2,
                    "recommendedPosition": {"x": 2.123457, "y": 1.987654},
                },
            )

    def test_route_failure_writes_null_when_no_safe_recommendation_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(self._payload()), encoding="utf-8")
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter.plan",
                    side_effect=AgentRouteUnreachableError("hidden position"),
                ),
                patch("route_planner.GridRouter.recommended_position", return_value=None),
                redirect_stderr(io.StringIO()),
            ):
                exit_code = main([str(input_path), str(output_dir)])

            error = json.loads((output_dir / "error.json").read_text("utf-8"))
            self.assertEqual(exit_code, 3)
            self.assertEqual(error["agentId"], 1)
            self.assertIsNone(error["recommendedPosition"])

    def test_reports_lowest_original_index_when_group_order_is_reversed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            payload = self._payload()
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            def reversed_group(area, agents):
                return ((area, ((1, agents[1]), (0, agents[0]))),)

            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch("route_planner.split_agent_components", side_effect=reversed_group),
                patch(
                    "route_planner.GridRouter.plan",
                    side_effect=AgentRouteUnreachableError("hidden position"),
                ) as plan,
                patch(
                    "route_planner.GridRouter.recommended_position", return_value=(1.5, 1.5)
                ) as recommend,
                redirect_stderr(io.StringIO()),
            ):
                exit_code = main([str(input_path), str(output_dir)])

            error = json.loads((output_dir / "error.json").read_text("utf-8"))
            self.assertEqual(exit_code, 3)
            self.assertEqual(plan.call_count, 1)
            self.assertEqual(error["agentId"], 1)
            self.assertEqual(recommend.call_args.kwargs["start"], (1.0, 1.0))


class NoReachableSelectedExitContractTest(unittest.TestCase):
    @staticmethod
    def _payload():
        return {
            "model": {
                "modelProfile": "SFM_DEFAULT_V2",
                "routingProfile": "HAZARD_RADIAL_EXP_V3",
                "walkingSpeed": 1.2,
                "reactionTime": 0.5,
            },
            "maxSimulationTimeSeconds": 0.01,
            "frameIntervalSeconds": 0.01,
            "drawing": {
                "outsideBoundary": [
                    {"x": 0, "y": 0},
                    {"x": 6, "y": 0},
                    {"x": 6, "y": 4},
                    {"x": 0, "y": 4},
                ],
                "walls": [{"startX": 3, "startY": 0, "endX": 3, "endY": 4}],
                "pillars": [],
                "fabrics": [],
                "exits": [{"id": 1, "startX": 0, "startY": 1.5, "endX": 0, "endY": 2.5}],
            },
            "agents": [{"x": 0.5, "y": 2}, {"x": 5, "y": 2}],
            "hazards": [],
            "selectedExitIds": [1],
        }

    def test_component_without_exit_seed_fails_with_typed_detail_and_no_result(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(self._payload()), encoding="utf-8")
            stderr = io.StringIO()
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter",
                    side_effect=ValueError(
                        "no selected exit is reachable from this walkable component"
                    ),
                ),
                redirect_stderr(stderr),
            ):
                exit_code = main([str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 3)
            self.assertEqual(stderr.getvalue(), "runner error: NO_REACHABLE_SELECTED_EXIT\n")
            error = json.loads((output_dir / "error.json").read_text("utf-8"))
            self.assertEqual(
                error,
                {
                    "schemaVersion": 1,
                    "code": "NO_REACHABLE_SELECTED_EXIT",
                    "affectedAgentCount": 2,
                    "representativeAgentIds": [1, 2],
                    "componentCount": 2,
                    "selectedExitIds": [1],
                    "reason": "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT",
                },
            )
            self.assertFalse((output_dir / "result.json").exists())
            self.assertFalse((output_dir / "timeline").exists())
            self.assertFalse((output_dir / "heatmap").exists())

    def test_partial_component_failure_does_not_produce_partial_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(self._payload()), encoding="utf-8")
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter",
                    side_effect=[
                        object(),
                        ValueError("no selected exit is reachable from this walkable component"),
                    ],
                ),
                redirect_stderr(io.StringIO()),
            ):
                with self.assertRaises(NoReachableSelectedExitRunnerError):
                    run(input_path, output_dir)

            error = json.loads((output_dir / "error.json").read_text("utf-8"))
            self.assertEqual(error["code"], "NO_REACHABLE_SELECTED_EXIT")
            self.assertEqual(error["affectedAgentCount"], 1)
            self.assertEqual(error["representativeAgentIds"], [2])
            self.assertEqual(error["componentCount"], 1)
            self.assertFalse((output_dir / "result.json").exists())

    def test_unrelated_grid_router_failure_is_not_swallowed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(self._payload()), encoding="utf-8")
            stderr = io.StringIO()
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter",
                    side_effect=ValueError("drawing is too large for the 0.25m routing grid"),
                ),
                redirect_stderr(stderr),
            ):
                exit_code = main([str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 2)
            self.assertIn("drawing is too large", stderr.getvalue())
            self.assertFalse((output_dir / "error.json").exists())


class RoutingValidationModeTest(unittest.TestCase):
    def test_validation_accepts_slanted_boundary_exit_rounding_error(self):
        payload = AgentRouteErrorContractTest._payload()
        payload["drawing"] = {
            "outsideBoundary": [
                {"x": 47.4, "y": 56.5},
                {"x": 83.2, "y": 23.1},
                {"x": 125.7, "y": 52.6},
                {"x": 128.2, "y": 75.1},
                {"x": 71.8, "y": 76.5},
            ],
            "walls": [],
            "pillars": [],
            "fabrics": [
                {
                    "startX": 67.2,
                    "startY": 43.6,
                    "endX": 102.8,
                    "endY": 61.0,
                    "rotation": 0,
                }
            ],
            "exits": [
                {
                    "id": 17,
                    "startX": 71.8,
                    "startY": 76.5,
                    "endX": 92.3,
                    "endY": 76.0,
                }
            ],
        }
        payload["agents"] = [{"x": 64.1704, "y": 59.7878}]
        payload["selectedExitIds"] = [17]

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch("runner._create_context") as create_context,
            ):
                exit_code = main(["--validate-only", str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 0)
            create_context.assert_not_called()
            self.assertFalse((output_dir / "error.json").exists())

    def test_success_stops_after_initial_route_planning(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(
                json.dumps(AgentRouteErrorContractTest._payload()), encoding="utf-8"
            )
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch("route_planner.GridRouter.plan", return_value=object()) as plan,
                patch("runner._create_context") as create_context,
            ):
                exit_code = main(["--validate-only", str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 0)
            self.assertEqual(plan.call_count, 2)
            create_context.assert_not_called()
            self.assertFalse((output_dir / "result.json").exists())
            self.assertFalse((output_dir / "timeline").exists())
            self.assertFalse((output_dir / "heatmap").exists())

    def test_route_failure_keeps_existing_typed_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(
                json.dumps(AgentRouteErrorContractTest._payload()), encoding="utf-8"
            )
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter.plan",
                    side_effect=AgentRouteUnreachableError("hidden position"),
                ),
                patch("route_planner.GridRouter.recommended_position", return_value=None),
                redirect_stderr(io.StringIO()),
            ):
                exit_code = main(["--validate-only", str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 3)
            self.assertEqual(
                json.loads((output_dir / "error.json").read_text("utf-8"))["code"],
                "AGENT_ROUTE_UNREACHABLE",
            )

    def test_component_failure_keeps_existing_typed_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(
                json.dumps(NoReachableSelectedExitContractTest._payload()),
                encoding="utf-8",
            )
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.GridRouter",
                    side_effect=ValueError(
                        "no selected exit is reachable from this walkable component"
                    ),
                ),
                redirect_stderr(io.StringIO()),
            ):
                exit_code = main(["--validate-only", str(input_path), str(output_dir)])

            self.assertEqual(exit_code, 3)
            self.assertEqual(
                json.loads((output_dir / "error.json").read_text("utf-8"))["code"],
                "NO_REACHABLE_SELECTED_EXIT",
            )


class TerminationDetailTest(unittest.TestCase):
    @staticmethod
    def _context(states, positions, last_progress):
        context = SimulationContext(None, None, states, positions, numpy=np)
        for index, agent_id in enumerate(states):
            context.last_progress_iterations[index] = last_progress[index]
        return context

    @staticmethod
    def _state(stable_id, cursor, waypoint_count, exit_start=(20.0, 0.0), exit_end=(20.0, 1.0)):
        return AgentRouteState(
            stable_id=stable_id,
            exit_id=501,
            waypoints=tuple((float(index), 0.0) for index in range(waypoint_count)),
            terminal_point=(waypoint_count - 1.0, 0.0),
            exit_start=exit_start,
            exit_end=exit_end,
            cursor=cursor,
        )

    def test_global_stalled_when_every_remaining_agent_is_stagnant(self):
        states = {
            11: self._state(1, cursor=2, waypoint_count=3),
            12: self._state(2, cursor=0, waypoint_count=4),
        }
        positions = {11: (19.5, 0.0), 12: (1.0, 1.0)}
        context = self._context(states, positions, [100, 100])

        detail = _termination_detail([context], iteration=600)

        self.assertEqual(detail["globalReason"], "GLOBAL_STALLED")
        self.assertEqual(detail["remainingPeople"], 2)
        self.assertEqual(detail["reasonCounts"], {"EXIT_PORTAL_STUCK": 1, "ROUTE_FOLLOWING_STUCK": 1})
        self.assertEqual(detail["representativeAgents"], [1, 2])

    def test_partial_stalled_when_one_agent_still_progresses(self):
        states = {
            11: self._state(1, cursor=0, waypoint_count=4),
            12: self._state(2, cursor=0, waypoint_count=4),
        }
        positions = {11: (0.0, 0.0), 12: (0.0, 0.0)}
        context = self._context(states, positions, [450, 599])

        detail = _termination_detail([context], iteration=600)

        self.assertEqual(detail["globalReason"], "PARTIAL_STALLED")
        self.assertEqual(detail["reasonCounts"], {"ROUTE_FOLLOWING_STUCK": 2})

    def test_final_stage_agent_far_from_exit_is_route_following_stuck(self):
        states = {11: self._state(1, cursor=2, waypoint_count=3)}
        positions = {11: (10.0, 10.0)}
        context = self._context(states, positions, [100])

        detail = _termination_detail([context], iteration=600)

        self.assertEqual(detail["reasonCounts"], {"ROUTE_FOLLOWING_STUCK": 1})


class ProgressTrackerTest(unittest.TestCase):
    @staticmethod
    def _context(count):
        context = type("TrackerContext", (), {})()
        context.numpy = np
        context.positions = np.zeros((count, 2), dtype=float)
        context.progress_anchors = np.zeros((count, 2), dtype=float)
        context.last_progress_iterations = np.zeros(count, dtype=np.int64)
        return context

    def test_fifty_small_ticks_register_as_progress_without_losing_intermediate_ticks(self):
        context = self._context(1)
        slots = np.asarray([0])
        for tick in range(1, 51):
            context.positions[0, 0] += 0.006
            if tick % 50 == 0:
                _update_progress(context, slots, context.positions, tick)

        self.assertEqual(context.last_progress_iterations[0], 50)
        np.testing.assert_allclose(context.progress_anchors[0], [0.3, 0.0], atol=1e-9)

    def test_thirty_centimeter_ticks_update_on_the_first_sampling(self):
        context = self._context(1)
        for _ in range(30):
            context.positions[0, 0] += 0.01

        _update_progress(context, np.asarray([0]), context.positions, 30)

        self.assertEqual(context.last_progress_iterations[0], 30)
        np.testing.assert_allclose(context.progress_anchors[0], [0.3, 0.0], atol=1e-9)

    def test_subthreshold_movement_keeps_the_anchor_until_the_next_sampling(self):
        context = self._context(1)
        context.positions[0, 0] = 0.2
        _update_progress(context, np.asarray([0]), context.positions, 50)

        self.assertEqual(context.last_progress_iterations[0], 0)
        np.testing.assert_array_equal(context.progress_anchors[0], [0.0, 0.0])

        context.positions[0, 0] = 0.3
        _update_progress(context, np.asarray([0]), context.positions, 100)

        self.assertEqual(context.last_progress_iterations[0], 100)
        np.testing.assert_array_equal(context.progress_anchors[0], [0.3, 0.0])

    def test_round_trip_oscillation_is_not_progress(self):
        context = self._context(1)
        for tick in (50, 100, 150, 200):
            context.positions[0, 0] = 0.2
            _update_progress(context, np.asarray([0]), context.positions, tick)
        context.positions[0, 0] = 0.0
        _update_progress(context, np.asarray([0]), context.positions, 250)

        self.assertEqual(context.last_progress_iterations[0], 0)
        np.testing.assert_array_equal(context.progress_anchors[0], [0.0, 0.0])

    def test_agents_with_different_speeds_update_independently(self):
        context = self._context(3)
        context.positions[:, 0] = [0.3, 0.1, 0.5]

        _update_progress(context, np.asarray([0, 1, 2]), context.positions, 50)

        self.assertEqual(context.last_progress_iterations.tolist(), [50, 0, 50])
        np.testing.assert_array_equal(context.progress_anchors[0], [0.3, 0.0])
        np.testing.assert_array_equal(context.progress_anchors[1], [0.0, 0.0])
        np.testing.assert_array_equal(context.progress_anchors[2], [0.5, 0.0])

    def test_slots_outside_the_active_set_are_never_touched(self):
        context = self._context(2)
        context.positions[:, 0] = [0.3, 0.3]

        _update_progress(context, np.asarray([0]), context.positions, 50)

        self.assertEqual(context.last_progress_iterations.tolist(), [50, 0])
        np.testing.assert_array_equal(context.progress_anchors[1], [0.0, 0.0])


class OverspeedClampTest(unittest.TestCase):
    @staticmethod
    def _agent():
        class Model:
            velocity = (0.0, 0.0)

        class Agent:
            def __init__(self):
                self.position = (0.0, 0.0)
                self.model = Model()

        return Agent()

    @staticmethod
    def _context(count, max_agent_speed):
        context = type("ClampContext", (), {})()
        context.numpy = np
        context.max_agent_speed = max_agent_speed
        context.active = np.ones(count, dtype=bool)
        context.agent_ids = np.arange(count, dtype=np.int64)
        return context

    def test_overspeed_move_is_scaled_to_max_step(self):
        agent = self._agent()
        context = self._context(1, max_agent_speed=10.0)
        previous = np.asarray([[0.0, 0.0]])
        current = np.asarray([[1.0, 0.0]])
        crossed = np.asarray([False])
        max_step = 10.0 * DT_SECONDS

        _clamp_overspeed_moves(context, previous, {0: agent}, current, crossed)

        self.assertAlmostEqual(float(current[0, 0]), max_step, places=9)
        self.assertAlmostEqual(float(current[0, 1]), 0.0, places=9)
        self.assertAlmostEqual(agent.position[0], max_step, places=9)
        self.assertAlmostEqual(agent.model.velocity[0], 10.0, places=9)
        self.assertAlmostEqual(agent.model.velocity[1], 0.0, places=9)

    def test_within_limit_move_is_untouched(self):
        agent = self._agent()
        context = self._context(1, max_agent_speed=10.0)
        previous = np.asarray([[0.0, 0.0]])
        current = np.asarray([[0.05, 0.0]])
        crossed = np.asarray([False])

        _clamp_overspeed_moves(context, previous, {0: agent}, current, crossed)

        self.assertAlmostEqual(float(current[0, 0]), 0.05, places=9)
        self.assertEqual(agent.position, (0.0, 0.0))

    def test_crossed_agents_are_excluded(self):
        agent = self._agent()
        context = self._context(1, max_agent_speed=10.0)
        previous = np.asarray([[0.0, 0.0]])
        current = np.asarray([[1.0, 0.0]])
        crossed = np.asarray([True])

        _clamp_overspeed_moves(context, previous, {0: agent}, current, crossed)

        self.assertAlmostEqual(float(current[0, 0]), 1.0, places=9)
        self.assertEqual(agent.position, (0.0, 0.0))

    def test_inactive_agents_are_excluded(self):
        agent = self._agent()
        context = self._context(1, max_agent_speed=10.0)
        context.active[0] = False
        previous = np.asarray([[0.0, 0.0]])
        current = np.asarray([[1.0, 0.0]])
        crossed = np.asarray([False])

        _clamp_overspeed_moves(context, previous, {0: agent}, current, crossed)

        self.assertAlmostEqual(float(current[0, 0]), 1.0, places=9)
        self.assertEqual(agent.position, (0.0, 0.0))


class SfmParameterForwardingTest(unittest.TestCase):
    class FakeJps:
        def __init__(self):
            self.sfm_kwargs = None
            self.agent_params = []

        def SocialForceModel(self, **kwargs):
            self.sfm_kwargs = kwargs
            return object()

        def SocialForceModelAgentParameters(self, **kwargs):
            self.agent_params.append(kwargs)
            return object()

        def JourneyDescription(self, _stages):
            return object()

        def Simulation(self, **kwargs):
            return SfmParameterForwardingTest.FakeSimulation()

    class FakeSimulation:
        def add_direct_steering_stage(self):
            return 0

        def add_journey(self, _description):
            return 0

    def test_add_agent_with_spacing_forwards_softened_parameters(self):
        jps = self.FakeJps()
        simulation = SimpleNamespace(add_agent=lambda _params: 42)
        physical_component = SimpleNamespace(covers=lambda _point: True)
        shapely = SimpleNamespace(Point=lambda candidate: candidate)

        agent_id = _add_agent_with_spacing(
            simulation,
            jps,
            np,
            shapely,
            physical_component,
            [],
            0,
            0,
            (0.0, 0.0),
            (1.0, 0.0),
            3.0,
            0.5,
            agent_scale=1000.0,
            force_distance=0.2,
        )

        self.assertEqual(agent_id, 42)
        (params,) = jps.agent_params
        self.assertEqual(params["agent_scale"], 1000.0)
        self.assertEqual(params["force_distance"], 0.2)
        self.assertEqual(params["radius"], AGENT_RADIUS_METERS)

    def test_create_context_forwards_body_force_friction_and_max_speed(self):
        jps = self.FakeJps()
        route = SimpleNamespace(
            exit_id=501,
            waypoints=((0.0, 0.0), (1.0, 0.0)),
            terminal_point=(1.0, 0.0),
            exit_start=(1.0, -0.5),
            exit_end=(1.0, 0.5),
        )
        indexed_agents = [(0, (0.0, 0.0))]
        with patch("runner._add_agent_with_spacing", return_value=100) as add_spacing:
            context = _create_context(
                jps,
                np,
                None,
                object(),
                object(),
                indexed_agents,
                [route],
                walking_speed=3.0,
                initial_response_times=[0.0],
                sfm_agent_scale=800.0,
                sfm_force_distance=0.25,
                sfm_body_force=50000.0,
                sfm_friction=90000.0,
                max_agent_speed=9.5,
            )

        self.assertEqual(jps.sfm_kwargs, {"body_force": 50000.0, "friction": 90000.0})
        self.assertEqual(context.max_agent_speed, 9.5)
        self.assertEqual(add_spacing.call_args.args[-2:], (800.0, 0.25))


class PositiveFloatEnvironmentTest(unittest.TestCase):
    def test_default_when_unset(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(_positive_float_from_environment("HWALRO_X", 7.5), 7.5)

    def test_parses_numeric_value(self):
        with patch.dict(os.environ, {"HWALRO_X": "3.25"}, clear=True):
            self.assertEqual(_positive_float_from_environment("HWALRO_X", 7.5), 3.25)

    def test_non_numeric_value_raises(self):
        with patch.dict(os.environ, {"HWALRO_X": "abc"}, clear=True):
            with self.assertRaises(RunnerError):
                _positive_float_from_environment("HWALRO_X", 7.5)

    def test_non_positive_value_raises(self):
        with patch.dict(os.environ, {"HWALRO_X": "0"}, clear=True):
            with self.assertRaises(RunnerError):
                _positive_float_from_environment("HWALRO_X", 7.5)


class WaypointProgressTest(unittest.TestCase):
    def setUp(self):
        self.state = AgentRouteState(
            stable_id=1,
            exit_id=501,
            waypoints=((0, 0), (1, 0), (2, 1)),
            terminal_point=(3, 0.5),
            exit_start=(3, 0),
            exit_end=(3, 1),
            cursor=1,
        )

    def test_reached_distance_covers_agent_radius_and_grid_diagonal(self):
        self.assertAlmostEqual(WAYPOINT_REACHED_DISTANCE_METERS, 0.25 * 2**0.5)
        self.assertTrue(_waypoint_reached((0.7, 0), self.state, lambda _a, _b: False))

    def test_pass_gate_advances_only_with_safe_immediate_connector(self):
        self.assertTrue(_waypoint_reached((1.1, 0.5), self.state, lambda _a, _b: True))
        self.assertFalse(_waypoint_reached((1.1, 0.5), self.state, lambda _a, _b: False))
        self.assertFalse(_waypoint_reached((0.5, 1), self.state, lambda _a, _b: True))

    def test_pass_gate_rejects_connector_across_routing_obstacle(self):
        routing = box(0, -1, 3, 2).difference(
            LineString(((1.5, -1), (1.5, 2))).buffer(0.3, cap_style="flat")
        )

        self.assertFalse(
            _waypoint_reached(
                (1.1, 0.5),
                self.state,
                lambda start, end: routing.covers(LineString((start, end))),
            )
        )

    def test_agents_sharing_waypoint_progress_independently(self):
        other = AgentRouteState(
            stable_id=2,
            exit_id=501,
            waypoints=self.state.waypoints,
            terminal_point=self.state.terminal_point,
            exit_start=self.state.exit_start,
            exit_end=self.state.exit_end,
            cursor=1,
        )

        self.assertTrue(_waypoint_reached((1.1, 0.2), self.state, lambda _a, _b: True))
        self.assertTrue(_waypoint_reached((1.2, -0.2), other, lambda _a, _b: True))


class BulkAgentAccessTest(unittest.TestCase):
    class Model:
        def __init__(self):
            self.velocity = (0.0, 0.0)

    class BackingAgent:
        def __init__(self, agent_id, position):
            self.id = agent_id
            self.position = position
            self.model = BulkAgentAccessTest.Model()
            self.position_reads = 0
            self.target = None
            self.target_writes = 0

    class AgentHandle:
        def __init__(self, simulation, backing):
            self._simulation = simulation
            self._backing = backing
            self._generation = simulation.generation

        def _check_generation(self):
            if self._generation != self._simulation.generation:
                raise AssertionError("agent handle was reused across an iteration")

        @property
        def id(self):
            self._check_generation()
            return self._backing.id

        @property
        def position(self):
            self._check_generation()
            self._backing.position_reads += 1
            return self._backing.position

        @position.setter
        def position(self, value):
            self._check_generation()
            self._backing.position = value

        @property
        def model(self):
            self._check_generation()
            return self._backing.model

        @property
        def target(self):
            self._check_generation()
            return self._backing.target

        @target.setter
        def target(self, value):
            self._check_generation()
            self._backing.target = value
            self._backing.target_writes += 1

    class Simulation:
        def __init__(self, agents):
            self._agents = {agent.id: agent for agent in agents}
            self.generation = 0
            self.pending_removals = set()
            self.next_positions = {}
            self.traversals = 0

        def agent(self, _agent_id):
            raise AssertionError("single-agent lookup must not be used")

        def agents(self):
            self.traversals += 1
            return [
                BulkAgentAccessTest.AgentHandle(self, agent)
                for agent in self._agents.values()
            ]

        def iterate(self):
            self.generation += 1
            for agent_id in self.pending_removals:
                self._agents.pop(agent_id, None)
            self.pending_removals.clear()
            for agent_id, position in self.next_positions.items():
                self._agents[agent_id].position = position
            self.next_positions.clear()

        def mark_agent_for_removal(self, agent_id):
            self.pending_removals.add(agent_id)
            return True

    class Router:
        def __init__(self):
            self.exits = [SimpleNamespace(id=501), SimpleNamespace(id=502)]
            self.proximity_labels = None

        def valid_moves(self, starts, _ends):
            return [True] * len(starts)

        def can_connect(self, _start, _end):
            return True

        def can_connect_many(self, starts, ends):
            return [
                self.can_connect(start, end)
                for start, end in zip(starts, ends, strict=True)
            ]

        def can_reach_exit(self, _start, _end):
            return True

        def can_reach_exits(self, starts, ends):
            return [
                self.can_reach_exit(start, end) for start, end in zip(starts, ends, strict=True)
            ]

        def crossed_exit(self, _start, _end, _exit_start, _exit_end):
            return False

        def crossed_exits(self, starts, ends, exit_starts, exit_ends):
            return [
                self.crossed_exit(start, end, exit_start, exit_end)
                for start, end, exit_start, exit_end in zip(
                    starts, ends, exit_starts, exit_ends, strict=True
                )
            ]

        def reached_exit(self, _position, _exit_start, _exit_end):
            return False

        def reached_exits(self, positions, exit_starts, exit_ends):
            return [
                self.reached_exit(position, exit_start, exit_end)
                for position, exit_start, exit_end in zip(
                    positions, exit_starts, exit_ends, strict=True
                )
            ]

        def reached_selected_exit_labels(self, positions):
            if self.proximity_labels is None:
                return np.full(len(positions), -1, dtype=np.int32)
            return np.asarray(self.proximity_labels, dtype=np.int32)

        def clamp_to_walkable(self, point):
            return point

    @staticmethod
    def _state(stable_id, waypoint):
        return AgentRouteState(
            stable_id=stable_id,
            exit_id=501,
            waypoints=(waypoint,),
            terminal_point=waypoint,
            exit_start=(20.0, 0.0),
            exit_end=(20.0, 1.0),
        )

    def test_uses_fresh_bulk_traversals_and_avoids_redundant_reads_and_targets(self):
        first = self.BackingAgent(1, (-1.0, 0.0))
        second = self.BackingAgent(2, (5.0, 0.0))
        simulation = self.Simulation([first, second])
        context = SimulationContext(
            simulation,
            self.Router(),
            {1: self._state(1, (0.0, 0.0)), 2: self._state(2, (10.0, 0.0))},
            numpy=np,
        )
        position_buffers = {id(context.positions), id(context.next_positions)}
        np.testing.assert_array_equal(context.agent_ids, [1, 2])
        np.testing.assert_array_equal(context.stable_ids, [1, 2])
        np.testing.assert_array_equal(context.waypoint_counts, [1, 1])
        np.testing.assert_array_equal(context.waypoint_offsets, [0, 1, 2])
        self.assertEqual(context.slot_by_id, {1: 0, 2: 1})

        self.assertEqual(_initialize_targets(context), [])
        self.assertEqual((first.target_writes, second.target_writes), (1, 1))

        simulation.next_positions[1] = (0.0, 0.0)
        self.assertEqual(_advance_context(context, 1), [1])
        self.assertEqual(context.active.tolist(), [False, True])
        self.assertEqual({id(context.positions), id(context.next_positions)}, position_buffers)
        context.states.pop(1)
        self.assertEqual((first.target_writes, second.target_writes), (1, 1))

        frame = _snapshot([context], 0.01)
        self.assertEqual(frame["agents"], [{"agentId": 2, "x": 5.0, "y": 0.0}])

        self.assertEqual(_advance_context(context, 2), [])
        self.assertEqual({id(context.positions), id(context.next_positions)}, position_buffers)
        self.assertEqual((first.position_reads, second.position_reads), (2, 3))
        self.assertEqual((first.target_writes, second.target_writes), (1, 1))
        self.assertEqual(simulation.traversals, 3)

    def test_sets_a_new_target_once_when_the_waypoint_changes(self):
        agent = self.BackingAgent(1, (-1.0, 0.0))
        simulation = self.Simulation([agent])
        state = AgentRouteState(
            stable_id=1,
            exit_id=501,
            waypoints=((0.0, 0.0), (10.0, 0.0)),
            terminal_point=(10.0, 0.0),
            exit_start=(20.0, 0.0),
            exit_end=(20.0, 1.0),
        )
        context = SimulationContext(simulation, self.Router(), {1: state}, numpy=np)

        self.assertEqual(_initialize_targets(context), [])
        simulation.next_positions[1] = (0.0, 0.0)
        self.assertEqual(_advance_context(context, 1), [])

        self.assertEqual(state.cursor, 1)
        self.assertEqual(agent.target, (10.0, 0.0))
        self.assertEqual(agent.target_writes, 2)

    def test_snapshot_skips_bulk_traversal_for_an_empty_context(self):
        simulation = self.Simulation([self.BackingAgent(1, (1.0, 1.0))])
        context = SimulationContext(simulation, self.Router(), {}, numpy=np)

        frame = _snapshot([context], 0.01)

        self.assertEqual(frame["agents"], [])
        self.assertEqual(simulation.traversals, 0)

    def test_initializes_empty_context_with_empty_numpy_masks(self):
        simulation = self.Simulation([])
        context = SimulationContext(simulation, self.Router(), {}, numpy=np)

        self.assertEqual(_initialize_targets(context), [])

        self.assertEqual(context.positions.shape, (0, 2))
        self.assertEqual(context.active_count, 0)
        self.assertEqual(simulation.traversals, 1)

    def test_snapshot_uses_cached_positions_and_stable_agent_order(self):
        first = self.BackingAgent(1, (9.0, 9.0))
        second = self.BackingAgent(2, (8.0, 8.0))
        simulation = self.Simulation([first, second])
        context = SimulationContext(
            simulation,
            self.Router(),
            {1: self._state(2, (0.0, 0.0)), 2: self._state(1, (0.0, 0.0))},
            {1: (2.0, 2.0), 2: (1.0, 1.0)},
            numpy=np,
        )

        frame = _snapshot([context], 0.01)

        self.assertEqual(
            frame["agents"],
            [
                {"agentId": 1, "x": 1.0, "y": 1.0},
                {"agentId": 2, "x": 2.0, "y": 2.0},
            ],
        )
        self.assertEqual(simulation.traversals, 0)
        self.assertEqual((first.position_reads, second.position_reads), (0, 0))

    def test_missing_active_agent_is_still_rejected(self):
        simulation = self.Simulation([self.BackingAgent(1, (1.0, 1.0))])
        context = SimulationContext(
            simulation,
            self.Router(),
            {1: self._state(1, (10.0, 0.0)), 2: self._state(2, (10.0, 0.0))},
            numpy=np,
        )

        with self.assertRaisesRegex(RunnerError, r"active JuPedSim agents are missing: \[2\]"):
            _initialize_targets(context)

    def test_batches_multi_waypoint_progress_and_cleans_evacuated_position(self):
        agent = self.BackingAgent(1, (-1.0, 0.0))
        simulation = self.Simulation([agent])
        state = AgentRouteState(
            stable_id=1,
            exit_id=501,
            waypoints=((0.0, 0.0), (1.0, 0.0), (2.0, 0.0), (3.0, 0.0)),
            terminal_point=(3.0, 0.0),
            exit_start=(3.0, -1.0),
            exit_end=(3.0, 1.0),
            cursor=1,
        )
        context = SimulationContext(simulation, self.Router(), {1: state}, numpy=np)
        self.assertEqual(_initialize_targets(context), [])

        simulation.next_positions[1] = (2.1, 0.0)
        self.assertEqual(_advance_context(context, 1), [])
        self.assertEqual(state.cursor, 3)
        self.assertEqual(context.cursors.tolist(), [3])
        self.assertEqual(agent.target, (3.0, 0.0))

        simulation.next_positions[1] = (3.0, 0.0)
        self.assertEqual(_advance_context(context, 2), [1])
        self.assertEqual(context.active.tolist(), [False])
        self.assertEqual(context.active_count, 0)
        self.assertEqual(simulation.pending_removals, {1})

    def test_removes_mid_route_agent_and_records_actual_reached_exit(self):
        agent = self.BackingAgent(1, (5.0, 0.0))
        simulation = self.Simulation([agent])
        router = self.Router()
        router.proximity_labels = [1]
        state = AgentRouteState(
            stable_id=1,
            exit_id=501,
            waypoints=((0.0, 0.0), (10.0, 0.0)),
            terminal_point=(10.0, 0.0),
            exit_start=(20.0, 0.0),
            exit_end=(20.0, 1.0),
        )
        context = SimulationContext(simulation, router, {1: state}, numpy=np)

        self.assertEqual(_initialize_targets(context), [1])

        self.assertEqual(state.cursor, 0)
        self.assertEqual(state.exit_id, 502)
        self.assertEqual(context.active.tolist(), [False])
        self.assertEqual(simulation.pending_removals, {1})

    def test_removal_refusal_preserves_position_without_rewriting_target(self):
        class RefusingSimulation(self.Simulation):
            def mark_agent_for_removal(self, _agent_id):
                return False

        agent = self.BackingAgent(1, (0.0, 0.0))
        simulation = RefusingSimulation([agent])
        router = self.Router()
        router.proximity_labels = [1]
        state = self._state(1, (0.0, 0.0))
        context = SimulationContext(
            simulation,
            router,
            {1: state},
            numpy=np,
        )

        self.assertEqual(_initialize_targets(context), [])

        np.testing.assert_array_equal(context.positions, [[0.0, 0.0]])
        self.assertEqual(context.active.tolist(), [True])
        self.assertEqual(state.exit_id, 501)
        self.assertEqual(agent.target_writes, 0)

    def test_exit_crossing_is_detected_before_invalid_move_rollback(self):
        class RejectingRouter(self.Router):
            def valid_moves(self, starts, _ends):
                return [False] * len(starts)

            def can_connect(self, _start, _end):
                return False

            def can_reach_exit(self, _start, _end):
                return False

            def crossed_exit(self, start, end, _exit_start, _exit_end):
                return start[0] < 0.0 <= end[0]

        agent = self.BackingAgent(1, (-1.0, 0.0))
        simulation = self.Simulation([agent])
        simulation.next_positions[1] = (1.0, 0.0)
        context = SimulationContext(
            simulation,
            RejectingRouter(),
            {1: self._state(1, (10.0, 0.0))},
            {1: (-1.0, 0.0)},
            numpy=np,
        )

        self.assertEqual(_advance_context(context, 1), [1])

        self.assertEqual(agent.position, (1.0, 0.0))
        self.assertEqual(simulation.pending_removals, {1})


class MovementGuardTest(unittest.TestCase):
    class Model:
        velocity = (3.0, 0.0)

    class Agent:
        position = (2.0, 1.0)
        model = None

        def __init__(self):
            self.model = MovementGuardTest.Model()

    class Simulation:
        def __init__(self, agent):
            self._agent = agent

        def agent(self, _agent_id):
            return self._agent

    @staticmethod
    def _state():
        return AgentRouteState(
            stable_id=1,
            exit_id=501,
            waypoints=((10.0, 1.0),),
            terminal_point=(10.0, 1.0),
            exit_start=(20.0, 0.0),
            exit_end=(20.0, 1.0),
        )

    class Router:
        def __init__(self, valid):
            self.valid = valid

        def contains(self, _point):
            return self.valid

        def can_connect(self, _start, _end):
            return self.valid

        def valid_moves(self, starts, _ends):
            return [self.valid] * len(starts)

        def clamp_to_walkable(self, point):
            return point

    def test_invalid_move_rolls_back_position_and_zeroes_velocity(self):
        agent = self.Agent()
        context = SimulationContext(
            self.Simulation(agent),
            self.Router(False),
            {1: self._state()},
            {1: (1.0, 1.0)},
            numpy=np,
        )
        current = np.asarray([[2.0, 1.0]])

        _rollback_invalid_moves(
            context,
            context.positions,
            {1: agent},
            current,
            np.asarray([False]),
            7,
        )

        self.assertEqual(agent.position, (1.0, 1.0))
        self.assertEqual(agent.model.velocity, (0.0, 0.0))
        np.testing.assert_array_equal(current, [[1.0, 1.0]])
        self.assertEqual(context.last_invalid_iteration.tolist(), [7])

    def test_valid_move_is_not_changed(self):
        agent = self.Agent()
        context = SimulationContext(
            self.Simulation(agent),
            self.Router(True),
            {1: self._state()},
            {1: (1.0, 1.0)},
            numpy=np,
        )
        current = np.asarray([[2.0, 1.0]])

        _rollback_invalid_moves(
            context,
            context.positions,
            {1: agent},
            current,
            np.asarray([False]),
            7,
        )

        self.assertEqual(agent.position, (2.0, 1.0))
        self.assertEqual(agent.model.velocity, (3.0, 0.0))
        np.testing.assert_array_equal(current, [[2.0, 1.0]])
        self.assertEqual(context.last_invalid_iteration.tolist(), [-1])

    def test_crossed_agents_are_excluded_from_rollback(self):
        agent = self.Agent()
        context = SimulationContext(
            self.Simulation(agent),
            self.Router(False),
            {1: self._state()},
            {1: (1.0, 1.0)},
            numpy=np,
        )
        current = np.asarray([[2.0, 1.0]])

        _rollback_invalid_moves(
            context,
            context.positions,
            {1: agent},
            current,
            np.asarray([True]),
            7,
        )

        self.assertEqual(agent.position, (2.0, 1.0))
        self.assertEqual(agent.model.velocity, (3.0, 0.0))
        np.testing.assert_array_equal(current, [[2.0, 1.0]])


class RecoveryScanTest(unittest.TestCase):
    SEEDS = (
        (11, (10.0, 3.75), (9.7, 3.75)),
        (12, (10.0, 4.0), (9.7, 4.0)),
        (13, (10.0, 4.25), (9.7, 4.25)),
    )

    class Exit:
        def __init__(self, exit_id):
            self.id = exit_id

    class Model:
        def __init__(self):
            self.velocity = (0.0, 0.0)

    class Agent:
        def __init__(self, target=None, explode=False, explode_occurrence=1):
            self._target = target
            self.explode = explode
            self.explode_occurrence = explode_occurrence
            self._target_sets = 0
            self.position = (0.0, 0.0)
            self.model = RecoveryScanTest.Model()

        @property
        def target(self):
            return self._target

        @target.setter
        def target(self, value):
            self._target_sets += 1
            if self.explode and self._target_sets == self.explode_occurrence:
                raise RuntimeError("secret failure detail must not leak into telemetry")
            self._target = value

    class Simulation:
        def __init__(self, agents):
            self.agents_by_id = agents

        def agent(self, agent_id):
            return self.agents_by_id[agent_id]

        def mark_agent_for_removal(self, _agent_id):
            return False

    class Router:
        def __init__(self, label_by_id, exits, neighbors=None):
            self._exit_labels = label_by_id
            self.exits = exits
            self.neighbors_fn = neighbors or (lambda _exit_id, _approach: None)
            self.can_connect_fn = lambda _start, _end: True
            self.can_reach_fn = lambda _start, _end: True
            self.reached_fn = lambda _position, _start, _end: False

        def recovery_seed_neighbors(self, exit_id, current_approach):
            return self.neighbors_fn(exit_id, current_approach)

        def can_connect(self, start, end):
            return self.can_connect_fn(start, end)

        def can_reach_exit(self, start, end):
            return self.can_reach_fn(start, end)

        def can_reach_exits(self, starts, ends):
            return [
                self.can_reach_fn(start, end) for start, end in zip(starts, ends, strict=True)
            ]

        def reached_exits(self, positions, exit_starts, exit_ends):
            return [
                self.reached_fn(position, exit_start, exit_end)
                for position, exit_start, exit_end in zip(
                    positions, exit_starts, exit_ends, strict=True
                )
            ]

        def reached_selected_exit_labels(self, positions):
            return np.full(len(positions), -1, dtype=np.int32)

        def valid_moves(self, starts, _ends):
            return [True] * len(starts)

        def clamp_to_walkable(self, point):
            return point

    @staticmethod
    def _state(stable_id, approach=(9.7, 4.0), exit_id=501, terminal=(10.0, 4.0)):
        return AgentRouteState(
            stable_id=stable_id,
            exit_id=exit_id,
            waypoints=(approach,),
            terminal_point=terminal,
            exit_start=(10.0, 1.0),
            exit_end=(10.0, 7.0),
            cursor=0,
        )

    def _context(
        self,
        states,
        positions,
        label_by_id=None,
        exits=None,
        neighbors=None,
        agents=None,
        context_index=0,
    ):
        if label_by_id is None:
            label_by_id = {"501": 0}
        if exits is None:
            exits = [self.Exit(501)]
        if agents is None:
            agents = {agent_id: self.Agent() for agent_id in states}
        simulation = self.Simulation(agents)
        router = self.Router(label_by_id, exits, neighbors)
        context = SimulationContext(
            simulation,
            router,
            states,
            positions,
            numpy=np,
            context_index=context_index,
        )
        for slot, state in enumerate(states.values()):
            context.cursors[slot] = len(state.waypoints) - 1
            context.stationary_streak[slot] = 500
            context.last_invalid_iteration[slot] = -1
            context.readiness_any[slot] = False
        return context

    def test_scan_applies_mutation_and_reports_recovered_event(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {1: self.Agent((9.7, 4.0)), 2: self.Agent((9.7, 4.0)), 3: self.Agent((9.7, 4.0))}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )

        applied = recovery_scan(context, 550)

        self.assertTrue(applied)
        self.assertEqual(context.recovered.tolist(), [True, True, True])
        self.assertEqual(context.recovery_counters, {
            "scans": 1,
            "eligible_groups": 1,
            "skipped_groups": 0,
            "recovered_groups": 1,
        })
        self.assertEqual(agents[1].target, (9.7, 3.75))
        self.assertEqual(agents[2].target, (9.7, 4.0))
        self.assertEqual(agents[3].target, (9.7, 4.25))
        self.assertEqual(states[1].terminal_point, (10.0, 3.75))
        self.assertEqual(states[1].waypoints, ((9.7, 3.75),))
        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERED")
        self.assertEqual(event["iteration"], 550)
        self.assertEqual(event["contextIndex"], 0)
        self.assertEqual(event["exitId"], 501)
        self.assertEqual(event["exitLabel"], 0)
        self.assertEqual(event["stableIds"], [1, 2, 3])
        self.assertEqual(event["oldTargets"], [[9.7, 4.0], [9.7, 4.0], [9.7, 4.0]])
        self.assertEqual(
            event["newTargets"], [[10.0, 3.75], [10.0, 4.0], [10.0, 4.25]]
        )
        self.assertEqual(
            event["newApproaches"], [[9.7, 3.75], [9.7, 4.0], [9.7, 4.25]]
        )
        self.assertEqual(event["seedNodeIds"], [11, 12, 13])
        summary = _build_recovery_summary([context])
        self.assertEqual(summary["schemaVersion"], 1)
        self.assertEqual(summary["recoveredGroupCount"], 1)
        self.assertEqual(summary["recoveredAgentCount"], 3)
        self.assertEqual(summary["recoveryTimeSeconds"], 5.5)
        self.assertEqual(summary["recoveredExitLabels"], [0])
        self.assertEqual(summary["recoveredExitIds"], [501])
        self.assertEqual(summary["attemptedGroupSignatures"], 1)
        self.assertEqual(summary["events"], [event])

    def test_groups_are_ordered_and_one_group_is_processed_per_scan(self):
        states = {
            1: self._state(1),
            2: self._state(2),
            3: self._state(3),
            4: self._state(4, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
            5: self._state(5, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
            6: self._state(6, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
        }
        positions = {
            1: (9.5, 3.9),
            2: (9.5, 4.0),
            3: (9.5, 4.1),
            4: (9.5, 5.9),
            5: (9.5, 6.0),
            6: (9.5, 6.1),
        }
        upper_seeds = (
            (21, (10.0, 5.75), (9.7, 5.75)),
            (22, (10.0, 6.0), (9.7, 6.0)),
            (23, (10.0, 6.25), (9.7, 6.25)),
        )

        def neighbors(exit_id, approach):
            if abs(approach[1] - 4.0) < 1e-9:
                return self.SEEDS
            return upper_seeds

        context = self._context(
            states,
            positions,
            label_by_id={"501": 0, "502": 1},
            exits=[self.Exit(501), self.Exit(502)],
            neighbors=neighbors,
        )

        self.assertTrue(recovery_scan(context, 550))
        self.assertEqual([event["exitLabel"] for event in context.recovery_events], [0])
        self.assertEqual(context.recovered[:3].tolist(), [True, True, True])
        self.assertEqual(context.recovered[3:].tolist(), [False, False, False])

        self.assertTrue(recovery_scan(context, 600))
        self.assertEqual(
            [event["exitLabel"] for event in context.recovery_events], [0, 1]
        )
        self.assertEqual(context.recovered.tolist(), [True] * 6)
        summary = _build_recovery_summary([context])
        self.assertEqual(summary["recoveredGroupCount"], 2)
        self.assertEqual(summary["recoveredAgentCount"], 6)
        self.assertEqual(summary["events"][0]["exitLabel"], 0)
        self.assertEqual(summary["events"][1]["exitLabel"], 1)

    def test_two_runs_of_the_same_scene_produce_identical_events(self):
        def build():
            states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
            positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
            context = self._context(
                states,
                positions,
                neighbors=lambda exit_id, approach: self.SEEDS,
            )
            recovery_scan(context, 550)
            return _build_recovery_summary([context])

        self.assertEqual(build(), build())

    def test_groups_of_two_and_four_are_skipped_and_never_processed(self):
        states = {
            1: self._state(1),
            2: self._state(2),
            3: self._state(3, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
            4: self._state(4, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
            5: self._state(5, approach=(9.7, 6.0), exit_id=502, terminal=(10.0, 6.0)),
            6: self._state(6, approach=(9.7, 8.0), exit_id=503, terminal=(10.0, 8.0)),
            7: self._state(7, approach=(9.7, 8.0), exit_id=503, terminal=(10.0, 8.0)),
            8: self._state(8, approach=(9.7, 8.0), exit_id=503, terminal=(10.0, 8.0)),
            9: self._state(9, approach=(9.7, 8.0), exit_id=503, terminal=(10.0, 8.0)),
        }
        positions = {
            1: (9.5, 3.9),
            2: (9.5, 4.0),
            3: (9.5, 5.9),
            4: (9.5, 6.0),
            5: (9.5, 6.1),
            6: (9.5, 7.9),
            7: (9.5, 8.0),
            8: (9.5, 8.1),
            9: (9.5, 8.2),
        }
        context = self._context(
            states,
            positions,
            label_by_id={"501": 0, "502": 1, "503": 2},
            exits=[self.Exit(501), self.Exit(502), self.Exit(503)],
            neighbors=lambda exit_id, approach: self.SEEDS,
        )

        self.assertTrue(recovery_scan(context, 550))
        self.assertEqual(context.recovery_counters["eligible_groups"], 3)
        self.assertEqual(context.recovery_counters["skipped_groups"], 1)
        self.assertEqual(len(context.recovery_events), 1)
        self.assertEqual(context.recovery_events[0]["exitLabel"], 1)
        self.assertEqual(context.recovered.tolist(), [False, False, True, True, True, False, False, False, False])

        self.assertFalse(recovery_scan(context, 600))
        self.assertEqual(context.recovery_counters["skipped_groups"], 3)
        self.assertEqual(len(context.recovery_events), 1)

    def test_edge_seed_is_reported_and_attempted_only_once(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        edge_neighbors = (None, self.SEEDS[1], self.SEEDS[2])
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: edge_neighbors,
        )

        self.assertFalse(recovery_scan(context, 550))

        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_INFEASIBLE")
        self.assertEqual(event["reasonCode"], "EDGE_SEED")
        self.assertEqual(context.recovery_counters["infeasible_scans"], 1)
        self.assertEqual(context.recovered.tolist(), [False, False, False])
        self.assertEqual(len(context.attempted_group_signatures), 1)

        self.assertFalse(recovery_scan(context, 600))
        self.assertEqual(len(context.recovery_events), 1)
        self.assertEqual(context.recovery_counters["infeasible_scans"], 1)
        self.assertEqual(context.recovery_counters["skipped_groups"], 1)

    def test_insufficient_seeds_is_reported_as_no_seeds(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: None,
        )

        self.assertFalse(recovery_scan(context, 550))

        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_INFEASIBLE")
        self.assertEqual(event["reasonCode"], "NO_SEEDS")
        self.assertEqual(event["newTargets"], [])

    def test_connect_failure_and_reach_failure_report_their_reasons(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}

        connect_context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
        )
        connect_context.router.can_connect_fn = (
            lambda start, end: abs(end[1] - 4.25) > 1e-9
        )
        self.assertFalse(recovery_scan(connect_context, 550))
        self.assertEqual(
            connect_context.recovery_events[0]["reasonCode"], "CONNECT_FAILED"
        )
        self.assertEqual(
            connect_context.recovery_events[0]["newTargets"],
            [[10.0, 3.75], [10.0, 4.0], [10.0, 4.25]],
        )

        reach_context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
        )
        reach_context.router.can_reach_fn = (
            lambda start, end: abs(end[1] - 3.75) > 1e-9
        )
        self.assertFalse(recovery_scan(reach_context, 550))
        self.assertEqual(reach_context.recovery_events[0]["reasonCode"], "REACH_FAILED")

    class FailingState:
        """Wraps an AgentRouteState and raises on the chosen setter occurrence."""

        def __init__(self, state, fail_field=None, fail_on_occurrence=1):
            self._state = state
            self.fail_field = fail_field
            self.fail_on_occurrence = fail_on_occurrence
            self._sets = 0

        def __getattr__(self, name):
            return getattr(self._state, name)

        def _raise_on_occurrence(self, detail):
            self._sets += 1
            if self._sets == self.fail_on_occurrence:
                raise RuntimeError(detail)

        @property
        def waypoints(self):
            return self._state.waypoints

        @waypoints.setter
        def waypoints(self, value):
            if self.fail_field == "waypoints":
                self._raise_on_occurrence("secret waypoints failure")
            self._state.waypoints = value

        @property
        def terminal_point(self):
            return self._state.terminal_point

        @terminal_point.setter
        def terminal_point(self, value):
            if self.fail_field == "terminal_point":
                self._raise_on_occurrence("secret terminal failure")
            self._state.terminal_point = value

    class FailingArray:
        """Wraps a numpy array and raises on a chosen item assignment call."""

        def __init__(self, array, fail_on_call=None):
            self.underlying = array
            self._fail_on_call = fail_on_call
            self._calls = 0

        def __getitem__(self, index):
            return self.underlying[index]

        def __setitem__(self, index, value):
            self._calls += 1
            if self._calls == self._fail_on_call:
                raise RuntimeError("secret array failure")
            self.underlying[index] = value

    def test_mutation_failure_rolls_back_the_whole_group(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {
            1: self.Agent((9.7, 4.0)),
            2: self.Agent((9.7, 4.0), explode=True),
            3: self.Agent((9.7, 4.0)),
        }
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )

        self.assertFalse(recovery_scan(context, 550))

        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_MUTATION_FAILED")
        self.assertEqual(event["reasonCode"], "MUTATION_APPLY_FAILED")
        self.assertEqual(event["exceptionClass"], "RuntimeError")
        self.assertNotIn("secret failure detail", repr(event))
        self.assertNotIn("traceback", repr(event))
        self.assertEqual(context.recovered.tolist(), [False, False, False])
        self.assertNotIn("recovered_groups", context.recovery_counters)
        self.assertEqual(states[1].waypoints, ((9.7, 4.0),))
        self.assertEqual(states[1].terminal_point, (10.0, 4.0))
        self.assertEqual(states[2].waypoints, ((9.7, 4.0),))
        self.assertEqual(states[3].waypoints, ((9.7, 4.0),))
        self.assertEqual(agents[1].target, (9.7, 4.0))
        np.testing.assert_array_equal(
            context.terminal_points, [[10.0, 4.0], [10.0, 4.0], [10.0, 4.0]]
        )
        np.testing.assert_array_equal(
            context.waypoints, [[9.7, 4.0], [9.7, 4.0], [9.7, 4.0]]
        )

    def _run_mutation_failure(self, failing_index, failing_step):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {agent_id: self.Agent((9.7, 4.0)) for agent_id in states}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )
        ordered_ids = [1, 2, 3]
        target_id = ordered_ids[failing_index]
        if failing_step == "waypoints":
            context.states[target_id] = self.FailingState(
                states[target_id], fail_field="waypoints"
            )
        elif failing_step == "terminal_point":
            context.states[target_id] = self.FailingState(
                states[target_id], fail_field="terminal_point"
            )
        elif failing_step == "context_waypoints":
            context.waypoints = self.FailingArray(
                context.waypoints, fail_on_call=failing_index + 1
            )
        elif failing_step == "context_terminal_points":
            context.terminal_points = self.FailingArray(
                context.terminal_points, fail_on_call=failing_index + 1
            )
        elif failing_step == "agent_target":
            agents[target_id].explode = True
        else:
            raise AssertionError(f"unknown mutation step {failing_step}")

        self.assertFalse(recovery_scan(context, 550))

        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_MUTATION_FAILED")
        self.assertEqual(event["reasonCode"], "MUTATION_APPLY_FAILED")
        self.assertEqual(event["exceptionClass"], "RuntimeError")
        self.assertEqual(context.recovered.tolist(), [False, False, False])
        self.assertNotIn("recovered_groups", context.recovery_counters)
        for agent_id in states:
            self.assertEqual(states[agent_id].waypoints, ((9.7, 4.0),))
            self.assertEqual(states[agent_id].terminal_point, (10.0, 4.0))
            self.assertEqual(agents[agent_id].target, (9.7, 4.0))
        np.testing.assert_array_equal(
            context.terminal_points.underlying
            if isinstance(context.terminal_points, self.FailingArray)
            else context.terminal_points,
            [[10.0, 4.0], [10.0, 4.0], [10.0, 4.0]],
        )
        np.testing.assert_array_equal(
            context.waypoints.underlying
            if isinstance(context.waypoints, self.FailingArray)
            else context.waypoints,
            [[9.7, 4.0], [9.7, 4.0], [9.7, 4.0]],
        )

    def test_mutation_failure_injections_on_middle_agent_restore_everything(self):
        for failing_step in (
            "waypoints",
            "context_waypoints",
            "terminal_point",
            "context_terminal_points",
            "agent_target",
        ):
            with self.subTest(failing_step=failing_step):
                self._run_mutation_failure(1, failing_step)

    def test_mutation_failure_on_first_agent_restores_its_own_changed_values(self):
        for failing_step in (
            "waypoints",
            "context_waypoints",
            "terminal_point",
            "context_terminal_points",
            "agent_target",
        ):
            with self.subTest(failing_step=failing_step):
                self._run_mutation_failure(0, failing_step)

    def test_mutation_failure_on_third_agent_restores_all_applied_agents(self):
        for failing_step in (
            "waypoints",
            "context_waypoints",
            "terminal_point",
            "context_terminal_points",
            "agent_target",
        ):
            with self.subTest(failing_step=failing_step):
                self._run_mutation_failure(2, failing_step)

    def test_rollback_failure_aborts_with_typed_engine_error(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {agent_id: self.Agent((9.7, 4.0)) for agent_id in states}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )
        context.states[2] = self.FailingState(
            states[2], fail_field="waypoints", fail_on_occurrence=2
        )
        context.states[3] = self.FailingState(
            states[3], fail_field="terminal_point", fail_on_occurrence=1
        )

        with self.assertRaises(RecoveryMutationRollbackRunnerError):
            recovery_scan(context, 550)

        self.assertEqual(context.recovered.tolist(), [False, False, False])
        self.assertNotIn("recovered_groups", context.recovery_counters)

    def test_rollback_target_restore_failure_aborts_with_typed_engine_error(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {
            1: self.Agent((9.7, 4.0)),
            2: self.Agent((9.7, 4.0), explode=True, explode_occurrence=2),
            3: self.Agent((9.7, 4.0), explode=True, explode_occurrence=1),
        }
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )

        with self.assertRaises(RecoveryMutationRollbackRunnerError):
            recovery_scan(context, 550)

        self.assertEqual(context.recovered.tolist(), [False, False, False])
        self.assertNotIn("recovered_groups", context.recovery_counters)

    def test_rollback_failure_produces_no_normal_result_json(self):
        payload = {
            "model": {
                "modelProfile": "SFM_DEFAULT_V2",
                "routingProfile": "HAZARD_RADIAL_EXP_V3",
                "walkingSpeed": 1.2,
                "reactionTime": 0.5,
            },
            "drawing": {
                "outsideBoundary": [
                    {"x": 0, "y": 0},
                    {"x": 4, "y": 0},
                    {"x": 4, "y": 4},
                    {"x": 0, "y": 4},
                ],
                "walls": [],
                "pillars": [],
                "fabrics": [],
                "exits": [{"id": 1, "startX": 4, "startY": 1, "endX": 4, "endY": 3}],
            },
            "agents": [{"x": 1, "y": 2}],
            "hazards": [],
            "selectedExitIds": [1],
            "recoveryDetectorEnabled": True,
            "maxSimulationTimeSeconds": 1.0,
            "frameIntervalSeconds": 1.0,
        }

        class StubRouter:
            def plan(self, _position):
                return Route(
                    exit_id=1,
                    waypoints=((3.0, 2.0),),
                    terminal_point=(4.0, 2.0),
                    exit_start=(4.0, 1.3),
                    exit_end=(4.0, 2.7),
                    total_cost=1.0,
                )

        def stub_context(*_args, **_kwargs):
            state = AgentRouteState(
                stable_id=1,
                exit_id=1,
                waypoints=((3.0, 2.0),),
                terminal_point=(4.0, 2.0),
                exit_start=(4.0, 1.3),
                exit_end=(4.0, 2.7),
                cursor=0,
            )
            return SimulationContext(None, None, {11: state}, {11: (1.0, 2.0)}, numpy=np)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_dir = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")
            with (
                patch("runner._load_dependencies", return_value=(None, None, None, "test")),
                patch(
                    "route_planner.split_agent_components",
                    side_effect=lambda area, agents: [(object(), list(enumerate(agents)))],
                ),
                patch("route_planner.containing_component", return_value=box(0, 0, 4, 4)),
                patch("route_planner.GridRouter", new=lambda *args, **kwargs: StubRouter()),
                patch("runner._create_context", side_effect=stub_context),
                patch("runner._initialize_targets", return_value=[]),
                patch("runner._advance_context", return_value=[]),
                patch(
                    "runner.recovery_scan",
                    side_effect=RecoveryMutationRollbackRunnerError("rollback failed"),
                ),
            ):
                with self.assertRaises(RecoveryMutationRollbackRunnerError):
                    run(input_path, output_dir)

            self.assertFalse((output_dir / "result.json").exists())

    def test_readiness_any_excludes_agents_from_eligibility(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
        )
        context.readiness_any[1] = True

        self.assertFalse(recovery_scan(context, 550))

        self.assertEqual(context.recovery_counters["eligible_groups"], 1)
        self.assertEqual(context.recovery_counters["skipped_groups"], 1)
        self.assertEqual(len(context.recovery_events), 0)

    def test_update_targets_records_readiness_masks(self):
        state = self._state(1)
        context = self._context({1: state}, {1: (9.7, 4.0)})
        agent = context.simulation.agent(1)
        agent.position = (9.7, 4.0)
        context.router.can_reach_fn = lambda _start, _end: True
        context.router.reached_fn = lambda _position, _start, _end: False

        self.assertEqual(_update_targets(context, {1: agent}, context.positions), [])

        self.assertEqual(context.removal_ready.tolist(), [True])
        self.assertEqual(context.readiness_any.tolist(), [True])

        far = self._context({2: self._state(2)}, {2: (8.0, 4.0)})
        far_agent = far.simulation.agent(2)
        far.router.can_reach_fn = lambda _start, _end: False
        far.router.reached_fn = lambda _position, _start, _end: False

        self.assertEqual(_update_targets(far, {2: far_agent}, far.positions), [])

        self.assertEqual(far.removal_ready.tolist(), [False])
        self.assertEqual(far.readiness_any.tolist(), [False])

    def test_post_recovery_invalid_moves_are_accounted_in_the_summary(self):
        states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        positions = {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)}
        agents = {1: self.Agent(), 2: self.Agent(), 3: self.Agent()}
        context = self._context(
            states,
            positions,
            neighbors=lambda exit_id, approach: self.SEEDS,
            agents=agents,
        )
        self.assertTrue(recovery_scan(context, 550))

        context.router.valid_moves = lambda starts, _ends: [False] * len(starts)
        context.router.can_connect_fn = lambda _start, _end: False
        previous = context.positions.copy()
        current = context.positions + 0.01
        agent_handles = {agent_id: context.simulation.agent(agent_id) for agent_id in states}
        for slot, agent_id in enumerate(states):
            agent_handles[agent_id].position = (9.5 + slot, 4.0)
        _rollback_invalid_moves(
            context,
            previous,
            agent_handles,
            current,
            np.zeros(len(states), dtype=bool),
            600,
        )

        self.assertEqual(context.post_recovery_invalid_moves.tolist(), [1, 1, 1])
        self.assertEqual(context.post_recovery_full_rollbacks.tolist(), [1, 1, 1])
        summary = _build_recovery_summary([context])
        self.assertEqual(summary["events"][0]["postRecoveryInvalidMoves"], 3)
        self.assertEqual(summary["events"][0]["postRecoveryFullRollbacks"], 3)

    def test_rollback_requires_the_iteration_number(self):
        states = {1: self._state(1)}
        context = self._context({1: self._state(1)}, {1: (9.5, 4.0)})
        agent = context.simulation.agent(1)

        with self.assertRaisesRegex(TypeError, "iteration"):
            _rollback_invalid_moves(
                context,
                context.positions,
                {1: agent},
                context.positions.copy(),
                np.asarray([False]),
            )

    def test_contexts_with_identical_labels_and_targets_do_not_collide(self):
        first_states = {1: self._state(1), 2: self._state(2), 3: self._state(3)}
        second_states = {11: self._state(1), 12: self._state(2), 13: self._state(3)}
        first = self._context(
            first_states,
            {1: (9.5, 3.9), 2: (9.5, 4.0), 3: (9.5, 4.1)},
            neighbors=lambda exit_id, approach: self.SEEDS,
            context_index=0,
        )
        second = self._context(
            second_states,
            {11: (9.5, 3.9), 12: (9.5, 4.0), 13: (9.5, 4.1)},
            neighbors=lambda exit_id, approach: self.SEEDS,
            context_index=1,
        )

        self.assertTrue(recovery_scan(first, 550))
        self.assertTrue(recovery_scan(second, 550))

        self.assertEqual(len(first.attempted_group_signatures), 1)
        self.assertEqual(len(second.attempted_group_signatures), 1)
        summary = _build_recovery_summary([first, second])
        self.assertEqual(summary["recoveredGroupCount"], 2)
        self.assertEqual(summary["recoveredAgentCount"], 6)
        self.assertEqual(
            [event["contextIndex"] for event in summary["events"]], [0, 1]
        )
        self.assertEqual(summary["events"][0]["exitId"], 501)
        self.assertEqual(summary["events"][1]["exitId"], 501)


class MidRouteRecoveryScanTest(unittest.TestCase):
    class Model:
        def __init__(self):
            self.velocity = (0.0, 0.0)

    class Agent:
        def __init__(self, target=None):
            self._target = target
            self.position = (0.0, 0.0)
            self.model = MidRouteRecoveryScanTest.Model()

        @property
        def target(self):
            return self._target

        @target.setter
        def target(self, value):
            self._target = value

    class Simulation:
        def __init__(self, agents):
            self.agents_by_id = agents

        def agent(self, agent_id):
            return self.agents_by_id[agent_id]

        def mark_agent_for_removal(self, _agent_id):
            return False

    class Router:
        def __init__(self, plan_fn):
            self._exit_labels = {"501": 0}
            self._plan_fn = plan_fn

        def plan(self, position):
            return self._plan_fn(position)

    def _state(self, stable_id, waypoints):
        return AgentRouteState(
            stable_id=stable_id,
            exit_id=501,
            waypoints=waypoints,
            terminal_point=(10.0, 4.0),
            exit_start=(10.0, 1.0),
            exit_end=(10.0, 7.0),
            cursor=0,
        )

    def _context(self, states, positions, plan_fn):
        agents = {agent_id: self.Agent((9.7, 4.0)) for agent_id in states}
        simulation = self.Simulation(agents)
        router = self.Router(plan_fn)
        context = SimulationContext(
            simulation,
            router,
            states,
            positions,
            numpy=np,
        )
        for slot in range(len(states)):
            context.stationary_streak[slot] = 500
            context.last_invalid_iteration[slot] = -1
            context.readiness_any[slot] = False
        return context, agents

    def _new_route(self):
        return SimpleNamespace(
            exit_id=501,
            waypoints=((9.4, 4.0), (9.7, 4.0), (10.0, 4.0)),
            terminal_point=(10.0, 4.0),
            exit_start=(10.0, 1.0),
            exit_end=(10.0, 7.0),
        )

    def test_recovers_mid_route_stuck_agent(self):
        states = {
            1: self._state(1, ((9.0, 4.0), (9.4, 4.0), (9.7, 4.0))),
        }
        context, agents = self._context(states, {1: (9.3, 4.0)}, lambda _pos: self._new_route())

        applied = mid_route_recovery_scan(context, 550)

        self.assertTrue(applied)
        self.assertEqual(states[1].waypoints, ((9.4, 4.0), (9.7, 4.0), (10.0, 4.0)))
        self.assertEqual(states[1].cursor, 1)
        self.assertTrue(context.recovered[0])
        self.assertEqual(context.recovery_counters.get("recovered_mid_route"), 1)
        self.assertEqual(agents[1].target, (9.7, 4.0))
        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERED")
        self.assertEqual(event["exitLabel"], 0)

    def test_recovers_stuck_agent_following_final_waypoint(self):
        states = {
            1: self._state(1, ((9.7, 4.0),)),
        }
        context, _agents = self._context(
            states, {1: (9.5, 4.0)}, lambda _pos: self._new_route()
        )

        self.assertTrue(mid_route_recovery_scan(context, 550))
        self.assertTrue(context.recovered[0])
        self.assertEqual(context.recovery_counters.get("recovered_mid_route"), 1)

    def test_ignores_exit_ready_final_stage_agent(self):
        states = {
            1: self._state(1, ((9.7, 4.0),)),
        }
        context, _agents = self._context(
            states, {1: (9.5, 4.0)}, lambda _pos: self._new_route()
        )
        context.readiness_any[0] = True

        self.assertFalse(mid_route_recovery_scan(context, 550))
        self.assertEqual(context.recovery_counters.get("recovered_mid_route", 0), 0)

    def test_ignores_non_stationary_agent(self):
        states = {
            1: self._state(1, ((9.0, 4.0), (9.4, 4.0), (9.7, 4.0))),
        }
        context, _agents = self._context(
            states, {1: (9.3, 4.0)}, lambda _pos: self._new_route()
        )
        context.stationary_streak[0] = 10

        self.assertFalse(mid_route_recovery_scan(context, 550))
        self.assertEqual(context.recovery_counters.get("recovered_mid_route", 0), 0)

    def test_reroute_unreachable_records_infeasible(self):
        def plan(_pos):
            raise AgentRouteUnreachableError()

        states = {
            1: self._state(1, ((9.0, 4.0), (9.4, 4.0), (9.7, 4.0))),
        }
        context, _agents = self._context(states, {1: (9.3, 4.0)}, plan)

        self.assertFalse(mid_route_recovery_scan(context, 550))
        self.assertFalse(context.recovered[0])
        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_INFEASIBLE")
        self.assertEqual(event["reasonCode"], "REROUTE_UNREACHABLE")

    def test_reroute_unchanged_records_infeasible(self):
        states = {
            1: self._state(1, ((9.0, 4.0), (9.4, 4.0), (9.7, 4.0))),
        }
        unchanged = SimpleNamespace(
            exit_id=501,
            waypoints=((9.0, 4.0), (9.4, 4.0), (9.7, 4.0)),
            terminal_point=(10.0, 4.0),
            exit_start=(10.0, 1.0),
            exit_end=(10.0, 7.0),
        )
        context, _agents = self._context(states, {1: (9.3, 4.0)}, lambda _pos: unchanged)

        self.assertFalse(mid_route_recovery_scan(context, 550))
        event = context.recovery_events[0]
        self.assertEqual(event["status"], "RECOVERY_INFEASIBLE")
        self.assertEqual(event["reasonCode"], "REROUTE_UNCHANGED")


if __name__ == "__main__":
    unittest.main()
