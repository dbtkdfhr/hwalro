"""Tests for the surrogate runtime: bundle validation, modes and guarded scoring."""

from __future__ import annotations

import json

import lightgbm as lgb
import numpy as np
import pytest

import surrogate
from layout_features import FEATURE_COUNT, FEATURE_NAMES

PROXY_INDEX = FEATURE_NAMES.index("proxy_score")


def inverse_proxy_model(feature_count: int = FEATURE_COUNT) -> str:
    """A real LightGBM model whose score decreases as `proxy_score` grows.

    Only the proxy column varies, so no other feature can be split on and the
    model is a deterministic inversion of the proxy ranking - which makes a
    surrogate-driven selection trivially distinguishable from a proxy one.
    """
    rows = 200
    features = np.zeros((rows, feature_count), dtype=np.float64)
    column = min(PROXY_INDEX, feature_count - 1)
    features[:, column] = np.linspace(0.0, 1.0, rows)
    labels = -features[:, column]
    dataset = lgb.Dataset(features, label=labels, free_raw_data=False)
    booster = lgb.train(
        {
            "objective": "regression",
            "num_leaves": 7,
            "min_data_in_leaf": 5,
            "learning_rate": 0.3,
            "seed": 7,
            "deterministic": True,
            "force_row_wise": True,
            "num_threads": 1,
            "verbose": -1,
        },
        dataset,
        num_boost_round=20,
    )
    return booster.model_to_string()


def write_bundle(tmp_path, promotion_status=surrogate.PROMOTION_PROMOTED, name="bundle", model_text=None):
    return surrogate.write_bundle(
        tmp_path / name,
        model_text if model_text is not None else inverse_proxy_model(),
        f"test-{promotion_status.lower()}",
        promotion_status,
    )


def metadata_of(bundle_dir):
    return json.loads((bundle_dir / surrogate.METADATA_FILE).read_text(encoding="utf-8"))


def rewrite_metadata(bundle_dir, **changes):
    metadata = metadata_of(bundle_dir)
    metadata.update(changes)
    (bundle_dir / surrogate.METADATA_FILE).write_text(json.dumps(metadata), encoding="utf-8")


class StubBooster:
    def __init__(self, result):
        self.result = result
        self.calls = 0

    def num_feature(self):
        return FEATURE_COUNT

    def predict(self, matrix):
        self.calls += 1
        if isinstance(self.result, Exception):
            raise self.result
        # A callable lets a test say "one bad score per candidate" without hardcoding how many
        # candidates the generator happens to produce today.
        if callable(self.result):
            return self.result(matrix)
        return self.result


def stub_runtime(result, mode=surrogate.MODE_ACTIVE):
    bundle = surrogate.Bundle(
        directory=None,
        model_version="stub",
        feature_schema_version=1,
        promotion_status=surrogate.PROMOTION_PROMOTED,
        model_sha256="0" * 64,
        booster=StubBooster(result),
    )
    return surrogate.SurrogateRuntime(mode, mode, surrogate.STATUS_READY, surrogate.REASON_OK, bundle)


def test_written_bundle_loads_and_reports_its_metadata(tmp_path):
    directory = write_bundle(tmp_path)
    bundle = surrogate.load_bundle(directory)
    assert bundle.model_version == "test-promoted"
    assert bundle.promotion_status == surrogate.PROMOTION_PROMOTED
    assert bundle.promotable
    metadata = metadata_of(directory)
    assert metadata["orderedFeatureNames"] == FEATURE_NAMES
    assert metadata["featureSchemaVersion"] == surrogate.FEATURE_SCHEMA_VERSION
    assert metadata["modelSha256"] == surrogate.sha256_of(directory / surrogate.MODEL_FILE)


def test_missing_bundle_raises_bundle_missing(tmp_path):
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(tmp_path / "nope")
    assert error.value.reason == surrogate.REASON_BUNDLE_MISSING


def test_bundle_without_model_file_raises_bundle_missing(tmp_path):
    directory = write_bundle(tmp_path)
    (directory / surrogate.MODEL_FILE).unlink()
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_BUNDLE_MISSING


def test_tampered_model_file_fails_the_checksum(tmp_path):
    directory = write_bundle(tmp_path)
    model = directory / surrogate.MODEL_FILE
    model.write_text(model.read_text(encoding="utf-8") + "\n# tampered\n", encoding="utf-8")
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_CHECKSUM_MISMATCH


def test_unreadable_metadata_is_rejected(tmp_path):
    directory = write_bundle(tmp_path)
    (directory / surrogate.METADATA_FILE).write_text("{not json", encoding="utf-8")
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_METADATA_INVALID


def test_metadata_missing_required_keys_is_rejected(tmp_path):
    directory = write_bundle(tmp_path)
    metadata = metadata_of(directory)
    del metadata["promotionStatus"]
    (directory / surrogate.METADATA_FILE).write_text(json.dumps(metadata), encoding="utf-8")
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_METADATA_INVALID


def test_other_feature_schema_version_is_rejected(tmp_path):
    directory = write_bundle(tmp_path)
    rewrite_metadata(directory, featureSchemaVersion=surrogate.FEATURE_SCHEMA_VERSION + 1)
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_SCHEMA_MISMATCH


def test_reordered_feature_names_are_rejected(tmp_path):
    directory = write_bundle(tmp_path)
    rewrite_metadata(directory, orderedFeatureNames=list(reversed(FEATURE_NAMES)))
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_SCHEMA_MISMATCH


def test_model_with_a_different_feature_count_is_rejected(tmp_path):
    directory = write_bundle(tmp_path, model_text=inverse_proxy_model(feature_count=5))
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_SCHEMA_MISMATCH


def test_unparsable_model_file_is_rejected(tmp_path):
    directory = surrogate.write_bundle(
        tmp_path / "broken", "this is not a lightgbm model", "broken", surrogate.PROMOTION_PROMOTED
    )
    with pytest.raises(surrogate.BundleError) as error:
        surrogate.load_bundle(directory)
    assert error.value.reason == surrogate.REASON_MODEL_LOAD_FAILED


def test_write_bundle_rejects_an_unknown_promotion_status(tmp_path):
    with pytest.raises(ValueError):
        surrogate.write_bundle(tmp_path / "x", "model", "v", "WHATEVER")


def test_off_mode_loads_nothing(tmp_path):
    directory = write_bundle(tmp_path)
    runtime = surrogate.create(surrogate.MODE_OFF, str(directory), environment={})
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.status == surrogate.STATUS_DISABLED
    assert runtime.bundle is None
    assert not runtime.scores and not runtime.selects


def test_mode_and_bundle_fall_back_to_the_environment(tmp_path):
    directory = write_bundle(tmp_path)
    environment = {surrogate.MODE_ENV: "shadow", surrogate.BUNDLE_ENV: str(directory)}
    runtime = surrogate.create(environment=environment)
    assert runtime.effective_mode == surrogate.MODE_SHADOW
    assert runtime.scores and not runtime.selects


def test_default_mode_is_shadow_and_missing_bundle_degrades_safely():
    runtime = surrogate.create(environment={})
    assert runtime.requested_mode == surrogate.MODE_SHADOW
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.status == surrogate.STATUS_DEGRADED
    assert runtime.reason == surrogate.REASON_BUNDLE_NOT_CONFIGURED


def test_scalar_metadata_degrades_instead_of_raising(tmp_path):
    directory = write_bundle(tmp_path)
    (directory / surrogate.METADATA_FILE).write_text("42", encoding="utf-8")
    runtime = surrogate.create(surrogate.MODE_SHADOW, str(directory), environment={})
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.status == surrogate.STATUS_DEGRADED
    assert runtime.reason == surrogate.REASON_METADATA_INVALID


def test_unexpected_loader_error_degrades_instead_of_raising(monkeypatch):
    monkeypatch.setattr(surrogate, "load_bundle", lambda path: (_ for _ in ()).throw(RuntimeError("boom")))
    runtime = surrogate.create(surrogate.MODE_SHADOW, "bundle", environment={})
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.status == surrogate.STATUS_DEGRADED
    assert runtime.reason == surrogate.REASON_MODEL_LOAD_FAILED


@pytest.mark.parametrize("interrupt", [KeyboardInterrupt(), SystemExit()])
def test_process_interrupts_are_not_swallowed(monkeypatch, interrupt):
    monkeypatch.setattr(surrogate, "load_bundle", lambda path: (_ for _ in ()).throw(interrupt))
    with pytest.raises(type(interrupt)):
        surrogate.create(surrogate.MODE_SHADOW, "bundle", environment={})


def test_explicit_request_wins_over_the_environment(tmp_path):
    directory = write_bundle(tmp_path)
    environment = {surrogate.MODE_ENV: "ACTIVE", surrogate.BUNDLE_ENV: "ignored"}
    runtime = surrogate.create(surrogate.MODE_SHADOW, str(directory), environment=environment)
    assert runtime.requested_mode == surrogate.MODE_SHADOW
    assert runtime.effective_mode == surrogate.MODE_SHADOW


def test_active_with_a_promoted_bundle_selects(tmp_path):
    directory = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})
    assert runtime.effective_mode == surrogate.MODE_ACTIVE
    assert runtime.selects and runtime.scores


def test_active_never_selects_with_a_synthetic_promoted_bundle(tmp_path):
    directory = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    rewrite_metadata(directory, containsSyntheticData=True)

    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})

    assert runtime.effective_mode == surrogate.MODE_SHADOW
    assert runtime.reason == surrogate.REASON_NOT_PROMOTABLE
    assert runtime.health()["containsSyntheticData"] is True


def test_active_treats_legacy_bundle_without_provenance_as_shadow_only(tmp_path):
    directory = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    metadata = metadata_of(directory)
    del metadata["containsSyntheticData"]
    (directory / surrogate.METADATA_FILE).write_text(json.dumps(metadata), encoding="utf-8")

    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})

    assert runtime.effective_mode == surrogate.MODE_SHADOW
    assert runtime.reason == surrogate.REASON_NOT_PROMOTABLE


@pytest.mark.parametrize("invalid_provenance", [0, 1, 0.0, 1.0, None, "false"])
def test_synthetic_provenance_rejects_non_boolean_json_values(tmp_path, invalid_provenance):
    directory = write_bundle(tmp_path, surrogate.PROMOTION_PROMOTED)
    rewrite_metadata(directory, containsSyntheticData=invalid_provenance)

    with pytest.raises(surrogate.BundleError, match="containsSyntheticData must be a boolean"):
        surrogate.load_bundle(directory)


@pytest.mark.parametrize(
    "status", [surrogate.PROMOTION_SYNTHETIC_ONLY, surrogate.PROMOTION_SHADOW_ONLY]
)
def test_active_degrades_to_shadow_for_an_unpromoted_bundle(tmp_path, status):
    directory = write_bundle(tmp_path, status)
    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})
    assert runtime.effective_mode == surrogate.MODE_SHADOW
    assert runtime.reason == surrogate.REASON_NOT_PROMOTABLE
    assert runtime.scores and not runtime.selects


def test_active_without_a_bundle_path_degrades(tmp_path):
    runtime = surrogate.create(surrogate.MODE_ACTIVE, "", environment={})
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.status == surrogate.STATUS_DEGRADED
    assert runtime.reason == surrogate.REASON_BUNDLE_NOT_CONFIGURED


def test_broken_bundle_degrades_instead_of_raising(tmp_path):
    directory = write_bundle(tmp_path)
    (directory / surrogate.MODEL_FILE).write_text("tampered", encoding="utf-8")
    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})
    assert runtime.status == surrogate.STATUS_DEGRADED
    assert runtime.reason == surrogate.REASON_CHECKSUM_MISMATCH
    assert runtime.predict([[0.0] * FEATURE_COUNT]) is None


def test_unknown_mode_degrades(tmp_path):
    runtime = surrogate.create("TURBO", str(write_bundle(tmp_path)), environment={})
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.reason == surrogate.REASON_UNKNOWN_MODE


def test_predict_returns_one_score_per_row(tmp_path):
    directory = write_bundle(tmp_path)
    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})
    rows = [[0.0] * FEATURE_COUNT, [0.0] * FEATURE_COUNT]
    rows[1][PROXY_INDEX] = 1.0
    scores = runtime.predict(rows)
    assert scores is not None and len(scores) == 2
    assert scores[0] > scores[1]  # the inverse-proxy model ranks a low proxy first
    assert runtime.health()["scoredCandidates"] == 2


def test_predict_with_no_rows_is_not_a_failure(tmp_path):
    runtime = surrogate.create(surrogate.MODE_SHADOW, str(write_bundle(tmp_path)), environment={})
    assert runtime.predict([]) == []
    assert runtime.status == surrogate.STATUS_READY


def test_non_finite_features_degrade_the_whole_round():
    runtime = stub_runtime([0.0])
    rows = [[0.0] * FEATURE_COUNT, [0.0] * FEATURE_COUNT]
    rows[1][3] = float("nan")
    assert runtime.predict(rows) is None
    assert runtime.reason == surrogate.REASON_NON_FINITE_FEATURES
    assert runtime.effective_mode == surrogate.MODE_OFF
    assert runtime.bundle.booster.calls == 0


def test_wrong_feature_width_degrades_the_whole_round():
    runtime = stub_runtime([0.0])
    assert runtime.predict([[0.0] * (FEATURE_COUNT - 1)]) is None
    assert runtime.reason == surrogate.REASON_SCHEMA_MISMATCH


def test_raising_model_degrades_the_whole_round():
    runtime = stub_runtime(RuntimeError("boom"))
    assert runtime.predict([[0.0] * FEATURE_COUNT]) is None
    assert runtime.reason == surrogate.REASON_PREDICTION_FAILED
    assert not runtime.selects


def test_non_finite_prediction_degrades_the_whole_round():
    runtime = stub_runtime([0.5, float("nan")])
    assert runtime.predict([[0.0] * FEATURE_COUNT] * 2) is None
    assert runtime.reason == surrogate.REASON_NON_FINITE_PREDICTION


def test_prediction_with_the_wrong_length_degrades_the_whole_round():
    runtime = stub_runtime([0.5])
    assert runtime.predict([[0.0] * FEATURE_COUNT] * 2) is None
    assert runtime.reason == surrogate.REASON_PREDICTION_SHAPE


def test_health_reports_mode_status_and_model(tmp_path):
    directory = write_bundle(tmp_path, surrogate.PROMOTION_SYNTHETIC_ONLY)
    runtime = surrogate.create(surrogate.MODE_ACTIVE, str(directory), environment={})
    health = runtime.health()
    assert health["requestedMode"] == surrogate.MODE_ACTIVE
    assert health["effectiveMode"] == surrogate.MODE_SHADOW
    assert health["promotionStatus"] == surrogate.PROMOTION_SYNTHETIC_ONLY
    assert health["modelVersion"] == "test-synthetic_only"
    assert health["featureSchemaVersion"] == surrogate.FEATURE_SCHEMA_VERSION
