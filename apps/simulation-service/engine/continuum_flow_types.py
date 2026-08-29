from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray
from shapely.geometry.base import BaseGeometry

from configuration_space import RectangleFootprint
from configuration_space_search_types import FabricId, ObjectiveVector


@dataclass(frozen=True, slots=True)
class InvalidContinuumScenarioError(ValueError):
    field: str

    def __str__(self) -> str:
        return f"invalid continuum scenario field: {self.field}"


@dataclass(frozen=True, slots=True)
class MissingContinuumPoseError(ValueError):
    fabric_id: FabricId

    def __str__(self) -> str:
        return f"layout has no pose for continuum fabric {self.fabric_id}"


@dataclass(frozen=True, slots=True)
class ContinuumFabric:
    fabric_id: FabricId
    footprint: RectangleFootprint


@dataclass(frozen=True, slots=True)
class ContinuumScenario:
    walkable: BaseGeometry
    exits: tuple[BaseGeometry, ...]
    agents: tuple[tuple[float, float], ...]
    fabrics: tuple[ContinuumFabric, ...]
    cell_size: float

    def __post_init__(self) -> None:
        if self.walkable.is_empty:
            raise InvalidContinuumScenarioError("walkable")
        if not self.exits:
            raise InvalidContinuumScenarioError("exits")
        if not math.isfinite(self.cell_size) or self.cell_size <= 0.0:
            raise InvalidContinuumScenarioError("cell_size")


@dataclass(frozen=True, slots=True)
class ContinuumField:
    x: NDArray[np.float64]
    y: NDArray[np.float64]
    free: NDArray[np.bool_]
    travel_time: NDArray[np.float64]
    density: NDArray[np.float64]


@dataclass(frozen=True, slots=True)
class ContinuumEvaluation:
    objective: ObjectiveVector
    field: ContinuumField


@dataclass(frozen=True, slots=True)
class FieldRequest:
    scenario: ContinuumScenario
    obstacles: tuple[BaseGeometry, ...]
