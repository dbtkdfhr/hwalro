from __future__ import annotations

import math
from dataclasses import dataclass

from configuration_space import Pose
from configuration_space_global import (
    GlobalSearchRequest,
    SearchContext,
    SearchState,
    global_search,
    limit_reason,
    sample_layout,
)
from configuration_space_pareto import ParetoArchive, layouts_near
from configuration_space_search_types import (
    Layout,
    LayoutPose,
    OptimizationCallbacks,
    OptimizationRequest,
    PoseBounds,
    PoseSignal,
    SearchDomain,
    TerminationReason,
)


@dataclass(frozen=True, slots=True)
class OptimizationResult:
    archive: ParetoArchive
    evaluations: int
    reason: TerminationReason


@dataclass(frozen=True, slots=True)
class _DescentStep:
    signals: tuple[PoseSignal, ...]
    scale: float


def _normalized(value: float, magnitude: float) -> float:
    return 0.0 if magnitude <= 1e-12 else value / magnitude


def _joint_descent(
    layout: Layout, step: _DescentStep, request: OptimizationRequest
) -> Layout:
    by_id = {signal.fabric_id: signal for signal in step.signals}
    maximum_force = max(
        (math.hypot(signal.force_x, signal.force_y) for signal in step.signals), default=0.0
    )
    maximum_torque = max((abs(signal.torque) for signal in step.signals), default=0.0)
    moved: list[LayoutPose] = []
    for item in layout.poses:
        signal = by_id.get(item.fabric_id)
        if signal is None:
            moved.append(item)
            continue
        pose = item.pose
        moved.append(
            LayoutPose(
                item.fabric_id,
                Pose(
                    pose.x
                    + _normalized(signal.force_x, maximum_force)
                    * request.steps.translation
                    * step.scale,
                    pose.y
                    + _normalized(signal.force_y, maximum_force)
                    * request.steps.translation
                    * step.scale,
                    pose.theta
                    + _normalized(signal.torque, maximum_torque)
                    * request.steps.rotation
                    * step.scale,
                ),
            )
        )
    return Layout.of(tuple(moved))


def _fallback_domain(request: OptimizationRequest) -> SearchDomain:
    translation_span = max(request.steps.translation * 40.0, request.tolerances.pose * 20.0)
    bounds = tuple(
        PoseBounds(
            item.fabric_id,
            item.pose.x - translation_span,
            item.pose.x + translation_span,
            item.pose.y - translation_span,
            item.pose.y + translation_span,
            item.pose.theta - 180.0,
            item.pose.theta + 180.0,
            (item.pose.theta,),
        )
        for item in request.initial_layout.poses
    )
    return SearchDomain.of(bounds)


def _theta_seed_layouts(domain: SearchDomain, initial: Layout) -> tuple[Layout, ...]:
    by_id = {item.fabric_id: item for item in initial.poses}
    seeded: list[Layout] = []
    for bound in domain.bounds:
        for theta in bound.theta_seeds:
            poses = list(initial.poses)
            index = next(
                index for index, item in enumerate(poses) if item.fabric_id == bound.fabric_id
            )
            source = by_id[bound.fabric_id]
            poses[index] = LayoutPose(bound.fabric_id, Pose(source.pose.x, source.pose.y, theta))
            seeded.append(Layout.of(tuple(poses)))
    return tuple(seeded)


def _polish(
    state: SearchState, context: SearchContext
) -> tuple[SearchState, TerminationReason | None]:
    scale = 1.0
    while scale >= context.request.steps.minimum_scale:
        anchors = (state.samples[0], *state.archive.entries[:3])
        unique_anchors = tuple(
            anchor
            for index, anchor in enumerate(anchors)
            if not any(
                layouts_near(anchor.layout, previous.layout, context.request.tolerances.pose)
                for previous in anchors[:index]
            )
        )
        for anchor in unique_anchors:
            candidate = _joint_descent(
                anchor.layout,
                _DescentStep(context.callbacks.signals(anchor.layout), scale),
                context.request,
            )
            state, _, reason = sample_layout(candidate, state, context)
            if reason is not None:
                return state, reason
        scale *= context.request.steps.refinement_ratio
    return state, None


def optimize(request: OptimizationRequest, callbacks: OptimizationCallbacks) -> OptimizationResult:
    archive = ParetoArchive.empty(
        pose_epsilon=request.tolerances.pose,
        objective_epsilon=request.tolerances.objective,
        maximum_size=request.limits.archive_size,
    )
    context = SearchContext(request, callbacks)
    reason = limit_reason(context, 0)
    if reason is not None:
        return OptimizationResult(archive, 0, reason)
    initial = callbacks.project(request.initial_layout)
    if initial is None:
        return OptimizationResult(archive, 0, TerminationReason.INFEASIBLE_START)
    reason = limit_reason(context, 0)
    if reason is not None:
        return OptimizationResult(archive, 0, reason)
    first = archive.evaluated(initial, callbacks.evaluate(initial))
    state = SearchState(archive.with_candidate(first), (first,), 1)
    domain = request.domain if request.domain is not None else _fallback_domain(request)
    for seed in (*domain.seeds, *_theta_seed_layouts(domain, initial)):
        state, _, reason = sample_layout(seed, state, context)
        if reason is not None:
            return OptimizationResult(state.archive, state.evaluations, reason)
    polish_budget = max(1, request.limits.evaluations // 3)
    global_budget = max(1, request.limits.evaluations - polish_budget)
    state, reason = global_search(state, GlobalSearchRequest(domain, global_budget), context)
    if reason is not None:
        return OptimizationResult(state.archive, state.evaluations, reason)
    state, reason = _polish(state, context)
    if reason is not None:
        return OptimizationResult(state.archive, state.evaluations, reason)
    if state.evaluations >= request.limits.evaluations:
        return OptimizationResult(state.archive, state.evaluations, TerminationReason.EVALUATION_LIMIT)
    return OptimizationResult(state.archive, state.evaluations, TerminationReason.REFINEMENT_COMPLETE)
