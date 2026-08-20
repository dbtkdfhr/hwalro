import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import benchmark


class BenchmarkTest(unittest.TestCase):
    def test_runner_clears_unrequested_parent_phase_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            output.mkdir()
            (output / "result.json").write_text(
                '{"engineVersion":"test"}\n', encoding="utf-8"
            )
            with patch.dict(
                benchmark.os.environ,
                {benchmark.PHASE_PROFILE_ENVIRONMENT_VARIABLE: str(root / "parent.json")},
            ), patch.object(
                benchmark.subprocess,
                "run",
                return_value=SimpleNamespace(returncode=0, stdout="", stderr=""),
            ) as run_process:
                benchmark._run_runner(
                    "python", root, root / "input.json", output, None
                )

        environment = run_process.call_args.kwargs["env"]
        self.assertNotIn(benchmark.PHASE_PROFILE_ENVIRONMENT_VARIABLE, environment)

    def test_production_fixture_is_external_and_forces_acceptance_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "fixture.json"
            fixture.write_text(
                json.dumps(
                    {
                        "agents": [{"x": 0.0, "y": 0.0}] * 5000,
                        "maxSimulationTimeSeconds": 10.0,
                        "frameIntervalSeconds": 10.0,
                    }
                ),
                encoding="utf-8",
            )

            scenario = benchmark._production_scenario(str(fixture))

        self.assertEqual(5000, scenario.agent_count)
        self.assertEqual(60000, scenario.iterations)
        self.assertEqual(600.0, scenario.payload["maxSimulationTimeSeconds"])
        self.assertEqual(1.0, scenario.payload["frameIntervalSeconds"])
        self.assertEqual("external-anonymized-fixture", scenario.source["kind"])

    def test_long_runtime_scenarios_are_600_seconds_at_one_hertz(self):
        scenarios = benchmark._long_runtime_scenarios()

        self.assertEqual([1000, 2500, 5000], [item.agent_count for item in scenarios])
        self.assertTrue(all(item.iterations == 60000 for item in scenarios))
        self.assertTrue(
            all(item.payload["frameIntervalSeconds"] == 1.0 for item in scenarios)
        )

    def test_candidate_median_threshold_marks_long_scenario_failed(self):
        scenario = benchmark.Scenario(
            name="long",
            category="long-runtime",
            agent_count=5000,
            iterations=60000,
            payload={},
            source={},
        )
        files = {"result.json": {"bytes": 1, "sha256": "same"}}
        summary = {"result": {"engineVersion": "same"}}
        baseline = benchmark.RunResult(
            602_000_000_000, "same", files, summary, None
        )
        candidate = benchmark.RunResult(
            601_000_000_000, "same", files, summary, None
        )
        pair = {"baseline": baseline, "candidate": candidate}

        with tempfile.TemporaryDirectory() as directory, patch.object(
            benchmark, "_run_pair", return_value=pair
        ):
            result = benchmark._benchmark_scenario(
                scenario,
                0,
                1,
                {"baseline": "python", "candidate": "python"},
                {"baseline": Path(directory), "candidate": Path(directory)},
                Path(directory),
                600.0,
                False,
            )

        self.assertFalse(result["acceptance"]["passed"])
        self.assertEqual(600.0, result["acceptance"]["candidateMedianThresholdSeconds"])

    def test_common_and_separate_python_cli_forms_are_accepted(self):
        common = benchmark._parse_args(
            [
                "--baseline-root",
                "baseline",
                "--candidate-root",
                "candidate",
                "--python",
                "python",
                "--output",
                "out",
            ]
        )
        separate = benchmark._parse_args(
            [
                "--baseline-root",
                "baseline",
                "--candidate-root",
                "candidate",
                "--baseline-python",
                "python-a",
                "--candidate-python",
                "python-b",
                "--output",
                "out",
            ]
        )

        self.assertEqual("python", common.python)
        self.assertEqual("python-a", separate.baseline_python)
        self.assertEqual("python-b", separate.candidate_python)

    def test_output_mismatch_writes_failed_result(self):
        scenario = benchmark.Scenario("mismatch", "runtime", 1, 1, {}, {})
        engine_root = Path(benchmark.__file__).parent
        with tempfile.TemporaryDirectory() as directory, patch.object(
            benchmark, "_resolve_python", return_value=str(Path(__file__).resolve())
        ), patch.object(
            benchmark, "_resolve_engine_root", return_value=engine_root
        ), patch.object(
            benchmark, "_build_scenarios", return_value=[scenario]
        ), patch.object(
            benchmark,
            "_benchmark_scenario",
            side_effect=benchmark.OutputMismatch("different result.json"),
        ), patch.object(
            benchmark, "_python_metadata", return_value={}
        ), patch.object(
            benchmark, "_implementation_metadata", return_value={}
        ):
            result_path = benchmark.run(
                [
                    "--baseline-root",
                    "baseline",
                    "--candidate-root",
                    "candidate",
                    "--python",
                    "python",
                    "--output",
                    directory,
                ]
            )
            result = json.loads(result_path.read_text(encoding="utf-8"))

        self.assertEqual("failed", result["status"])
        self.assertEqual("output-mismatch", result["failures"][0]["kind"])

    def test_output_comparison_ignores_only_engine_version(self):
        baseline = benchmark.RunResult(
            1,
            "baseline",
            {"result.json": {"bytes": 10, "sha256": "baseline"}},
            {"result": {"engineVersion": "old", "evacuatedPeople": 5}},
            None,
            b'{"engineVersion":"old","evacuatedPeople":5}\n',
        )
        candidate = benchmark.RunResult(
            1,
            "candidate",
            {"result.json": {"bytes": 11, "sha256": "candidate"}},
            {"result": {"engineVersion": "new", "evacuatedPeople": 5}},
            None,
            b'{"engineVersion":"new","evacuatedPeople":5}\n',
        )

        benchmark._assert_same_output(
            "scenario",
            "phase",
            baseline,
            candidate,
            None,
            ignore_engine_version=True,
        )
        with self.assertRaises(benchmark.OutputMismatch):
            benchmark._assert_same_output(
                "scenario", "phase", baseline, candidate, None
            )

        changed = benchmark.RunResult(
            1,
            "candidate",
            candidate.files,
            {"result": {"engineVersion": "new", "evacuatedPeople": 4}},
            None,
            b'{"engineVersion":"new","evacuatedPeople":4}\n',
        )
        with self.assertRaises(benchmark.OutputMismatch):
            benchmark._assert_same_output(
                "scenario",
                "phase",
                baseline,
                changed,
                None,
                ignore_engine_version=True,
            )

        representation_changed = benchmark.RunResult(
            1,
            "candidate",
            candidate.files,
            candidate.summary,
            None,
            b'{ "engineVersion": "new", "evacuatedPeople": 5 }\n',
        )
        with self.assertRaises(benchmark.OutputMismatch):
            benchmark._assert_same_output(
                "scenario",
                "phase",
                baseline,
                representation_changed,
                None,
                ignore_engine_version=True,
            )


if __name__ == "__main__":
    unittest.main()
