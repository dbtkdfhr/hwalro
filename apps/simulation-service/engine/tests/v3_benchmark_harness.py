"""Machine-readable comparison harness for V2 and the future V3 planner."""

from __future__ import annotations

import copy
import json
from collections.abc import Callable
from dataclasses import dataclass
from typing import Final, assert_never

import layout_search
from tests.v3_benchmark_fixtures import BenchmarkCase, JsonValue

PlannerRunner = Callable[[dict[str, JsonValue]], dict[str, JsonValue]]
V2_PLANNER_MODE: Final = "IDEAL_ROUTE_DOCKING"
V3_GRID_PLANNER_MODE: Final = "CONFIGURATION_SPACE_SHAPE_GRID"
V3_GRID_PLANNER_VERSION: Final = "CONFIGURATION_SPACE_SHAPE_V3_GRID"


@dataclass(frozen=True, slots=True)
class PlannerSnapshot:
    """Planner-neutral observables only.

    Proxy scores are deliberately absent: V2 reports a recovered route cost and V3 an
    ideal-flow balanced delta, so comparing them across planners is meaningless. The
    evacuation-time gate belongs to the actual-simulation run, not to this harness.
    """

    planner_version: str
    candidate_count: int
    blocker_group_count: int
    multi_object_candidate_count: int
    changed_fabric_ids: tuple[int, ...]


@dataclass(frozen=True, slots=True)
class PlannerUnavailable:
    status: str = "UNAVAILABLE"
    reason: str = "V3 runner has not been supplied"


@dataclass(frozen=True, slots=True)
class ComparedCase:
    case_id: str
    scenario: str
    v2: PlannerSnapshot
    v3: PlannerSnapshot | PlannerUnavailable


@dataclass(frozen=True, slots=True)
class ComparisonSummary:
    case_count: int
    v3_available: bool
    v2_candidate_case_count: int


@dataclass(frozen=True, slots=True)
class ComparisonReport:
    cases: tuple[ComparedCase, ...]
    summary: ComparisonSummary

    def to_json(self) -> str:
        """Serialize the stable comparison contract for the promotion-gate job."""
        payload = {
            "cases": [
                {
                    "caseId": case.case_id,
                    "scenario": case.scenario,
                    "v2": _snapshot_payload(case.v2),
                    "v3": _snapshot_payload(case.v3),
                }
                for case in self.cases
            ],
            "summary": {
                "caseCount": self.summary.case_count,
                "v3Available": self.summary.v3_available,
                "v2CandidateCaseCount": self.summary.v2_candidate_case_count,
            },
        }
        return json.dumps(payload, ensure_ascii=False, sort_keys=True)


def _snapshot_payload(snapshot: PlannerSnapshot | PlannerUnavailable) -> dict[str, JsonValue]:
    match snapshot:
        case PlannerSnapshot():
            return {
                "status": "AVAILABLE",
                "plannerVersion": snapshot.planner_version,
                "candidateCount": snapshot.candidate_count,
                "blockerGroupCount": snapshot.blocker_group_count,
                "multiObjectCandidateCount": snapshot.multi_object_candidate_count,
                "changedFabricIds": list(snapshot.changed_fabric_ids),
            }
        case PlannerUnavailable():
            return {"status": snapshot.status, "reason": snapshot.reason}
        case unreachable:
            assert_never(unreachable)


def _planner_input(case: BenchmarkCase, planner_mode: str) -> dict[str, JsonValue]:
    payload = copy.deepcopy(case.input_data)
    payload["plannerMode"] = planner_mode
    return payload


def _candidate_group(candidate: dict[str, JsonValue]) -> tuple[int, ...]:
    operations = candidate.get("ops", [])
    assert isinstance(operations, list)
    return tuple(
        sorted(
            int(operation["fabricId"])
            for operation in operations
            if isinstance(operation, dict) and "fabricId" in operation
        )
    )


def _snapshot(result: dict[str, JsonValue]) -> PlannerSnapshot:
    candidates = result["candidates"]
    assert isinstance(candidates, list)
    groups = [_candidate_group(candidate) for candidate in candidates if isinstance(candidate, dict)]
    return PlannerSnapshot(
        planner_version=str(result["plannerVersion"]),
        candidate_count=len(candidates),
        blocker_group_count=len(set(groups)),
        multi_object_candidate_count=sum(len(group) > 1 for group in groups),
        changed_fabric_ids=tuple(sorted({fabric_id for group in groups for fabric_id in group})),
    )


def _run_v2(input_data: dict[str, JsonValue]) -> dict[str, JsonValue]:
    return layout_search.generate(input_data)


def _run_v3(runner: PlannerRunner, case: BenchmarkCase) -> PlannerSnapshot | PlannerUnavailable:
    snapshot = _snapshot(runner(_planner_input(case, V3_GRID_PLANNER_MODE)))
    if snapshot.planner_version == V3_GRID_PLANNER_VERSION:
        return snapshot
    return PlannerUnavailable(
        "CONTRACT_MISMATCH",
        f"expected {V3_GRID_PLANNER_VERSION}, received {snapshot.planner_version}",
    )


def _is_approved_v3(result: PlannerSnapshot | PlannerUnavailable) -> bool:
    match result:
        case PlannerSnapshot():
            return result.planner_version == V3_GRID_PLANNER_VERSION
        case PlannerUnavailable():
            return False
        case unreachable:
            assert_never(unreachable)


def compare_v2_to_v3(
    cases: tuple[BenchmarkCase, ...],
    v3_runner: PlannerRunner | None = None,
) -> ComparisonReport:
    """Run V2 and optionally V3 without treating an absent V3 as a pass."""
    comparisons = tuple(
        ComparedCase(
            case.case_id,
            case.scenario,
            _snapshot(_run_v2(_planner_input(case, V2_PLANNER_MODE))),
            _run_v3(v3_runner, case) if v3_runner is not None else PlannerUnavailable(),
        )
        for case in cases
    )
    return ComparisonReport(
        comparisons,
        ComparisonSummary(
            case_count=len(comparisons),
            v3_available=bool(comparisons) and all(_is_approved_v3(item.v3) for item in comparisons),
            v2_candidate_case_count=sum(item.v2.candidate_count > 0 for item in comparisons),
        ),
    )
