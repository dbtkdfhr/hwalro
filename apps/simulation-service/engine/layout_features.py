"""Canonical surrogate feature extraction, shared by training and serving.

This module is the single definition of the feature vector. `layout_search`
imports it at candidate-generation time and the training scripts under `train/`
import the very same function, so a bundle can never be trained on one ordering
and served with another. `FEATURE_SCHEMA_VERSION` is stamped into every model
bundle and re-checked before the bundle is allowed to score anything.

Pure functions over a record dict - no `layout_search` import, no geometry
library. Every feature must be computable at *candidate-generation* time,
because serving builds the record before any trial has run. Two consequences:

* No trial-derived feature (termination reason, improved flag) is used. Those
  are only known after the trial the surrogate exists to avoid, so including
  them would be label leakage and would be uncomputable at inference.
* No baseline *simulation* metric is used. The engine `SearchInput` contract
  carries drawing/agents/hazards/findings only - baseline evacuation time and
  density never reach the planner. Baseline *routing* statistics
  (`search_metrics`) replace them; they are computed from the router snapshots
  `layout_search` already builds.

Missing values default to 0.0, and every returned value is finite.
"""

from __future__ import annotations

from typing import Any

FEATURE_SCHEMA_VERSION = 1

FEATURE_NAMES = [
    # finding / diagnosis context
    "region_area",
    "region_routing_cells",
    "finding_severity",
    "finding_is_bottleneck",
    "finding_is_congestion_hotspot",
    "finding_is_exit_imbalance",
    # change set shape
    "op_count",
    "move_distance_total",
    "has_rotation",
    "target_fabric_area",
    "target_fabric_rotation",
    # geometric relation between the move and the finding region
    "move_aligned_with_region_normal",
    "region_distance_before",
    "region_distance_after",
    # routing context and deltas
    "agent_count",
    "exit_count",
    "congestion_before",
    "congestion_delta_ratio",
    "corridor_delta_ratio",
    "imbalance_delta_ratio",
    # meta
    "round_index",
    "proxy_score",
]

FEATURE_COUNT = len(FEATURE_NAMES)

FINDING_TYPES = ("BOTTLENECK", "CONGESTION_HOTSPOT", "EXIT_IMBALANCE")

SEARCH_METRIC_NAMES = (
    "corridorCellsBefore",
    "corridorCellsAfter",
    "congestionBefore",
    "congestionAfter",
    "imbalanceBefore",
    "imbalanceAfter",
    "agentCount",
    "exitCount",
)


def _float(value: Any) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return 0.0
    return result if result == result and abs(result) != float("inf") else 0.0


def _bounds(region: dict | None) -> tuple[float, float, float, float] | None:
    if not isinstance(region, dict) or region.get("startX") is None:
        return None
    # A partially populated rectangle must degrade to 0.0, not raise: feature
    # extraction runs inside candidate generation, where an exception would fail
    # the whole search rather than fall back to the proxy ranker.
    return (
        _float(region.get("startX")),
        _float(region.get("startY")),
        _float(region.get("endX")),
        _float(region.get("endY")),
    )


def _center(rect: dict | None) -> tuple[float, float] | None:
    bounds = _bounds(rect)
    if bounds is None:
        return None
    return ((bounds[0] + bounds[2]) / 2.0, (bounds[1] + bounds[3]) / 2.0)


def _ratio(before: float, after: float) -> float:
    if abs(before) <= 1e-9:
        return 0.0
    return (after - before) / before


def _ops(record: dict) -> list[dict]:
    change_set = record.get("change_set") or {}
    return [op for op in (change_set.get("ops") or []) if isinstance(op, dict)]


def build_record(
    finding: dict | None,
    search_metrics: dict | None,
    ops: list[dict] | tuple[dict, ...],
    round_index: int,
    proxy_score: float,
) -> dict:
    """Assemble the record `extract_features` consumes.

    Serving and the database exporter both call this, so a training row and the
    runtime candidate it came from produce byte-identical feature vectors.
    """
    return {
        "round_index": round_index,
        "proxy_score": proxy_score,
        "finding": dict(finding or {}),
        "search_metrics": dict(search_metrics or {}),
        "change_set": {"ops": [dict(op) for op in ops]},
    }


def extract_features(record: dict) -> list[float]:
    finding = record.get("finding") or {}
    metrics = record.get("search_metrics") or {}
    ops = _ops(record)

    region_bounds = _bounds(finding.get("region"))
    if region_bounds is None:
        region_area = 0.0
        region_center = None
        region_normal_is_y = True
    else:
        width = abs(region_bounds[2] - region_bounds[0])
        height = abs(region_bounds[3] - region_bounds[1])
        region_area = width * height
        region_center = ((region_bounds[0] + region_bounds[2]) / 2.0, (region_bounds[1] + region_bounds[3]) / 2.0)
        # mirrors layout_search._region_normal: "X" when the region is taller than wide
        region_normal_is_y = width >= height

    finding_type = str(finding.get("type", ""))

    move_distance = 0.0
    rotated = 0.0
    aligned = 0.0
    target_area = 0.0
    target_rotation = 0.0
    distance_before = 0.0
    distance_after = 0.0
    if ops:
        for op in ops:
            before = op.get("before") or {}
            after = op.get("after") or {}
            before_center = _center(before)
            after_center = _center(after)
            if before_center is None or after_center is None:
                continue
            dx = after_center[0] - before_center[0]
            dy = after_center[1] - before_center[1]
            move_distance += (dx * dx + dy * dy) ** 0.5
            if abs(_float(after.get("rotation")) - _float(before.get("rotation"))) > 1e-9:
                rotated = 1.0
        first_before = ops[0].get("before") or {}
        first_after = ops[0].get("after") or {}
        bounds = _bounds(first_before)
        if bounds is not None:
            target_area = abs(bounds[2] - bounds[0]) * abs(bounds[3] - bounds[1])
        target_rotation = _float(first_after.get("rotation"))
        before_center = _center(first_before)
        after_center = _center(first_after)
        if before_center is not None and after_center is not None:
            dx = after_center[0] - before_center[0]
            dy = after_center[1] - before_center[1]
            if (abs(dy) >= abs(dx)) == region_normal_is_y and (abs(dx) + abs(dy)) > 1e-9:
                aligned = 1.0
            if region_center is not None:
                distance_before = (
                    (before_center[0] - region_center[0]) ** 2 + (before_center[1] - region_center[1]) ** 2
                ) ** 0.5
                distance_after = (
                    (after_center[0] - region_center[0]) ** 2 + (after_center[1] - region_center[1]) ** 2
                ) ** 0.5

    corridor_before = _float(metrics.get("corridorCellsBefore"))
    congestion_before = _float(metrics.get("congestionBefore"))
    imbalance_before = _float(metrics.get("imbalanceBefore"))

    return [
        region_area,
        corridor_before,
        _float(finding.get("severity")),
        1.0 if finding_type == FINDING_TYPES[0] else 0.0,
        1.0 if finding_type == FINDING_TYPES[1] else 0.0,
        1.0 if finding_type == FINDING_TYPES[2] else 0.0,
        float(len(ops)),
        move_distance,
        rotated,
        target_area,
        target_rotation,
        aligned,
        distance_before,
        distance_after,
        _float(metrics.get("agentCount")),
        _float(metrics.get("exitCount")),
        congestion_before,
        _ratio(congestion_before, _float(metrics.get("congestionAfter"))),
        _ratio(corridor_before, _float(metrics.get("corridorCellsAfter"))),
        _ratio(imbalance_before, _float(metrics.get("imbalanceAfter"))),
        _float(record.get("round_index")),
        _float(record.get("proxy_score")),
    ]


def feature_snapshot(values: list[float]) -> dict[str, Any]:
    """Serialisable record of exactly what the model was asked to score."""
    return {
        "schemaVersion": FEATURE_SCHEMA_VERSION,
        "names": list(FEATURE_NAMES),
        "values": [float(value) for value in values],
    }


assert FEATURE_COUNT == len(extract_features({})), "FEATURE_NAMES must match extract_features order"
assert FEATURE_COUNT == 22, "the trained bundles in this repository use a 22-feature schema"
