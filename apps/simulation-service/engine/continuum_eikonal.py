from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Final

import numpy as np
from numpy.typing import NDArray
from scipy.ndimage import distance_transform_edt, gaussian_filter
from shapely.geometry import Point, box

from continuum_flow_types import ContinuumField, FieldRequest
from continuum_sampling import axis_weights

OBSTACLE_SLOWDOWN: Final = 0.45
CROWD_SLOWDOWN: Final = 0.15
CONVERGENCE_RATIO: Final = 1e-7


@dataclass(frozen=True, slots=True)
class _Grid:
    x: NDArray[np.float64]
    y: NDArray[np.float64]
    free: NDArray[np.bool_]
    exits: NDArray[np.bool_]
    density: NDArray[np.float64]
    speed: NDArray[np.float64]
    cell_size: float


def _immutable(values: NDArray[np.float64] | NDArray[np.bool_]) -> None:
    values.setflags(write=False)


def _source_density(request: FieldRequest, x: NDArray[np.float64], y: NDArray[np.float64]) -> NDArray[np.float64]:
    density = np.zeros((len(y), len(x)), dtype=np.float64)
    for agent_x, agent_y in request.scenario.agents:
        for column, x_weight in axis_weights(x, agent_x):
            for row, y_weight in axis_weights(y, agent_y):
                density[row, column] += x_weight * y_weight
    sigma = max(0.5, 0.5 / request.scenario.cell_size)
    smoothed = gaussian_filter(density, sigma=sigma, mode="constant")
    total = float(smoothed.sum())
    if total > 0.0:
        smoothed *= len(request.scenario.agents) / total
    return smoothed


def _rasterize(request: FieldRequest) -> _Grid:
    min_x, min_y, max_x, max_y = request.scenario.walkable.bounds
    size = request.scenario.cell_size
    x = min_x + (np.arange(math.ceil((max_x - min_x) / size)) + 0.5) * size
    y = min_y + (np.arange(math.ceil((max_y - min_y) / size)) + 0.5) * size
    inside = np.zeros((len(y), len(x)), dtype=np.bool_)
    blocked = np.zeros_like(inside)
    exit_masks = [np.zeros_like(inside) for _ in request.scenario.exits]
    half_size = size / 2.0
    for row, y_value in enumerate(y):
        for column, x_value in enumerate(x):
            point = Point(float(x_value), float(y_value))
            inside[row, column] = request.scenario.walkable.covers(point)
            blocked[row, column] = any(obstacle.covers(point) for obstacle in request.obstacles)
            cell = box(
                float(x_value) - half_size,
                float(y_value) - half_size,
                float(x_value) + half_size,
                float(y_value) + half_size,
            )
            for exit_mask, exit_geometry in zip(exit_masks, request.scenario.exits, strict=True):
                exit_mask[row, column] = cell.intersects(exit_geometry)
    free = inside & ~blocked
    exit_cells = np.zeros_like(inside)
    for exit_mask, exit_geometry in zip(exit_masks, request.scenario.exits, strict=True):
        exit_mask &= free
        if not exit_mask.any() and free.any():
            distances = np.full(free.shape, math.inf, dtype=np.float64)
            for row, y_value in enumerate(y):
                for column, x_value in enumerate(x):
                    if free[row, column]:
                        cell = box(
                            float(x_value) - half_size,
                            float(y_value) - half_size,
                            float(x_value) + half_size,
                            float(y_value) + half_size,
                        )
                        distances[row, column] = exit_geometry.distance(cell)
            minimum = float(np.min(distances))
            exit_mask |= free & np.isclose(distances, minimum, rtol=0.0, atol=max(size * 1e-9, 1e-12))
        exit_cells |= exit_mask
    density = _source_density(request, x, y)
    density *= free
    if blocked.any():
        clearance = distance_transform_edt(~blocked, sampling=size)
        obstacle_penalty = OBSTACLE_SLOWDOWN * np.exp(-clearance / max(size, 0.5))
    else:
        obstacle_penalty = np.zeros_like(density)
    speed = np.where(free, 1.0 / (1.0 + obstacle_penalty + CROWD_SLOWDOWN * density), 0.0)
    return _Grid(x, y, free, exit_cells, density, speed, size)


def _upwind_time(horizontal: float, vertical: float, cost: float) -> float:
    if math.isinf(horizontal) and math.isinf(vertical):
        return math.inf
    if math.isinf(horizontal):
        return vertical + cost
    if math.isinf(vertical):
        return horizontal + cost
    difference = abs(horizontal - vertical)
    if difference >= cost:
        return min(horizontal, vertical) + cost
    return (horizontal + vertical + math.sqrt(2.0 * cost * cost - difference * difference)) / 2.0


def _neighbour_minimum(times: NDArray[np.float64], row: int, column: int) -> tuple[float, float]:
    height, width = times.shape
    horizontal = min(
        times[row, column - 1] if column > 0 else math.inf,
        times[row, column + 1] if column + 1 < width else math.inf,
    )
    vertical = min(
        times[row - 1, column] if row > 0 else math.inf,
        times[row + 1, column] if row + 1 < height else math.inf,
    )
    return float(horizontal), float(vertical)


def _solve(grid: _Grid) -> NDArray[np.float64]:
    times = np.full(grid.free.shape, math.inf, dtype=np.float64)
    times[grid.exits] = 0.0
    height, width = times.shape
    row_orders = (range(height), range(height - 1, -1, -1))
    column_orders = (range(width), range(width - 1, -1, -1))
    tolerance = grid.cell_size * CONVERGENCE_RATIO
    for _ in range(4 * (height + width)):
        changed = False
        for rows in row_orders:
            for columns in column_orders:
                for row in rows:
                    for column in columns:
                        if not grid.free[row, column] or grid.exits[row, column]:
                            continue
                        horizontal, vertical = _neighbour_minimum(times, row, column)
                        updated = _upwind_time(horizontal, vertical, grid.cell_size / grid.speed[row, column])
                        if updated + tolerance < times[row, column]:
                            times[row, column] = updated
                            changed = True
        if not changed:
            break
    return times


def solve_continuum_field(request: FieldRequest) -> ContinuumField:
    grid = _rasterize(request)
    travel_time = _solve(grid)
    for values in (grid.x, grid.y, grid.free, travel_time, grid.density):
        _immutable(values)
    return ContinuumField(grid.x, grid.y, grid.free, travel_time, grid.density)
