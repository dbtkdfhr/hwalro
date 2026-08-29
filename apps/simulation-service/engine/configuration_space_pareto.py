from __future__ import annotations

from dataclasses import dataclass, replace

from configuration_space_search_types import Layout, ObjectiveVector


@dataclass(frozen=True, slots=True)
class EvaluatedLayout:
    layout: Layout
    objectives: ObjectiveVector


def _layout_key(layout: Layout) -> tuple[tuple[int, float, float, float], ...]:
    return tuple(
        (item.fabric_id, item.pose.x, item.pose.y, item.pose.theta) for item in layout.poses
    )


def _angle_distance(first: float, second: float) -> float:
    return abs((first - second + 180.0) % 360.0 - 180.0)


def layouts_near(first: Layout, second: Layout, epsilon: float) -> bool:
    if tuple(item.fabric_id for item in first.poses) != tuple(
        item.fabric_id for item in second.poses
    ):
        return False
    return all(
        abs(left.pose.x - right.pose.x) <= epsilon
        and abs(left.pose.y - right.pose.y) <= epsilon
        and _angle_distance(left.pose.theta, right.pose.theta) <= epsilon
        for left, right in zip(first.poses, second.poses, strict=True)
    )


def _dominates(first: ObjectiveVector, second: ObjectiveVector, epsilon: float) -> bool:
    pairs = tuple(zip(first.values(), second.values(), strict=True))
    return all(left <= right + epsilon for left, right in pairs) and any(
        left < right - epsilon for left, right in pairs
    )


def _rank(entry: EvaluatedLayout) -> tuple[float, float, float, tuple[tuple[int, float, float, float], ...]]:
    objective = entry.objectives
    return (objective.balanced, objective.total, objective.average, _layout_key(entry.layout))


def _trim(entries: tuple[EvaluatedLayout, ...], maximum_size: int) -> tuple[EvaluatedLayout, ...]:
    if len(entries) <= maximum_size:
        return tuple(sorted(entries, key=_rank))
    extremes = {
        min(entries, key=lambda item: (item.objectives.total, _rank(item))),
        min(entries, key=lambda item: (item.objectives.average, _rank(item))),
        min(entries, key=lambda item: (item.objectives.balanced, _rank(item))),
    }
    selected = sorted(extremes, key=_rank)[:maximum_size]
    for entry in sorted(entries, key=_rank):
        if entry not in extremes and len(selected) < maximum_size:
            selected.append(entry)
    return tuple(selected)


@dataclass(frozen=True, slots=True)
class ParetoArchive:
    entries: tuple[EvaluatedLayout, ...]
    pose_epsilon: float
    objective_epsilon: float
    maximum_size: int

    @classmethod
    def empty(
        cls, *, pose_epsilon: float, objective_epsilon: float, maximum_size: int
    ) -> ParetoArchive:
        return cls((), pose_epsilon, objective_epsilon, maximum_size)

    def evaluated(self, layout: Layout, objectives: ObjectiveVector) -> EvaluatedLayout:
        return EvaluatedLayout(layout, objectives)

    def contains_layout(self, layout: Layout) -> bool:
        return any(layouts_near(entry.layout, layout, self.pose_epsilon) for entry in self.entries)

    def with_candidate(self, candidate: EvaluatedLayout) -> ParetoArchive:
        duplicate = next(
            (
                entry
                for entry in self.entries
                if layouts_near(entry.layout, candidate.layout, self.pose_epsilon)
            ),
            None,
        )
        remaining = self.entries
        if duplicate is not None:
            if _rank(duplicate) <= _rank(candidate):
                return self
            remaining = tuple(entry for entry in self.entries if entry != duplicate)
        if any(
            _dominates(entry.objectives, candidate.objectives, self.objective_epsilon)
            for entry in remaining
        ):
            return self
        nondominated = tuple(
            entry
            for entry in remaining
            if not _dominates(candidate.objectives, entry.objectives, self.objective_epsilon)
        )
        return replace(self, entries=_trim((*nondominated, candidate), self.maximum_size))
