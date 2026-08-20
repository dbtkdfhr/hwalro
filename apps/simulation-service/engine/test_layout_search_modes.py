"""Surrogate mode behaviour of the layout search: OFF, SHADOW, ACTIVE and fallback."""

from __future__ import annotations

import json

import pytest

import layout_features
import layout_search
import surrogate
from layout_features import FEATURE_COUNT, FEATURE_NAMES
from test_layout_search import base_input, bottleneck_finding, room_drawing
from test_surrogate import StubBooster, write_bundle

ROUND_INDEX_FEATURE = FEATURE_NAMES.index("round_index")


def single_fabric_input(max_candidates: int = 3, **extra) -> dict:
    drawing = room_drawing(
        fabrics=[{"id": 1, "name": "f", "startX": 4, "startY": 5, "endX": 6, "endY": 6, "rotation": 0}]
    )
    search_input = base_input(drawing, max_candidates=max_candidates)
    search_input.update(extra)
    return search_input


def ops_keys(result: dict) -> list[str]:
    return [json.dumps(candidate["ops"], sort_keys=True) for candidate in result["candidates"]]


def install_stub_booster(monkeypatch, result):
    """Replace the loaded model with one that misbehaves, keeping bundle validation real."""
    real_load = surrogate.load_bundle

    def fake_load(directory):
        bundle = real_load(directory)
        bundle.booster = StubBooster(result)
        return bundle

    monkeypatch.setattr(surrogate, "load_bundle", fake_load)


def test_default_shadow_safely_falls_back_to_proxy_without_a_bundle():
    result = layout_search.generate(single_fabric_input(max_candidates=6))
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION
    assert result["candidates"] and result["rejected"] == []
    for candidate in result["candidates"]:
        assert candidate["selectionSource"] == layout_search.SELECTION_PROXY
        assert candidate["surrogateScore"] is None
        assert candidate["rationale"]["plannerMode"] == surrogate.MODE_OFF
    assert result["surrogateHealth"]["requestedMode"] == surrogate.MODE_SHADOW
    assert result["surrogateHealth"]["status"] == surrogate.STATUS_DEGRADED
    assert result["surrogateHealth"]["reason"] == surrogate.REASON_BUNDLE_NOT_CONFIGURED


def test_off_mode_never_touches_a_configured_bundle(tmp_path, monkeypatch):
    monkeypatch.setattr(surrogate, "load_bundle", lambda directory: pytest.fail("OFF must not load a bundle"))
    result = layout_search.generate(
        single_fabric_input(surrogateMode="OFF", surrogateBundle=str(tmp_path / "bundle"))
    )
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION
    assert result["surrogateHealth"]["effectiveMode"] == surrogate.MODE_OFF


def test_shadow_scores_every_candidate_but_selection_stays_on_the_proxy(tmp_path):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_SYNTHETIC_ONLY)
    baseline = layout_search.generate(single_fabric_input())
    shadow = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_SHADOW, surrogateBundle=str(bundle))
    )
    assert ops_keys(shadow) == ops_keys(baseline)
    assert shadow["plannerVersion"] == layout_search.PLANNER_VERSION
    assert shadow["surrogateHealth"]["effectiveMode"] == surrogate.MODE_SHADOW
    assert shadow["surrogateHealth"]["scoredCandidates"] == shadow["rawCandidateCount"]
    for candidate in shadow["candidates"]:
        assert candidate["selectionSource"] == layout_search.SELECTION_PROXY
        assert candidate["surrogateScore"] is not None
        assert candidate["rationale"]["modelVersion"] == "test-synthetic_only"


def test_active_with_a_promoted_bundle_changes_the_selection(tmp_path):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    baseline = layout_search.generate(single_fabric_input())
    active = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    # the test bundle deliberately inverts the proxy ranking
    assert set(ops_keys(active)) != set(ops_keys(baseline))
    assert active["plannerVersion"] == layout_search.SURROGATE_PLANNER_VERSION
    assert [candidate["proxyScore"] for candidate in active["candidates"]] == sorted(
        candidate["proxyScore"] for candidate in active["candidates"]
    )
    for candidate in active["candidates"]:
        assert candidate["selectionSource"] == layout_search.SELECTION_SURROGATE
        assert candidate["rationale"]["plannerMode"] == surrogate.MODE_ACTIVE


def test_exhaustive_never_claims_surrogate_selection(tmp_path):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    result = layout_search.generate(
        single_fabric_input(
            max_candidates=1,
            exhaustive=True,
            surrogateMode=surrogate.MODE_ACTIVE,
            surrogateBundle=str(bundle),
        )
    )
    assert result["plannerVersion"] == layout_search.EXHAUSTIVE_PLANNER_VERSION
    assert len(result["candidates"]) == result["rawCandidateCount"] > 1
    assert result["surrogateHealth"]["scoredCandidates"] == result["rawCandidateCount"]
    assert all(candidate["surrogateScore"] is not None for candidate in result["candidates"])
    assert all(
        candidate["selectionSource"] == layout_search.SELECTION_EXHAUSTIVE
        for candidate in result["candidates"]
    )


def test_active_on_a_synthetic_only_bundle_falls_back_to_shadow_selection(tmp_path):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_SYNTHETIC_ONLY)
    baseline = layout_search.generate(single_fabric_input())
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    assert ops_keys(result) == ops_keys(baseline)
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION
    assert result["surrogateHealth"]["effectiveMode"] == surrogate.MODE_SHADOW
    assert result["surrogateHealth"]["reason"] == surrogate.REASON_NOT_PROMOTABLE
    assert all(candidate["surrogateScore"] is not None for candidate in result["candidates"])


@pytest.mark.parametrize(
    "breakage,reason",
    [
        ("missing", surrogate.REASON_BUNDLE_MISSING),
        ("checksum", surrogate.REASON_CHECKSUM_MISMATCH),
        ("schema", surrogate.REASON_SCHEMA_MISMATCH),
    ],
)
def test_a_broken_bundle_falls_the_whole_round_back_to_the_proxy(tmp_path, breakage, reason):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    if breakage == "missing":
        (bundle / surrogate.MODEL_FILE).unlink()
    elif breakage == "checksum":
        (bundle / surrogate.MODEL_FILE).write_text("tampered", encoding="utf-8")
    else:
        metadata = json.loads((bundle / surrogate.METADATA_FILE).read_text(encoding="utf-8"))
        metadata["featureSchemaVersion"] = surrogate.FEATURE_SCHEMA_VERSION + 1
        (bundle / surrogate.METADATA_FILE).write_text(json.dumps(metadata), encoding="utf-8")

    baseline = layout_search.generate(single_fabric_input())
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    assert ops_keys(result) == ops_keys(baseline)
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION
    assert result["surrogateHealth"]["status"] == surrogate.STATUS_DEGRADED
    assert result["surrogateHealth"]["reason"] == reason
    assert all(candidate["surrogateScore"] is None for candidate in result["candidates"])
    assert all(candidate["selectionSource"] == layout_search.SELECTION_PROXY for candidate in result["candidates"])


def test_a_raising_model_falls_the_whole_round_back_to_the_proxy(tmp_path, monkeypatch):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    install_stub_booster(monkeypatch, RuntimeError("boom"))
    baseline = layout_search.generate(single_fabric_input())
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    assert ops_keys(result) == ops_keys(baseline)
    assert result["surrogateHealth"]["reason"] == surrogate.REASON_PREDICTION_FAILED
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION


def test_a_non_finite_prediction_falls_the_whole_round_back_to_the_proxy(tmp_path, monkeypatch):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    install_stub_booster(monkeypatch, lambda matrix: [float("nan")] * len(matrix))
    baseline = layout_search.generate(single_fabric_input())
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    assert ops_keys(result) == ops_keys(baseline)
    assert result["surrogateHealth"]["reason"] == surrogate.REASON_NON_FINITE_PREDICTION
    assert all(candidate["surrogateScore"] is None for candidate in result["candidates"])


def test_non_finite_features_fall_the_whole_round_back_to_the_proxy(tmp_path, monkeypatch):
    bundle = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    baseline = layout_search.generate(single_fabric_input())
    monkeypatch.setattr(layout_search, "extract_features", lambda record: [float("nan")] * FEATURE_COUNT)
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(bundle))
    )
    assert ops_keys(result) == ops_keys(baseline)
    assert result["surrogateHealth"]["reason"] == surrogate.REASON_NON_FINITE_FEATURES
    assert result["plannerVersion"] == layout_search.PLANNER_VERSION


def test_a_search_still_succeeds_when_the_surrogate_is_unusable(tmp_path):
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_ACTIVE, surrogateBundle=str(tmp_path / "gone"))
    )
    assert result["candidates"]
    assert result["surrogateHealth"]["status"] == surrogate.STATUS_DEGRADED


def test_the_feature_snapshot_matches_what_extract_features_rebuilds(tmp_path):
    result = layout_search.generate(
        single_fabric_input(surrogateMode=surrogate.MODE_SHADOW, surrogateBundle=str(write_bundle(tmp_path)))
    )
    for candidate in result["candidates"]:
        rationale = candidate["rationale"]
        snapshot = rationale["featureSnapshot"]
        context = rationale["surrogateContext"]
        assert snapshot["schemaVersion"] == layout_features.FEATURE_SCHEMA_VERSION
        assert snapshot["names"] == FEATURE_NAMES
        rebuilt = layout_features.extract_features(
            layout_features.build_record(
                context["finding"],
                context["searchMetrics"],
                candidate["ops"],
                rationale["roundIndex"],
                candidate["proxyScore"],
            )
        )
        assert rebuilt == pytest.approx(snapshot["values"])


def test_round_index_defaults_to_one_and_follows_the_java_round():
    default = layout_search.generate(single_fabric_input())
    assert default["roundIndex"] == 1
    for candidate in default["candidates"]:
        assert candidate["rationale"]["roundIndex"] == 1
        assert candidate["rationale"]["featureSnapshot"]["values"][ROUND_INDEX_FEATURE] == 1.0

    parent_op = {
        "type": "MOVE_FABRIC",
        "fabricId": 1,
        "before": {"startX": 4, "startY": 5, "endX": 6, "endY": 6, "rotation": 0},
        "after": {"startX": 3.5, "startY": 5, "endX": 5.5, "endY": 6, "rotation": 0},
    }
    second = layout_search.generate(
        single_fabric_input(
            max_candidates=4,
            round=2,
            parents=[{"candidateId": 11, "originFindingType": "BOTTLENECK", "ops": [parent_op]}],
        )
    )
    assert second["roundIndex"] == 2
    assert second["candidates"]
    for candidate in second["candidates"]:
        assert candidate["rationale"]["roundIndex"] == 2
        assert candidate["rationale"]["featureSnapshot"]["values"][ROUND_INDEX_FEATURE] == 2.0


def test_an_invalid_round_falls_back_to_one():
    for value in ("nonsense", 0, -3, None):
        result = layout_search.generate(single_fabric_input(round=value))
        assert result["roundIndex"] == 1


def test_evacuation_tail_is_diagnosed_but_consumes_no_quota():
    tail = {
        "type": "EVACUATION_TAIL",
        "severity": 0.9,
        "region": {"startX": 0.0, "startY": 0.0, "endX": 2.0, "endY": 2.0},
        "evidence": {"metric": "EXIT_DEMAND", "value": 1.0, "unit": "RATIO", "source": "TIMELINE"},
        "description": "tail",
    }
    result = layout_search.generate(
        single_fabric_input(max_candidates=4, findings=[tail, bottleneck_finding()])
    )
    assert len(result["candidates"]) == 4
    for candidate in result["candidates"]:
        assert candidate["originFindingType"] == "BOTTLENECK"
        assert candidate["rationale"]["findingIndex"] == 1
