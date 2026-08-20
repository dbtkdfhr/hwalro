import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from shapely.geometry import LineString


@unittest.skipUnless(os.environ.get("RUN_JUPEDSIM_SMOKE") == "1", "optional JuPedSim smoke test")
class JuPedSimSmokeTest(unittest.TestCase):
    def test_small_room_evacuation_writes_result_and_timeline(self):
        from runner import run

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
                "walls": [
                    {"startX": 0.5, "startY": 3, "endX": 1.5, "endY": 3},
                    {"startX": 1.5, "startY": 3, "endX": 1.5, "endY": 3.8},
                    {"startX": 1.5, "startY": 3.8, "endX": 0.5, "endY": 3.8},
                    {"startX": 0.5, "startY": 3.8, "endX": 0.5, "endY": 3},
                ],
                "pillars": [],
                "fabrics": [],
                "exits": [{"id": 1, "startX": 4, "startY": 1.5, "endX": 4, "endY": 2.5}],
            },
            "agents": [{"x": 1, "y": 2}],
            "hazards": [],
            "selectedExitIds": [1],
            "maxSimulationTimeSeconds": 10,
            "frameIntervalSeconds": 1,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            phase_profile_path = root / "phase-profile.json"
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            with patch.dict(
                os.environ,
                {"HWALRO_PHASE_PROFILE_PATH": str(phase_profile_path)},
            ):
                result = run(input_path, output_path)

            self.assertEqual(result["engineVersion"], "1.4.2+hwalro.2")
            self.assertEqual(result["terminationReason"], "ALL_EVACUATED")
            self.assertEqual(result["evacuatedPeople"], 1)
            self.assertEqual(result["remainingPeople"], 0)
            self.assertIsNotNone(result["totalEvacuationTimeSeconds"])
            self.assertTrue((output_path / "result.json").is_file())
            self.assertEqual(result["heatmapChunkCount"], result["timelineChunkCount"])
            heatmap = json.loads(
                (output_path / "heatmap" / "000000.json").read_text("utf-8")
            )
            self.assertEqual(heatmap["densityMethod"], "GRID_COUNT")
            timeline = json.loads(
                (output_path / "timeline" / f"{result['timelineChunkCount'] - 1:06d}.json").read_text(
                    "utf-8"
                )
            )
            self.assertEqual(timeline["frames"][-1]["timeSeconds"], result["simulationDurationSeconds"])
            self.assertEqual(timeline["frames"][-1]["agents"], [])
            phase_profile = json.loads(phase_profile_path.read_text("utf-8"))
            self.assertEqual(phase_profile["schemaVersion"], 1)
            self.assertEqual(
                set(phase_profile["phasesNanoseconds"]),
                {
                    "inputAndContextSetup",
                    "routePlanning",
                    "iterate",
                    "agentStateCapture",
                    "moveValidation",
                    "targetAndExitUpdate",
                    "snapshotAndSerialization",
                    "recoveryScan",
                    "recoveryMutation",
                },
            )
            self.assertGreater(phase_profile["counters"]["agentSteps"], 0)

            baseline_duration = result["simulationDurationSeconds"]
            payload["randomSeed"] = 123
            payload["model"]["reactionTime"] = 2.0
            payload["model"]["initialResponseTimeMean"] = 2.0
            payload["model"]["initialResponseTimeStdDev"] = 0.0
            delayed_output = root / "delayed-output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")
            delayed = run(input_path, delayed_output)
            delayed_timeline = json.loads(
                (delayed_output / "timeline" / "000000.json").read_text("utf-8")
            )
            self.assertEqual(delayed["terminationReason"], "ALL_EVACUATED")
            self.assertAlmostEqual(
                delayed["simulationDurationSeconds"] - baseline_duration, 2.0, delta=0.01
            )
            waiting_position = delayed_timeline["frames"][1]["agents"][0]
            self.assertEqual(waiting_position["agentId"], 1)
            self.assertLess(
                ((waiting_position["x"] - 1.0) ** 2 + (waiting_position["y"] - 2.0) ** 2)
                ** 0.5,
                0.01,
            )

            payload["maxSimulationTimeSeconds"] = 0.05
            input_path.write_text(json.dumps(payload), encoding="utf-8")
            limited_output = root / "limited-output"
            limited = run(input_path, limited_output)
            limited_timeline = json.loads(
                (limited_output / "timeline" / "000000.json").read_text("utf-8")
            )
            self.assertEqual(limited["terminationReason"], "MAX_DURATION")
            self.assertEqual(limited["simulationDurationSeconds"], 0.05)
            self.assertEqual(limited["evacuatedPeople"], 0)
            self.assertEqual(limited["remainingPeople"], 1)
            self.assertIsNone(limited["totalEvacuationTimeSeconds"])
            self.assertIsNone(limited["averageEvacuationTimeSeconds"])
            self.assertEqual(limited_timeline["frames"][-1]["timeSeconds"], 0.05)
            self.assertEqual(len(limited_timeline["frames"][-1]["agents"]), 1)

    def test_agents_route_around_wall_without_stalling_or_crossing_it(self):
        from runner import run

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
                    {"x": 6, "y": 0},
                    {"x": 6, "y": 5},
                    {"x": 0, "y": 5},
                ],
                "walls": [{"startX": 3, "startY": 0, "endX": 3, "endY": 4}],
                "pillars": [],
                "fabrics": [],
                "exits": [{"id": 1, "startX": 6, "startY": 4.0, "endX": 6, "endY": 4.8}],
            },
            "agents": [
                {"x": 1, "y": 1.5},
                {"x": 1, "y": 2.2},
                {"x": 1.7, "y": 1.5},
                {"x": 1.7, "y": 2.2},
            ],
            "hazards": [],
            "selectedExitIds": [1],
            "maxSimulationTimeSeconds": 60,
            "frameIntervalSeconds": 0.1,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            result = run(input_path, output_path)

            self.assertEqual(result["terminationReason"], "ALL_EVACUATED")
            self.assertEqual(result["evacuatedPeople"], 4)
            wall = LineString(((3, 0), (3, 4)))
            previous = {}
            for chunk_path in sorted((output_path / "timeline").glob("*.json")):
                chunk = json.loads(chunk_path.read_text("utf-8"))
                for frame in chunk["frames"]:
                    for agent in frame["agents"]:
                        agent_id = agent["agentId"]
                        point = (agent["x"], agent["y"])
                        if agent_id in previous:
                            self.assertFalse(LineString((previous[agent_id], point)).crosses(wall))
                        previous[agent_id] = point

    def test_local_wheel_can_restore_agent_position_and_velocity(self):
        import jupedsim as jps

        simulation = jps.Simulation(
            model=jps.SocialForceModel(),
            geometry=[(0, 0), (4, 0), (4, 4), (0, 4)],
            dt=0.01,
        )
        stage_id = simulation.add_direct_steering_stage()
        journey_id = simulation.add_journey(jps.JourneyDescription([stage_id]))
        agent_id = simulation.add_agent(
            jps.SocialForceModelAgentParameters(
                position=(1, 1), journey_id=journey_id, stage_id=stage_id
            )
        )
        agent = simulation.agent(agent_id)

        agent.position = (2, 2)
        agent.model.velocity = (0, 0)

        self.assertEqual(agent.position, (2.0, 2.0))
        self.assertEqual(agent.model.velocity, (0.0, 0.0))

    def test_mixed_boundary_and_interior_exit_selection_runs_and_evacuates(self):
        from runner import run

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
                    {"x": 20, "y": 0},
                    {"x": 20, "y": 10},
                    {"x": 0, "y": 10},
                ],
                "walls": [],
                "pillars": [],
                "fabrics": [],
                "exits": [
                    {"id": 1, "startX": 20, "startY": 4, "endX": 20, "endY": 6},
                    {"id": 2, "startX": 10, "startY": 4, "endX": 10, "endY": 6},
                ],
            },
            "agents": [
                {"x": 16, "y": 5},
                {"x": 17, "y": 4.4},
                {"x": 17, "y": 5.6},
            ],
            "hazards": [],
            "selectedExitIds": [1, 2],
            "maxSimulationTimeSeconds": 60,
            "frameIntervalSeconds": 1,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            result = run(input_path, output_path)

            self.assertEqual(result["terminationReason"], "ALL_EVACUATED")
            self.assertEqual(result["evacuatedPeople"], 3)
            self.assertFalse((output_path / "error.json").exists())
            exit_events = [
                event["exitId"]
                for chunk_path in sorted((output_path / "timeline").glob("*.json"))
                for event in json.loads(chunk_path.read_text("utf-8")).get("exitEvents", [])
            ]
            self.assertTrue(exit_events)
            self.assertTrue(all(exit_id == 1 for exit_id in exit_events))

    def test_wide_single_exit_endpoint_stall_recovers_to_all_evacuated(self):
        from runner import run

        payload = {
            "model": {
                "modelProfile": "SFM_DEFAULT_V2",
                "routingProfile": "HAZARD_RADIAL_EXP_V3",
                "walkingSpeed": 1.25,
                "reactionTime": 0.5,
            },
            "drawing": {
                "outsideBoundary": [
                    {"x": 31.2, "y": 5.5},
                    {"x": 31.2, "y": 71.2},
                    {"x": 113.3, "y": 71.8},
                    {"x": 113.3, "y": 5.5},
                ],
                "walls": [],
                "pillars": [
                    {"startX": 35.1, "startY": 10.3, "endX": 106.5, "endY": 64.7, "rotation": 0.0}
                ],
                "fabrics": [],
                "exits": [{"id": 19, "startX": 31.2, "startY": 71.2, "endX": 51.1, "endY": 71.3}],
            },
            "agents": [
                {"x": 105.1252, "y": 8.2939},
                {"x": 104.4181, "y": 8.3832},
                {"x": 105.6372, "y": 8.8181},
                {"x": 104.6006, "y": 9.4711},
                {"x": 106.9916, "y": 11.1521},
                {"x": 107.2246, "y": 11.9509},
                {"x": 107.8059, "y": 13.134},
                {"x": 108.4439, "y": 10.7843},
                {"x": 107.8434, "y": 12.4854},
                {"x": 109.7756, "y": 10.0728},
                {"x": 110.609, "y": 10.3845},
                {"x": 109.8136, "y": 8.1897},
                {"x": 109.754, "y": 7.4071},
                {"x": 110.005, "y": 8.7804},
                {"x": 105.0236, "y": 5.9399},
                {"x": 106.0845, "y": 5.8776},
                {"x": 100.5952, "y": 7.3403},
                {"x": 101.7576, "y": 7.1808},
                {"x": 101.4767, "y": 8.0085},
                {"x": 96.8531, "y": 9.2658},
                {"x": 97.0279, "y": 8.5612},
                {"x": 96.3118, "y": 9.5318},
                {"x": 100.7267, "y": 8.5015},
                {"x": 99.9612, "y": 8.5806},
                {"x": 100.2654, "y": 9.4262},
                {"x": 99.7512, "y": 9.7561},
            ],
            "hazards": [],
            "selectedExitIds": [19],
            "maxSimulationTimeSeconds": 600,
            "frameIntervalSeconds": 1,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            result = run(input_path, output_path)

            self.assertEqual(result["terminationReason"], "ALL_EVACUATED")
            self.assertEqual(result["evacuatedPeople"], 26)
            self.assertEqual(result["remainingPeople"], 0)
            self.assertFalse((output_path / "error.json").exists())

    def test_agent_in_component_without_connected_exit_fails_with_typed_setup_error(self):
        from runner import run
        from runner import NoReachableSelectedExitRunnerError

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
            "maxSimulationTimeSeconds": 0.05,
            "frameIntervalSeconds": 0.01,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")

            with self.assertRaises(NoReachableSelectedExitRunnerError):
                run(input_path, output_path)

            error = json.loads((output_path / "error.json").read_text("utf-8"))
            self.assertEqual(error["code"], "NO_REACHABLE_SELECTED_EXIT")
            self.assertEqual(error["affectedAgentCount"], 1)
            self.assertEqual(error["representativeAgentIds"], [2])
            self.assertEqual(error["componentCount"], 1)
            self.assertEqual(error["reason"], "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT")
            self.assertFalse((output_path / "result.json").exists())
            self.assertFalse((output_path / "timeline").exists())
            self.assertFalse((output_path / "heatmap").exists())

    @staticmethod
    def _run_fixture(flag):
        from runner import run
        from tests.fixtures.build_fixture_013 import build_payload

        payload = build_payload()
        payload["recoveryDetectorEnabled"] = flag
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output"
            input_path.write_text(json.dumps(payload), encoding="utf-8")
            result = run(input_path, output_path)
            result_bytes = (output_path / "result.json").read_bytes()
            chunk_bytes = {}
            for subdirectory in ("timeline", "heatmap"):
                folder = output_path / subdirectory
                if folder.exists():
                    for chunk in sorted(folder.glob("*.json")):
                        chunk_bytes[f"{subdirectory}/{chunk.name}"] = chunk.read_bytes()
            return result, result_bytes, chunk_bytes

    def test_synthetic_fixture_flag_false_is_deterministic_and_has_no_recovery_summary(self):
        first, first_bytes, _first_chunks = self._run_fixture(False)
        second, second_bytes, _second_chunks = self._run_fixture(False)

        self.assertEqual(first["terminationReason"], "ALL_EVACUATED")
        self.assertEqual(first["evacuatedPeople"], 4)
        self.assertEqual(first["remainingPeople"], 0)
        self.assertNotIn("recoverySummary", first)
        self.assertEqual(first_bytes, second_bytes)
        self.assertEqual(first, second)

    def test_synthetic_fixture_flag_true_keeps_dynamics_and_records_empty_recovery_summary(self):
        disabled, disabled_bytes, disabled_chunks = self._run_fixture(False)
        enabled, enabled_bytes, enabled_chunks = self._run_fixture(True)

        self.assertEqual(enabled["terminationReason"], disabled["terminationReason"])
        self.assertEqual(enabled["remainingPeople"], disabled["remainingPeople"])
        summary = enabled["recoverySummary"]
        self.assertEqual(summary["schemaVersion"], 1)
        self.assertGreaterEqual(summary["scanCount"], 1)
        self.assertEqual(summary["eligibleGroupCount"], 0)
        self.assertEqual(summary["skippedEligibleGroupCount"], 0)
        self.assertEqual(summary["recoveredGroupCount"], 0)
        self.assertEqual(summary["recoveredAgentCount"], 0)
        self.assertEqual(summary["events"], [])
        self.assertEqual(enabled_chunks, disabled_chunks)
        self.assertNotEqual(enabled_bytes, disabled_bytes)

    def test_synthetic_fixture_reversed_exit_endpoints_behave_identically(self):
        from runner import run
        from tests.fixtures.build_fixture_013 import build_payload

        payload = build_payload()
        payload["recoveryDetectorEnabled"] = False
        reversed_payload = json.loads(json.dumps(payload))
        exit_ = reversed_payload["drawing"]["exits"][0]
        exit_["startX"], exit_["startY"], exit_["endX"], exit_["endY"] = (
            exit_["endX"],
            exit_["endY"],
            exit_["startX"],
            exit_["startY"],
        )

        results = []
        for candidate in (payload, reversed_payload):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                input_path = root / "input.json"
                output_path = root / "output"
                input_path.write_text(json.dumps(candidate), encoding="utf-8")
                results.append(run(input_path, output_path))

        self.assertEqual(results[0]["terminationReason"], "ALL_EVACUATED")
        self.assertEqual(results[1]["terminationReason"], "ALL_EVACUATED")
        self.assertEqual(results[0], results[1])
        self.assertNotIn("recoverySummary", results[0])
        self.assertNotIn("recoverySummary", results[1])


if __name__ == "__main__":
    unittest.main()
