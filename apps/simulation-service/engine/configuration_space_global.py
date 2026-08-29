from __future__ import annotations

import math
from dataclasses import dataclass

from configuration_space import Pose
from configuration_space_pareto import EvaluatedLayout, ParetoArchive, layouts_near
from configuration_space_search_types import (
    Layout,
    LayoutPose,
    OptimizationCallbacks,
    OptimizationRequest,
    SearchDomain,
    TerminationReason,
)


@dataclass(frozen=True, slots=True)
class SearchState:
    archive: ParetoArchive
    samples: tuple[EvaluatedLayout, ...]
    evaluations: int


@dataclass(frozen=True, slots=True)
class SearchContext:
    request: OptimizationRequest
    callbacks: OptimizationCallbacks


@dataclass(frozen=True, slots=True)
class GlobalSearchRequest:
    domain: SearchDomain
    evaluation_budget: int


@dataclass(frozen=True, slots=True)
class _Region:
    center: tuple[float, ...]
    half_sizes: tuple[float, ...]
    value: float

    @property
    def size(self) -> float:
        return math.sqrt(sum(value * value for value in self.half_sizes))


def limit_reason(context: SearchContext, evaluations: int) -> TerminationReason | None:
    if context.callbacks.clock() >= context.request.limits.deadline:
        return TerminationReason.DEADLINE
    if evaluations >= context.request.limits.evaluations:
        return TerminationReason.EVALUATION_LIMIT
    return None


def sample_layout(
    proposal: Layout, state: SearchState, context: SearchContext
) -> tuple[SearchState, EvaluatedLayout | None, TerminationReason | None]:
    reason = limit_reason(context, state.evaluations)
    if reason is not None:
        return state, None, reason
    projected = context.callbacks.project(proposal)
    if projected is None:
        return state, None, None
    duplicate = next(
        (
            item
            for item in state.samples
            if layouts_near(item.layout, projected, context.request.tolerances.pose)
        ),
        None,
    )
    if duplicate is not None:
        return state, duplicate, None
    reason = limit_reason(context, state.evaluations)
    if reason is not None:
        return state, None, reason
    measured = state.archive.evaluated(projected, context.callbacks.evaluate(projected))
    return (
        SearchState(
            state.archive.with_candidate(measured),
            (*state.samples, measured),
            state.evaluations + 1,
        ),
        measured,
        None,
    )


def _layout_at(domain: SearchDomain, coordinates: tuple[float, ...]) -> Layout:
    poses: list[LayoutPose] = []
    for index, bound in enumerate(domain.bounds):
        offset = index * 3
        x = bound.minimum_x + (bound.maximum_x - bound.minimum_x) * coordinates[offset]
        y = bound.minimum_y + (bound.maximum_y - bound.minimum_y) * coordinates[offset + 1]
        theta = bound.minimum_theta + (
            bound.maximum_theta - bound.minimum_theta
        ) * coordinates[offset + 2]
        poses.append(LayoutPose(bound.fabric_id, Pose(x, y, theta)))
    return Layout.of(tuple(poses))


def _potential_regions(regions: tuple[_Region, ...]) -> tuple[_Region, ...]:
    by_size: dict[float, _Region] = {}
    for region in regions:
        key = round(region.size, 12)
        incumbent = by_size.get(key)
        if incumbent is None or (region.value, region.center) < (
            incumbent.value,
            incumbent.center,
        ):
            by_size[key] = region
    return tuple(sorted(by_size.values(), key=lambda item: (-item.size, item.value, item.center)))


def global_search(
    state: SearchState, search: GlobalSearchRequest, context: SearchContext
) -> tuple[SearchState, TerminationReason | None]:
    dimensions = len(search.domain.bounds) * 3
    center = tuple(0.5 for _ in range(dimensions))
    state, measured, reason = sample_layout(_layout_at(search.domain, center), state, context)
    if reason is not None:
        return state, reason
    center_value = math.inf if measured is None else measured.objectives.balanced
    regions = (_Region(center, tuple(0.5 for _ in range(dimensions)), center_value),)
    expansions = 0
    while (
        state.evaluations < search.evaluation_budget
        and expansions < context.request.limits.evaluations * 3
    ):
        selected = _potential_regions(regions)
        if not selected:
            break
        remaining = [region for region in regions if region not in selected]
        progressed = False
        for region in selected:
            half_sizes = list(region.half_sizes)
            longest = max(half_sizes)
            dimensions_to_split = tuple(
                index for index, value in enumerate(half_sizes) if math.isclose(value, longest)
            )
            children: list[_Region] = []
            for dimension in dimensions_to_split:
                delta = half_sizes[dimension] * 2.0 / 3.0
                child_half_sizes = list(half_sizes)
                child_half_sizes[dimension] /= 3.0
                for direction in (-1.0, 1.0):
                    child_center = list(region.center)
                    child_center[dimension] += direction * delta
                    state, sample, reason = sample_layout(
                        _layout_at(search.domain, tuple(child_center)), state, context
                    )
                    if reason is not None:
                        return state, reason
                    value = math.inf if sample is None else sample.objectives.balanced
                    children.append(_Region(tuple(child_center), tuple(child_half_sizes), value))
                    progressed = progressed or sample is not None
                    if state.evaluations >= search.evaluation_budget:
                        break
                half_sizes[dimension] /= 3.0
                if state.evaluations >= search.evaluation_budget:
                    break
            remaining.extend(children)
            remaining.append(_Region(region.center, tuple(half_sizes), region.value))
            if state.evaluations >= search.evaluation_budget:
                break
        regions = tuple(remaining)
        expansions += 1
        if not progressed:
            break
    return state, None
