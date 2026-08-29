from __future__ import annotations

import math
from dataclasses import dataclass

from configuration_space_search_types import MonotonicClock


@dataclass(frozen=True, slots=True)
class InvalidGenerationBudgetError(ValueError):
    seconds: float

    def __str__(self) -> str:
        return f"generation budget must be finite and nonnegative: {self.seconds}"


@dataclass(frozen=True, slots=True)
class GenerationBudgetExpired(RuntimeError):
    deadline: float

    def __str__(self) -> str:
        return f"layout generation deadline reached: {self.deadline}"


@dataclass(frozen=True, slots=True)
class GenerationDeadline:
    value: float
    clock: MonotonicClock

    @classmethod
    def start(cls, seconds: float, clock: MonotonicClock) -> GenerationDeadline:
        if not math.isfinite(seconds) or seconds < 0.0:
            raise InvalidGenerationBudgetError(seconds)
        return cls(clock() + seconds, clock)

    def checkpoint(self) -> None:
        if self.clock() >= self.value:
            raise GenerationBudgetExpired(self.value)
