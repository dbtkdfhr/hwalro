from __future__ import annotations

from dataclasses import dataclass

import pytest

from configuration_space import Pose
from configuration_space_optimizer import optimize
from configuration_space_search_types import (
    FabricId,
    InvalidRefinementRatioError,
    Layout,
    LayoutPose,
    ObjectiveVector,
    OptimizationCallbacks,
    OptimizationRequest,
    PoseSignal,
    SearchLimits,
    StepSchedule,
    TerminationReason,
    Tolerances,
)
from configuration_space_pareto import ParetoArchive


def _layout(*xs: float) -> Layout:
    return Layout.of(
        tuple(LayoutPose(FabricId(index + 1), Pose(x, 0.0, 0.0)) for index, x in enumerate(xs))
    )


def _request(layout: Layout, *, evaluations: int = 64, deadline: float = 100.0) -> OptimizationRequest:
    return OptimizationRequest(
        initial_layout=layout,
        steps=StepSchedule(translation=1.0, rotation=30.0, minimum_scale=0.25, refinement_ratio=0.5),
        tolerances=Tolerances(pose=1e-6, objective=1e-9),
        limits=SearchLimits(evaluations=evaluations, archive_size=12, deadline=deadline),
    )


def _objective(layout: Layout) -> ObjectiveVector:
    distance = sum((item.pose.x - 1.0) ** 2 + (item.pose.theta / 30.0) ** 2 for item in layout.poses)
    return ObjectiveVector(total=distance, average=distance, balanced=distance)


def _signals(layout: Layout) -> tuple[PoseSignal, ...]:
    return tuple(PoseSignal(item.fabric_id, 1.0 - item.pose.x, 0.0, -item.pose.theta) for item in layout.poses)


def _identity(layout: Layout) -> Layout | None:
    return layout


def _fixed_clock() -> float:
    return 0.0


def test_optimizer_replays_deterministically() -> None:
    # Given: one deterministic objective, projector, signal field, and budget.
    request = _request(_layout(0.0, -1.0))
    callbacks = OptimizationCallbacks(_objective, _signals, _identity, _fixed_clock)

    # When: the same search is run twice.
    first = optimize(request, callbacks)
    second = optimize(request, callbacks)

    # Then: the complete search trace summary and archive are identical.
    assert first == second


def test_optimizer_crosses_plateau_with_one_coordinated_layout_move() -> None:
    # Given: neither fabric helps alone; both must cross x=1 atomically.
    initial = _layout(0.0, 0.0)

    def plateau(layout: Layout) -> ObjectiveVector:
        opened = all(item.pose.x >= 1.0 for item in layout.poses)
        value = 0.0 if opened else 10.0
        return ObjectiveVector(total=value, average=value, balanced=value)

    callbacks = OptimizationCallbacks(plateau, _signals, _identity, _fixed_clock)

    # When: shape signals point both blockers along the same joint descent.
    result = optimize(_request(initial), callbacks)

    # Then: the archive contains the coordinated route-opening placement.
    best = result.archive.entries[0]
    assert best.objectives.total == 0.0
    assert tuple(item.pose.x for item in best.layout.poses) == (1.0, 1.0)


def test_optimizer_crosses_a_deceptive_basin_with_global_partitioning() -> None:
    # Given: every local step is worse, while a remote feasible cell is globally best.
    def deceptive(layout: Layout) -> ObjectiveVector:
        x = layout.poses[0].pose.x
        value = 0.0 if x >= 2.5 else (1.0 if abs(x) <= 1e-9 else 2.0 + abs(x))
        return ObjectiveVector(value, value, value)

    callbacks = OptimizationCallbacks(deceptive, _signals, _identity, _fixed_clock)

    # When: the bounded deterministic search explores beyond its local descent basin.
    result = optimize(_request(_layout(0.0)), callbacks)

    # Then: a remote globally better placement survives the Pareto archive.
    assert result.archive.entries[0].objectives.balanced == 0.0
    assert result.archive.entries[0].layout.poses[0].pose.x >= 2.5


def test_pareto_archive_replaces_a_near_duplicate_stably() -> None:
    # Given: two layouts within pose epsilon and a strictly better measurement.
    archive = ParetoArchive.empty(pose_epsilon=1e-6, objective_epsilon=1e-9, maximum_size=8)
    worse = archive.evaluated(_layout(1.0), ObjectiveVector(5.0, 5.0, 5.0))
    better = archive.evaluated(_layout(1.0000005), ObjectiveVector(4.0, 4.0, 4.0))

    # When: both are inserted in discovery order.
    result = archive.with_candidate(worse).with_candidate(better)

    # Then: one canonical representative remains, and it is the better one.
    assert result.entries == (better,)


def test_pareto_archive_preserves_distinct_objective_extremes() -> None:
    # Given: candidates that separately minimize total, average, and balanced cost.
    archive = ParetoArchive.empty(pose_epsilon=1e-6, objective_epsilon=1e-9, maximum_size=8)
    candidates = (
        archive.evaluated(_layout(1.0), ObjectiveVector(1.0, 8.0, 8.0)),
        archive.evaluated(_layout(2.0), ObjectiveVector(8.0, 1.0, 8.0)),
        archive.evaluated(_layout(3.0), ObjectiveVector(8.0, 8.0, 1.0)),
    )

    # When: all three candidates enter the archive.
    result = archive
    for candidate in candidates:
        result = result.with_candidate(candidate)

    # Then: no objective-specific recommendation is discarded by scalar ranking.
    assert frozenset(result.entries) == frozenset(candidates)


def test_pareto_archive_treats_wrapped_angles_as_near_duplicates() -> None:
    # Given: the same orientation expressed on opposite sides of the angle wrap.
    archive = ParetoArchive.empty(pose_epsilon=1e-3, objective_epsilon=1e-9, maximum_size=8)
    zero = Layout.of((LayoutPose(FabricId(1), Pose(0.0, 0.0, 0.0)),))
    wrapped = Layout.of((LayoutPose(FabricId(1), Pose(0.0, 0.0, 359.9999)),))
    worse = archive.evaluated(zero, ObjectiveVector(1.0, 5.0, 5.0))
    better = archive.evaluated(wrapped, ObjectiveVector(5.0, 1.0, 4.0))

    # When: both representations enter the archive.
    result = archive.with_candidate(worse).with_candidate(better)

    # Then: circular angle distance suppresses the duplicate.
    assert result.entries == (better,)


def test_pareto_archive_never_exceeds_its_bound() -> None:
    # Given: three nondominated objective extremes and room for only one.
    archive = ParetoArchive.empty(pose_epsilon=1e-6, objective_epsilon=1e-9, maximum_size=1)
    candidates = (
        archive.evaluated(_layout(1.0), ObjectiveVector(1.0, 8.0, 8.0)),
        archive.evaluated(_layout(2.0), ObjectiveVector(8.0, 1.0, 8.0)),
        archive.evaluated(_layout(3.0), ObjectiveVector(8.0, 8.0, 1.0)),
    )

    # When: all candidates are offered to the bounded archive.
    result = archive
    for candidate in candidates:
        result = result.with_candidate(candidate)

    # Then: the configured storage bound remains strict.
    assert len(result.entries) == 1


@dataclass(slots=True)  # noqa: MUTABLE_OK -- deterministic clock advances on each observation.
class _StepClock:
    current: float = 0.0

    def __call__(self) -> float:
        value = self.current
        self.current += 1.0
        return value


def test_optimizer_stops_before_evaluating_past_monotonic_deadline() -> None:
    # Given: a clock whose third budget check reaches the deadline.
    clock = _StepClock()
    callbacks = OptimizationCallbacks(_objective, _signals, _identity, clock)

    # When: the deadline is exhausted during candidate expansion.
    result = optimize(_request(_layout(0.0), deadline=2.0), callbacks)

    # Then: termination is explicit and no post-deadline evaluation occurs.
    assert result.reason is TerminationReason.DEADLINE
    assert result.evaluations == 1


def test_optimizer_refines_after_infeasible_full_step() -> None:
    # Given: a full unit move is infeasible while a half move is feasible.
    def bounded(layout: Layout) -> Layout | None:
        return layout if all(item.pose.x <= 0.5 for item in layout.poses) else None

    callbacks = OptimizationCallbacks(_objective, _signals, bounded, _fixed_clock)

    # When: projected descent retries at its bounded refinement scale.
    result = optimize(_request(_layout(0.0)), callbacks)

    # Then: it retains the feasible half-step and never evaluates an invalid pose.
    assert any(entry.layout.poses[0].pose.x == 0.5 for entry in result.archive.entries)
    assert all(entry.layout.poses[0].pose.x <= 0.5 for entry in result.archive.entries)


def test_optimizer_uses_torque_in_joint_descent() -> None:
    # Given: rotation alone reduces the proxy objective.
    def rotation_objective(layout: Layout) -> ObjectiveVector:
        value = abs(layout.poses[0].pose.theta - 30.0)
        return ObjectiveVector(value, value, value)

    def torque(layout: Layout) -> tuple[PoseSignal, ...]:
        item = layout.poses[0]
        return (PoseSignal(item.fabric_id, 0.0, 0.0, 30.0 - item.pose.theta),)

    callbacks = OptimizationCallbacks(rotation_objective, torque, _identity, _fixed_clock)

    # When: projected descent follows the shape torque.
    result = optimize(_request(_layout(0.0)), callbacks)

    # Then: the target orientation survives Pareto selection.
    assert result.archive.entries[0].layout.poses[0].pose.theta == 30.0


def test_optimizer_honors_evaluation_limit() -> None:
    # Given: a budget smaller than the deterministic neighborhood.
    callbacks = OptimizationCallbacks(_objective, _signals, _identity, _fixed_clock)

    # When: search reaches its evaluation ceiling.
    result = optimize(_request(_layout(0.0, 0.0), evaluations=3), callbacks)

    # Then: it stops exactly at the ceiling with an explicit reason.
    assert result.evaluations == 3
    assert result.reason is TerminationReason.EVALUATION_LIMIT


def test_step_schedule_rejects_non_decreasing_refinement() -> None:
    # Given: a refinement ratio that can leave scale unchanged forever.
    invalid_ratio = 1.0

    # When: the schedule crosses the typed construction boundary.
    with pytest.raises(InvalidRefinementRatioError) as caught:
        StepSchedule(translation=1.0, rotation=30.0, minimum_scale=0.25, refinement_ratio=invalid_ratio)

    # Then: construction rejects the nonterminating schedule and identifies the value.
    assert caught.value.args == (invalid_ratio,)
