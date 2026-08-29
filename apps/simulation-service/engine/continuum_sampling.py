from __future__ import annotations

import math

import numpy as np
from numpy.typing import NDArray

from continuum_flow_types import ContinuumField


def axis_weights(axis: NDArray[np.float64], value: float) -> tuple[tuple[int, float], ...]:
    upper = int(np.searchsorted(axis, value, side="right"))
    if upper <= 0:
        return ((0, 1.0),)
    if upper >= len(axis):
        return ((len(axis) - 1, 1.0),)
    lower = upper - 1
    span = float(axis[upper] - axis[lower])
    upper_weight = (value - float(axis[lower])) / span
    return ((lower, 1.0 - upper_weight), (upper, upper_weight))


def sample_field(field: ContinuumField, point: tuple[float, float]) -> float:
    weighted = []
    for column, x_weight in axis_weights(field.x, point[0]):
        for row, y_weight in axis_weights(field.y, point[1]):
            weight = x_weight * y_weight
            if weight > 0.0 and field.free[row, column]:
                weighted.append((float(field.travel_time[row, column]), weight))
    if not weighted:
        return math.inf
    if any(math.isinf(value) and weight > 0.0 for value, weight in weighted):
        return math.inf
    total_weight = math.fsum(weight for _, weight in weighted)
    return math.fsum(value * weight for value, weight in weighted) / total_weight
