"""Contract tests for the V2-versus-V3 benchmark harness."""

from __future__ import annotations

import json
from typing import assert_never

import layout_search
from constraints import parse_constraints
from shapely.geometry import box
from tests.v3_benchmark_fixtures import JsonValue, benchmark_cases
from tests.v3_benchmark_harness import (
    PlannerSnapshot,
    PlannerUnavailable,
    V3_GRID_PLANNER_MODE,
    V3_GRID_PLANNER_VERSION,
    compare_v2_to_v3,
)


def test_compare_v2_to_v3_reports_unavailable_v3_without_claiming_success() -> None:
    """Given benchmark cases and no V3 runner, when compared, then V3 is unavailable."""
    # Given
    cases = benchmark_cases()

    # When
    report = compare_v2_to_v3(cases)

    # Then
    payload = json.loads(report.to_json())
    assert len(payload["cases"]) >= 10
    assert {case["v3"]["status"] for case in payload["cases"]} == {"UNAVAILABLE"}
    assert payload["summary"]["v3Available"] is False


def test_benchmark_cases_cover_required_geometry_and_constraints() -> None:
    cases = benchmark_cases()
    by_id = {case.case_id: case for case in cases}

    assert len(cases) == 10
    assert set(by_id) == {
        "straight-single-blocker",
        "straight-paired-blockers",
        "tapered-corridor",
        "l-turn",
        "s-turn",
        "multi-exit",
        "interacting-blockers",
        "within-zone",
        "fixed-blocker",
        "empty-noop",
    }
    tapered_walls = by_id["tapered-corridor"].input_data["drawing"]["walls"]
    assert tapered_walls[1]["startX"] - tapered_walls[0]["startX"] == 8.0
    assert tapered_walls[1]["endX"] - tapered_walls[0]["endX"] == 4.0
    assert len(by_id["multi-exit"].input_data["selectedExitIds"]) == 2
    within_constraints = parse_constraints(by_id["within-zone"].input_data["constraints"])
    assert within_constraints.allows_placement(10, box(3.5, 3.0, 8.5, 8.0))
    assert not within_constraints.allows_placement(10, box(3.0, 3.0, 8.5, 8.0))
    fixed_constraints = parse_constraints(by_id["fixed-blocker"].input_data["constraints"])
    assert not fixed_constraints.can_move(10)
    assert by_id["empty-noop"].input_data["drawing"]["fabrics"] == []


def test_compare_v2_to_v3_rejects_v2_output_disguised_as_v3() -> None:
    cases = benchmark_cases()

    report = compare_v2_to_v3(
        cases,
        lambda payload: layout_search.generate({**payload, "plannerMode": "IDEAL_ROUTE_DOCKING"}),
    )

    assert report.summary.v3_available is False
    for case in report.cases:
        match case.v3:
            case PlannerSnapshot() as snapshot:
                raise AssertionError(f"V2 output was incorrectly accepted as V3: {snapshot}")
            case PlannerUnavailable() as unavailable:
                assert unavailable.status == "CONTRACT_MISMATCH"
            case unreachable:
                assert_never(unreachable)


def test_compare_v2_to_v3_dispatches_grid_mode_and_accepts_matching_version() -> None:
    cases = benchmark_cases()
    received_modes: list[str] = []

    def v3_runner(payload: dict[str, JsonValue]) -> dict[str, JsonValue]:
        received_modes.append(str(payload["plannerMode"]))
        result = layout_search.generate({**payload, "plannerMode": "IDEAL_ROUTE_DOCKING"})
        return {**result, "plannerVersion": V3_GRID_PLANNER_VERSION}

    report = compare_v2_to_v3(cases, v3_runner)

    assert received_modes == [V3_GRID_PLANNER_MODE] * len(cases)
    assert report.summary.v3_available is True
    for case in report.cases:
        match case.v3:
            case PlannerSnapshot() as snapshot:
                assert snapshot.planner_version == V3_GRID_PLANNER_VERSION
            case PlannerUnavailable() as unavailable:
                raise AssertionError(f"matching V3 version was rejected: {unavailable}")
            case unreachable:
                assert_never(unreachable)


def test_compare_v2_to_v3_is_deterministic_for_the_fixed_corpus() -> None:
    cases = benchmark_cases()

    first = compare_v2_to_v3(cases).to_json()
    second = compare_v2_to_v3(cases).to_json()

    assert first == second
