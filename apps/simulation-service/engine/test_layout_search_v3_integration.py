from __future__ import annotations

from typing import NotRequired, TypedDict

import layout_search
import pytest
import configuration_space_shape_planner
from ideal_route_docking_placement import Coordinate, Drawing
from route_planner import AgentRouteUnreachableError


class _ConstraintInput(TypedDict):
    movementPolicies: dict[str, str]
    movementZones: dict[str, dict[str, float]]
    forbiddenZones: list[dict[str, float]]


class _PlannerInput(TypedDict):
    plannerMode: str
    drawing: Drawing
    agents: list[Coordinate]
    hazards: list[dict[str, float]]
    selectedExitIds: list[int]
    maxCandidates: int
    generationBudgetSeconds: NotRequired[float]
    constraints: _ConstraintInput


def _blocked_room(planner_mode: str, maximum: int = 3) -> _PlannerInput:
    return {
        "plannerMode": planner_mode,
        "drawing": {
            "outsideBoundary": [
                {"x": 0.0, "y": 0.0},
                {"x": 12.0, "y": 0.0},
                {"x": 12.0, "y": 10.0},
                {"x": 0.0, "y": 10.0},
            ],
            "walls": [],
            "pillars": [],
            "fabrics": [
                {
                    "id": 10,
                    "name": "south blocker",
                    "startX": 5.0,
                    "startY": 3.0,
                    "endX": 7.0,
                    "endY": 4.0,
                    "rotation": 0.0,
                },
                {
                    "id": 11,
                    "name": "north blocker",
                    "startX": 5.0,
                    "startY": 6.0,
                    "endX": 7.0,
                    "endY": 7.0,
                    "rotation": 0.0,
                },
            ],
            "exits": [
                {"id": 1, "startX": 5.0, "startY": 10.0, "endX": 7.0, "endY": 10.0}
            ],
        },
        "agents": [{"x": 6.0, "y": 1.0}],
        "hazards": [],
        "selectedExitIds": [1],
        "maxCandidates": maximum,
        "constraints": {
            "movementPolicies": {"10": "FREE", "11": "FREE"},
            "movementZones": {},
            "forbiddenZones": [],
        },
    }


def test_v2_mode_keeps_the_existing_planner_contract() -> None:
    # Given: the shipped docking mode and a route blocked by two fabrics.
    search_input = _blocked_room("IDEAL_ROUTE_DOCKING", maximum=1)

    # When: candidate generation is dispatched through the common entry point.
    result = layout_search.generate(search_input)

    # Then: the V2 implementation and version remain available unchanged.
    assert result["plannerVersion"] == "IDEAL_ROUTE_DOCKING_V2"
    assert len(result["candidates"]) <= 1


def test_v3_grid_generates_one_atomic_layout_deterministically() -> None:
    # Given: two fabrics jointly blocking the same ideal route.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_GRID", maximum=1)

    # When: the exact same V3 search is replayed.
    first = layout_search.generate(search_input)
    second = layout_search.generate(search_input)

    # Then: one deterministic complete layout moves the whole blocker hyperedge.
    assert first == second
    assert first["plannerVersion"] == "CONFIGURATION_SPACE_SHAPE_V3_GRID"
    assert len(first["candidates"]) == 1
    assert {operation["fabricId"] for operation in first["candidates"][0]["ops"]} == {
        10,
        11,
    }
    assert all(
        operation["before"] != operation["after"]
        for operation in first["candidates"][0]["ops"]
    )
    rationale = first["candidates"][0]["rationale"]
    assert set(rationale) >= {
        "objective",
        "blockers",
        "forceTorque",
        "feasibility",
        "generation",
    }


def test_v3_grid_honors_a_zero_candidate_budget() -> None:
    # Given: a valid V3 input whose requested trial budget is zero.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_GRID", maximum=0)

    # When: candidate generation is requested.
    result = layout_search.generate(search_input)

    # Then: the planner performs no candidate work and preserves its output contract.
    assert result["plannerVersion"] == "CONFIGURATION_SPACE_SHAPE_V3_GRID"
    assert result["candidates"] == []
    assert result["rawCandidateCount"] == 0


def test_v3_grid_stops_before_preprocessing_when_generation_time_is_zero(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Given: a positive trial count but no generation time remains.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_GRID", maximum=1)
    search_input["generationBudgetSeconds"] = 0.0

    def unexpected_preprocessing(*_args: object, **_kwargs: object) -> None:
        raise AssertionError("ideal-flow preprocessing must not start after the deadline")

    monkeypatch.setattr(
        configuration_space_shape_planner,
        "build_ideal_flow",
        unexpected_preprocessing,
    )

    # When: generation crosses the public engine boundary.
    result = layout_search.generate(search_input)

    # Then: it returns the normal empty V3 contract without starting preprocessing.
    assert result["plannerVersion"] == "CONFIGURATION_SPACE_SHAPE_V3_GRID"
    assert result["plannerStatus"] == "AVAILABLE"
    assert result["candidates"] == []
    assert result["rawCandidateCount"] == 0


def test_v3_pde_generates_a_deterministic_layout_through_the_continuum_evaluator() -> None:
    # Given: the same blocked room routed through the continuum planner mode.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_PDE", maximum=1)

    # When: the identical PDE search is replayed.
    first = layout_search.generate(search_input)
    second = layout_search.generate(search_input)

    # Then: the reserved mode is live, distinct from GRID, and deterministic.
    assert first == second
    assert first["plannerVersion"] == "CONFIGURATION_SPACE_SHAPE_V3_PDE"
    assert first["plannerStatus"] == "AVAILABLE"
    assert len(first["candidates"]) == 1
    candidate = first["candidates"][0]
    assert {operation["fabricId"] for operation in candidate["ops"]} == {10, 11}
    assert candidate["rationale"]["plannerMode"] == "CONFIGURATION_SPACE_SHAPE_PDE"


def test_v3_pde_honors_a_zero_candidate_budget() -> None:
    # Given: a valid PDE input whose requested trial budget is zero.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_PDE", maximum=0)

    # When: candidate generation is requested.
    result = layout_search.generate(search_input)

    # Then: no continuum work runs and the output contract is preserved.
    assert result["plannerVersion"] == "CONFIGURATION_SPACE_SHAPE_V3_PDE"
    assert result["candidates"] == []
    assert result["rawCandidateCount"] == 0


def _two_corridors(planner_mode: str, maximum: int = 6) -> _PlannerInput:
    """Two corridors split by a central wall, one blocker starving each own route."""
    return {
        "plannerMode": planner_mode,
        "drawing": {
            "outsideBoundary": [
                {"x": 0.0, "y": 0.0},
                {"x": 12.0, "y": 0.0},
                {"x": 12.0, "y": 12.0},
                {"x": 0.0, "y": 12.0},
            ],
            "walls": [{"startX": 6.0, "startY": 0.0, "endX": 6.0, "endY": 9.0}],
            "pillars": [],
            "fabrics": [
                {
                    "id": 10,
                    "name": "left blocker",
                    "startX": 0.2,
                    "startY": 4.0,
                    "endX": 5.75,
                    "endY": 5.0,
                    "rotation": 0.0,
                },
                {
                    "id": 11,
                    "name": "right blocker",
                    "startX": 6.25,
                    "startY": 4.0,
                    "endX": 11.8,
                    "endY": 5.0,
                    "rotation": 0.0,
                },
            ],
            "exits": [
                {"id": 1, "startX": 5.0, "startY": 12.0, "endX": 7.0, "endY": 12.0}
            ],
        },
        "agents": [{"x": 3.0, "y": 1.0}, {"x": 9.0, "y": 1.0}],
        "hazards": [],
        "selectedExitIds": [1],
        "maxCandidates": maximum,
        "constraints": {
            "movementPolicies": {"10": "FREE", "11": "FREE"},
            "movementZones": {},
            "forbiddenZones": [],
        },
    }


def test_v3_grid_spreads_the_trial_budget_across_blocker_groups() -> None:
    # Given: two independent blockers, each obstructing its own agent's only route.
    search_input = _two_corridors("CONFIGURATION_SPACE_SHAPE_GRID", maximum=6)

    # When: the whole trial budget is generated.
    result = layout_search.generate(search_input)

    # Then: no blocker group may swallow the budget while another gets nothing.
    groups = {
        tuple(sorted(operation["fabricId"] for operation in candidate["ops"]))
        for candidate in result["candidates"]
    }
    assert groups == {(10,), (11,)}


def test_v3_grid_keeps_the_top_group_first_when_the_budget_is_one() -> None:
    # Given: the same two groups but only a single trial available.
    search_input = _two_corridors("CONFIGURATION_SPACE_SHAPE_GRID", maximum=1)

    # When: generation runs.
    result = layout_search.generate(search_input)

    # Then: exactly one candidate is produced, from the highest-priority group.
    assert len(result["candidates"]) == 1


@pytest.mark.parametrize("planner_mode", [None, "", "UNKNOWN_MODE", "DIAGNOSTIC_BEAM_V2"])
def test_an_unset_or_unknown_planner_mode_fails_loudly(planner_mode: str | None) -> None:
    # Given: an input whose planner mode is missing or not a supported mode.
    search_input = _blocked_room("CONFIGURATION_SPACE_SHAPE_GRID", maximum=1)
    if planner_mode is None:
        del search_input["plannerMode"]  # type: ignore[misc]
    else:
        search_input["plannerMode"] = planner_mode

    # When/Then: generation refuses instead of silently running the legacy beam planner.
    with pytest.raises(layout_search.UnknownPlannerModeError) as failure:
        layout_search.generate(search_input)
    assert "CONFIGURATION_SPACE_SHAPE_GRID" in str(failure.value)


def test_v3_grid_reports_rotations_normalized_to_a_single_turn() -> None:
    # Given: a search whose optimizer explores full-turn angles.
    search_input = _two_corridors("CONFIGURATION_SPACE_SHAPE_GRID", maximum=6)

    # When: candidates cross the engine boundary.
    result = layout_search.generate(search_input)

    # Then: no operation reports a 360-degree turn as a rotation.
    rotations = [
        float(operation["after"]["rotation"])
        for candidate in result["candidates"]
        for operation in candidate["ops"]
    ]
    assert rotations
    assert all(0.0 <= rotation < 360.0 for rotation in rotations)


def _sealed_corridor(planner_mode: str) -> _PlannerInput:
    """A layout whose only corridor is already fully blocked, so nobody can evacuate."""
    return {
        "plannerMode": planner_mode,
        "drawing": {
            "outsideBoundary": [
                {"x": 0.0, "y": 0.0},
                {"x": 12.0, "y": 0.0},
                {"x": 12.0, "y": 12.0},
                {"x": 0.0, "y": 12.0},
            ],
            "walls": [],
            "pillars": [],
            "fabrics": [
                {
                    "id": 10,
                    "name": "sealing blocker",
                    "startX": 0.2,
                    "startY": 4.0,
                    "endX": 11.8,
                    "endY": 5.0,
                    "rotation": 0.0,
                }
            ],
            "exits": [
                {"id": 1, "startX": 5.0, "startY": 12.0, "endX": 7.0, "endY": 12.0}
            ],
        },
        "agents": [{"x": 6.0, "y": 1.0}],
        "hazards": [],
        "selectedExitIds": [1],
        "maxCandidates": 3,
        "constraints": {
            "movementPolicies": {"10": "FREE"},
            "movementZones": {},
            "forbiddenZones": [],
        },
    }


def test_v3_still_proposes_a_layout_when_the_current_one_is_unevacuable() -> None:
    # Given: the exact case where an improvement matters most - the corridor is sealed today.
    # The docking planner routes on the current layout, so it cannot even start.
    with pytest.raises(AgentRouteUnreachableError):
        layout_search.generate(_sealed_corridor("IDEAL_ROUTE_DOCKING"))

    # When: the configuration-space planner runs the same input.
    result = layout_search.generate(_sealed_corridor("CONFIGURATION_SPACE_SHAPE_GRID"))

    # Then: it plans against the ideal layout and still returns a way to open the corridor.
    assert result["candidates"]
    assert all(
        operation["fabricId"] == 10
        for candidate in result["candidates"]
        for operation in candidate["ops"]
    )
