"""Surrogate ranking runtime: modes, versioned bundle loading, guarded scoring.

Three modes:

* ``OFF``     - nothing is loaded, nothing is scored. An explicit OFF request is
                always honored.
* ``SHADOW``  - a bundle is loaded and every candidate is scored, but selection
                stays on the proxy score. This is the safe default and is used to
                collect evidence before a model may decide anything.
* ``ACTIVE``  - selection uses the surrogate score, but only when a bundle loads
                *and* its ``promotionStatus`` is promotable. A synthetic-only
                bundle is never promotable, so a cold-start model can bootstrap
                shadow evidence and nothing else.

Any failure - missing directory, unreadable metadata, checksum mismatch, feature
schema drift, missing lightgbm, a model that will not load, a prediction that
raises or returns a non-finite number - degrades the runtime for the *whole
round*: every candidate in that round is ranked by the proxy score and the
search still completes. The surrogate never fails a search.

A bundle directory holds exactly two files:

    metadata.json   modelVersion, featureSchemaVersion, orderedFeatureNames,
                    modelSha256, promotionStatus
    model.txt       native LightGBM text model (no pickle, no joblib)
"""

from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
from typing import Any, Sequence

from layout_features import FEATURE_NAMES, FEATURE_SCHEMA_VERSION

MODE_OFF = "OFF"
MODE_SHADOW = "SHADOW"
MODE_ACTIVE = "ACTIVE"
MODES = (MODE_OFF, MODE_SHADOW, MODE_ACTIVE)

STATUS_DISABLED = "DISABLED"
STATUS_READY = "READY"
STATUS_DEGRADED = "DEGRADED"

PROMOTION_PROMOTED = "PROMOTED"
PROMOTION_SHADOW_ONLY = "SHADOW_ONLY"
PROMOTION_SYNTHETIC_ONLY = "SYNTHETIC_ONLY"
PROMOTION_STATUSES = (PROMOTION_PROMOTED, PROMOTION_SHADOW_ONLY, PROMOTION_SYNTHETIC_ONLY)
# Only a bundle validated against real trial data may decide selection.
PROMOTABLE_STATUSES = frozenset({PROMOTION_PROMOTED})

MODEL_FILE = "model.txt"
METADATA_FILE = "metadata.json"
REQUIRED_METADATA_KEYS = (
    "modelVersion",
    "featureSchemaVersion",
    "orderedFeatureNames",
    "modelSha256",
    "promotionStatus",
)

MODE_ENV = "LAYOUT_SEARCH_SURROGATE_MODE"
BUNDLE_ENV = "LAYOUT_SEARCH_SURROGATE_BUNDLE"

REASON_OK = "OK"
REASON_MODE_OFF = "MODE_OFF"
REASON_BUNDLE_NOT_CONFIGURED = "BUNDLE_NOT_CONFIGURED"
REASON_BUNDLE_MISSING = "BUNDLE_MISSING"
REASON_METADATA_INVALID = "METADATA_INVALID"
REASON_CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
REASON_SCHEMA_MISMATCH = "SCHEMA_MISMATCH"
REASON_IMPORT_FAILED = "IMPORT_FAILED"
REASON_MODEL_LOAD_FAILED = "MODEL_LOAD_FAILED"
REASON_NOT_PROMOTABLE = "NOT_PROMOTABLE"
REASON_UNKNOWN_MODE = "UNKNOWN_MODE"
REASON_NON_FINITE_FEATURES = "NON_FINITE_FEATURES"
REASON_PREDICTION_FAILED = "PREDICTION_FAILED"
REASON_NON_FINITE_PREDICTION = "NON_FINITE_PREDICTION"
REASON_PREDICTION_SHAPE = "PREDICTION_SHAPE"


class BundleError(RuntimeError):
    """A bundle could not be loaded. `reason` is one of the REASON_* codes."""

    def __init__(self, reason: str, detail: str = "") -> None:
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason = reason
        self.detail = detail


class Bundle:
    def __init__(
        self,
        directory: Path,
        model_version: str,
        feature_schema_version: int,
        promotion_status: str,
        model_sha256: str,
        booster: Any,
        contains_synthetic_data: bool = False,
    ) -> None:
        self.directory = directory
        self.model_version = model_version
        self.feature_schema_version = feature_schema_version
        self.promotion_status = promotion_status
        self.model_sha256 = model_sha256
        self.booster = booster
        self.contains_synthetic_data = contains_synthetic_data

    @property
    def promotable(self) -> bool:
        return self.promotion_status in PROMOTABLE_STATUSES and not self.contains_synthetic_data


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_bundle(
    directory: str | os.PathLike[str],
    model_text: str,
    model_version: str,
    promotion_status: str,
    extra: dict[str, Any] | None = None,
) -> Path:
    """Write a bundle directory the runtime can load. Used by training."""
    if promotion_status not in PROMOTION_STATUSES:
        raise ValueError(f"unknown promotionStatus: {promotion_status}")
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    model_path = target / MODEL_FILE
    _write_text(model_path, model_text)
    metadata: dict[str, Any] = {
        "modelVersion": model_version,
        "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
        "orderedFeatureNames": list(FEATURE_NAMES),
        "modelSha256": sha256_of(model_path),
        "promotionStatus": promotion_status,
        "containsSyntheticData": False,
    }
    metadata.update(extra or {})
    _write_text(target / METADATA_FILE, json.dumps(metadata, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    return target


def _write_text(path: Path, text: str) -> None:
    """Always LF: the checksum is over the bytes, and CRLF breaks the model parser."""
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def _read_metadata(path: Path) -> dict[str, Any]:
    try:
        metadata = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise BundleError(REASON_METADATA_INVALID, str(error)) from error
    if not isinstance(metadata, dict):
        raise BundleError(REASON_METADATA_INVALID, "metadata.json must be an object")
    missing = [key for key in REQUIRED_METADATA_KEYS if key not in metadata]
    if missing:
        raise BundleError(REASON_METADATA_INVALID, f"missing keys: {', '.join(missing)}")
    return metadata


def load_bundle(directory: str | os.PathLike[str]) -> Bundle:
    """Load and fully validate a bundle, or raise `BundleError`."""
    try:
        root = Path(directory)
    except (OSError, TypeError, ValueError) as error:
        raise BundleError(REASON_BUNDLE_MISSING, str(error)) from error
    model_path = root / MODEL_FILE
    metadata_path = root / METADATA_FILE
    try:
        if not root.is_dir():
            raise BundleError(REASON_BUNDLE_MISSING, f"not a directory: {root}")
        if not model_path.is_file() or not metadata_path.is_file():
            raise BundleError(REASON_BUNDLE_MISSING, f"expected {MODEL_FILE} and {METADATA_FILE} in {root}")
    except OSError as error:
        raise BundleError(REASON_BUNDLE_MISSING, str(error)) from error

    metadata = _read_metadata(metadata_path)

    promotion_status = str(metadata["promotionStatus"])
    if promotion_status not in PROMOTION_STATUSES:
        raise BundleError(REASON_METADATA_INVALID, f"unknown promotionStatus: {promotion_status}")
    if "containsSyntheticData" not in metadata:
        # A promoted legacy bundle without explicit provenance is safe for
        # SHADOW scoring, but may not decide candidate selection.
        contains_synthetic_data = True
    else:
        contains_synthetic_data = metadata["containsSyntheticData"]
        if not isinstance(contains_synthetic_data, bool):
            raise BundleError(REASON_METADATA_INVALID, "containsSyntheticData must be a boolean")

    try:
        schema_version = int(metadata["featureSchemaVersion"])
    except (OverflowError, TypeError, ValueError) as error:
        raise BundleError(REASON_METADATA_INVALID, "featureSchemaVersion must be an integer") from error
    if schema_version != FEATURE_SCHEMA_VERSION:
        raise BundleError(
            REASON_SCHEMA_MISMATCH, f"bundle schema {schema_version} != runtime schema {FEATURE_SCHEMA_VERSION}"
        )
    ordered_names = metadata["orderedFeatureNames"]
    if not isinstance(ordered_names, list):
        raise BundleError(REASON_METADATA_INVALID, "orderedFeatureNames must be an array")
    if ordered_names != list(FEATURE_NAMES):
        raise BundleError(REASON_SCHEMA_MISMATCH, "orderedFeatureNames differ from the runtime feature order")

    try:
        actual_sha = sha256_of(model_path)
    except OSError as error:
        raise BundleError(REASON_BUNDLE_MISSING, str(error)) from error
    if actual_sha != str(metadata["modelSha256"]).lower():
        raise BundleError(REASON_CHECKSUM_MISMATCH, f"{MODEL_FILE} does not match modelSha256")

    # A model file with CRLF line endings makes the native LightGBM parser abort
    # the process instead of raising, so it is rejected before it gets there.
    try:
        model_bytes = model_path.read_bytes()
    except OSError as error:
        raise BundleError(REASON_BUNDLE_MISSING, str(error)) from error
    if b"\r" in model_bytes:
        raise BundleError(REASON_MODEL_LOAD_FAILED, f"{MODEL_FILE} must use LF line endings")

    try:
        import lightgbm  # noqa: PLC0415
    except Exception as error:
        raise BundleError(REASON_IMPORT_FAILED, str(error)) from error

    try:
        booster = lightgbm.Booster(model_file=str(model_path))
        feature_count = int(booster.num_feature())
    except Exception as error:  # lightgbm raises bare LightGBMError / ValueError
        raise BundleError(REASON_MODEL_LOAD_FAILED, str(error)) from error
    if feature_count != len(FEATURE_NAMES):
        raise BundleError(
            REASON_SCHEMA_MISMATCH, f"model expects {feature_count} features, runtime builds {len(FEATURE_NAMES)}"
        )

    return Bundle(
        directory=root,
        model_version=str(metadata["modelVersion"]),
        feature_schema_version=schema_version,
        promotion_status=promotion_status,
        model_sha256=actual_sha,
        booster=booster,
        contains_synthetic_data=contains_synthetic_data,
    )


class SurrogateRuntime:
    """Per-run surrogate state. One instance per `layout_search.generate` call."""

    def __init__(self, requested_mode: str, effective_mode: str, status: str, reason: str, bundle: Bundle | None) -> None:
        self.requested_mode = requested_mode
        self.effective_mode = effective_mode
        self.status = status
        self.reason = reason
        self.bundle = bundle
        self.scored_candidates = 0

    @property
    def selects(self) -> bool:
        return self.effective_mode == MODE_ACTIVE and self.status == STATUS_READY

    @property
    def scores(self) -> bool:
        return self.effective_mode in (MODE_SHADOW, MODE_ACTIVE) and self.status == STATUS_READY

    def degrade(self, reason: str) -> None:
        """Fall the whole round back to the proxy ranker."""
        self.status = STATUS_DEGRADED
        self.effective_mode = MODE_OFF
        self.reason = reason
        self.scored_candidates = 0

    def predict(self, rows: Sequence[Sequence[float]]) -> list[float] | None:
        """Score every candidate of a round at once, or degrade and return None."""
        if not self.scores or self.bundle is None:
            return None
        if not rows:
            return []
        for row in rows:
            if len(row) != len(FEATURE_NAMES):
                self.degrade(REASON_SCHEMA_MISMATCH)
                return None
            for value in row:
                if not math.isfinite(value):
                    self.degrade(REASON_NON_FINITE_FEATURES)
                    return None
        try:
            import numpy  # noqa: PLC0415

            matrix = numpy.asarray(rows, dtype=numpy.float64)
            raw = self.bundle.booster.predict(matrix)
            predictions = [float(value) for value in numpy.asarray(raw, dtype=numpy.float64).reshape(-1)]
        except Exception:  # a broken model must not fail the search
            self.degrade(REASON_PREDICTION_FAILED)
            return None
        if len(predictions) != len(rows):
            self.degrade(REASON_PREDICTION_SHAPE)
            return None
        if any(not math.isfinite(value) for value in predictions):
            self.degrade(REASON_NON_FINITE_PREDICTION)
            return None
        self.scored_candidates = len(predictions)
        return predictions

    def health(self) -> dict[str, Any]:
        return {
            "requestedMode": self.requested_mode,
            "effectiveMode": self.effective_mode,
            "status": self.status,
            "reason": self.reason,
            "modelVersion": self.bundle.model_version if self.bundle else None,
            "promotionStatus": self.bundle.promotion_status if self.bundle else None,
            "containsSyntheticData": self.bundle.contains_synthetic_data if self.bundle else None,
            "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
            "scoredCandidates": self.scored_candidates,
        }

    def summary(self) -> str:
        model = self.bundle.model_version if self.bundle else "-"
        return (
            f"surrogate mode={self.requested_mode}->{self.effective_mode} "
            f"status={self.status} reason={self.reason} model={model} scored={self.scored_candidates}"
        )


def normalize_mode(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip().upper()
    if not text:
        return None
    return text


def create(
    mode: Any = None,
    bundle_path: Any = None,
    environment: dict[str, str] | None = None,
) -> SurrogateRuntime:
    """Build the runtime for one round. Never raises."""
    env = os.environ if environment is None else environment
    requested = normalize_mode(mode) or normalize_mode(env.get(MODE_ENV)) or MODE_SHADOW
    path = bundle_path if bundle_path not in (None, "") else env.get(BUNDLE_ENV, "")

    if requested == MODE_OFF:
        return SurrogateRuntime(requested, MODE_OFF, STATUS_DISABLED, REASON_MODE_OFF, None)
    if requested not in MODES:
        return SurrogateRuntime(requested, MODE_OFF, STATUS_DEGRADED, REASON_UNKNOWN_MODE, None)
    if not path:
        return SurrogateRuntime(requested, MODE_OFF, STATUS_DEGRADED, REASON_BUNDLE_NOT_CONFIGURED, None)

    try:
        bundle = load_bundle(path)
    except BundleError as error:
        return SurrogateRuntime(requested, MODE_OFF, STATUS_DEGRADED, error.reason, None)
    except Exception:
        # Third-party loaders and unusual filesystem failures must not abort a search.
        return SurrogateRuntime(requested, MODE_OFF, STATUS_DEGRADED, REASON_MODEL_LOAD_FAILED, None)

    if requested == MODE_ACTIVE and not bundle.promotable:
        # A loadable but unpromoted bundle still produces shadow evidence.
        return SurrogateRuntime(requested, MODE_SHADOW, STATUS_READY, REASON_NOT_PROMOTABLE, bundle)
    return SurrogateRuntime(requested, requested, STATUS_READY, REASON_OK, bundle)
