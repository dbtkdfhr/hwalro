"""JSON-only JuPedSim 1.4.2 subprocess runner."""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from importlib import metadata
import json
import math
import os
from pathlib import Path
import sys
import time
from typing import Any, Callable, Sequence


REQUIRED_JUPEDSIM_VERSION = "1.4.2"
ENGINE_VERSION = "1.4.2+hwalro.2"
REQUIRED_MODEL_PROFILE = "SFM_DEFAULT_V2"
REQUIRED_ROUTING_PROFILE = "HAZARD_RADIAL_EXP_V3"
DT_SECONDS = 0.01
AGENT_RADIUS_METERS = 0.3
AGENT_SPACING_METERS = AGENT_RADIUS_METERS * 2.0
MAX_SIMULATION_TIME_SECONDS = 600.0
MAX_INITIAL_RESPONSE_TIME_SECONDS = 600.0
MAX_AGENTS = 5000
FRAMES_PER_CHUNK = 20
HEATMAP_CELL_SIZE_METERS = 1.0
WAYPOINT_REACHED_DISTANCE_METERS = max(AGENT_RADIUS_METERS, 0.25 * math.sqrt(2.0))
PHASE_PROFILE_ENVIRONMENT_VARIABLE = "HWALRO_PHASE_PROFILE_PATH"
PHASE_NAMES = (
    "inputAndContextSetup",
    "routePlanning",
    "iterate",
    "agentStateCapture",
    "moveValidation",
    "targetAndExitUpdate",
    "snapshotAndSerialization",
    "recoveryScan",
    "recoveryMutation",
)
STALL_ITERATION_LIMIT = 500
STALL_MOVEMENT_EPSILON_METERS = 0.001
PROGRESS_EPSILON_METERS = 0.25
STALL_PROGRESS_SAMPLE_INTERVAL_ITERATIONS = 50
EXIT_PORTAL_COMPLETION_BAND_METERS = (
    AGENT_RADIUS_METERS + WAYPOINT_REACHED_DISTANCE_METERS
)
RECOVERY_STATIONARY_STREAK_THRESHOLD = 500
MID_ROUTE_STATIONARY_STREAK_THRESHOLD = 500
RECOVERY_INVALID_QUIET_ITERATIONS = 500
RECOVERY_GROUP_SIZE = 3
RECOVERY_TARGET_ROUNDING_DIGITS = 6
SFM_REACTION_TIME_SECONDS = 0.5
SFM_AGENT_SCALE_NEWTONS = 2000.0
SFM_FORCE_DISTANCE_METERS = 0.08
SFM_BODY_FORCE = 120000.0
SFM_FRICTION = 240000.0
MAX_AGENT_SPEED_METERS_PER_SECOND = 10.0
SFM_AGENT_SCALE_ENVIRONMENT_VARIABLE = "HWALRO_SFM_AGENT_SCALE"
SFM_FORCE_DISTANCE_ENVIRONMENT_VARIABLE = "HWALRO_SFM_FORCE_DISTANCE"
SFM_BODY_FORCE_ENVIRONMENT_VARIABLE = "HWALRO_SFM_BODY_FORCE"
SFM_FRICTION_ENVIRONMENT_VARIABLE = "HWALRO_SFM_FRICTION"
MAX_AGENT_SPEED_ENVIRONMENT_VARIABLE = "HWALRO_MAX_AGENT_SPEED_MPS"
RELOCATION_LOG_LIMIT = 20


class RunnerError(RuntimeError):
    pass


class AgentRouteUnreachableRunnerError(RunnerError):
    pass


class NoReachableSelectedExitRunnerError(RunnerError):
    pass


class NoWalkableOriginInZoneRunnerError(RunnerError):
    pass


class RecoveryMutationRollbackRunnerError(RunnerError):
    pass


class PhaseProfile:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.phases = {name: 0 for name in PHASE_NAMES}
        self.counters: dict[str, int] = {}

    def add(self, name: str, started: int) -> None:
        self.phases[name] += time.perf_counter_ns() - started

    def increment(self, name: str, value: int = 1) -> None:
        self.counters[name] = self.counters.get(name, 0) + value

    def write(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        _write_json(
            self.path,
            {
                "schemaVersion": 1,
                "phasesNanoseconds": self.phases,
                "counters": self.counters,
            },
        )


@dataclass
class AgentRouteState:
    stable_id: int
    exit_id: Any
    waypoints: tuple[tuple[float, float], ...]
    terminal_point: tuple[float, float]
    exit_start: tuple[float, float]
    exit_end: tuple[float, float]
    cursor: int = 0


@dataclass
class SimulationContext:
    simulation: Any
    router: Any
    states: dict[int, AgentRouteState]
    positions: Any = field(default_factory=dict)
    numpy: Any = None
    phase_profile: PhaseProfile | None = None
    context_index: int = 0
    agent_ids: Any = field(init=False, repr=False)
    slot_by_id: dict[int, int] = field(init=False, repr=False)
    stable_ids: Any = field(init=False, repr=False)
    active: Any = field(init=False, repr=False)
    active_count: int = field(init=False)
    next_positions: Any = field(init=False, repr=False)
    cursors: Any = field(init=False, repr=False)
    waypoint_counts: Any = field(init=False, repr=False)
    waypoint_offsets: Any = field(init=False, repr=False)
    waypoints: Any = field(init=False, repr=False)
    terminal_points: Any = field(init=False, repr=False)
    exit_starts: Any = field(init=False, repr=False)
    exit_ends: Any = field(init=False, repr=False)
    seen: Any = field(init=False, repr=False)
    cursor_changed: Any = field(init=False, repr=False)
    progress_anchors: Any = field(init=False, repr=False)
    last_progress_iterations: Any = field(init=False, repr=False)
    stationary_streak: Any = field(init=False, repr=False)
    last_invalid_iteration: Any = field(init=False, repr=False)
    recovered: Any = field(init=False, repr=False)
    readiness_any: Any = field(init=False, repr=False)
    removal_ready: Any = field(init=False, repr=False)
    post_recovery_invalid_moves: Any = field(init=False, repr=False)
    post_recovery_full_rollbacks: Any = field(init=False, repr=False)
    attempted_group_signatures: set = field(init=False, repr=False)
    recovery_events: list = field(init=False, repr=False)
    recovery_counters: dict = field(init=False, repr=False)
    start_iterations: Any = field(init=False, repr=False)
    waiting: Any = field(init=False, repr=False)
    waiting_count: int = field(init=False)
    walking_speed: float = field(init=False)
    max_agent_speed: float = field(init=False)

    def __post_init__(self) -> None:
        np = self.numpy
        if np is None:
            raise RunnerError("NumPy is unavailable for simulation context")

        engine_ids = list(self.states)
        state_values = list(self.states.values())
        count = len(engine_ids)
        self.agent_ids = np.asarray(engine_ids, dtype=np.int64)
        self.slot_by_id = {agent_id: slot for slot, agent_id in enumerate(engine_ids)}
        self.stable_ids = np.asarray([state.stable_id for state in state_values], dtype=np.int64)
        self.active = np.ones(count, dtype=bool)
        self.active_count = count

        initial_positions = self.positions
        if initial_positions:
            self.positions = np.asarray(
                [initial_positions[agent_id] for agent_id in engine_ids], dtype=float
            ).reshape(count, 2)
        else:
            self.positions = np.empty((count, 2), dtype=float)
        self.next_positions = np.empty_like(self.positions)
        self.cursors = np.asarray([state.cursor for state in state_values], dtype=np.int64)
        self.waypoint_counts = np.asarray(
            [len(state.waypoints) for state in state_values], dtype=np.int64
        )
        self.waypoint_offsets = np.empty(count + 1, dtype=np.int64)
        self.waypoint_offsets[0] = 0
        np.cumsum(self.waypoint_counts, out=self.waypoint_offsets[1:])
        self.waypoints = np.asarray(
            [point for state in state_values for point in state.waypoints], dtype=float
        ).reshape(-1, 2)
        self.terminal_points = np.asarray(
            [state.terminal_point for state in state_values], dtype=float
        ).reshape(count, 2)
        self.exit_starts = np.asarray(
            [state.exit_start for state in state_values], dtype=float
        ).reshape(count, 2)
        self.exit_ends = np.asarray(
            [state.exit_end for state in state_values], dtype=float
        ).reshape(count, 2)
        self.seen = np.zeros(count, dtype=bool)
        self.cursor_changed = np.zeros(count, dtype=bool)
        self.progress_anchors = self.positions.copy()
        self.last_progress_iterations = np.zeros(count, dtype=np.int64)
        self.stationary_streak = np.zeros(count, dtype=np.int32)
        self.last_invalid_iteration = np.full(count, -1, dtype=np.int32)
        self.recovered = np.zeros(count, dtype=bool)
        self.readiness_any = np.zeros(count, dtype=bool)
        self.removal_ready = np.zeros(count, dtype=bool)
        self.post_recovery_invalid_moves = np.zeros(count, dtype=np.int32)
        self.post_recovery_full_rollbacks = np.zeros(count, dtype=np.int32)
        self.attempted_group_signatures: set[tuple] = set()
        self.recovery_events: list[dict[str, Any]] = []
        self.recovery_counters: dict[str, int] = {}
        self.start_iterations = np.ones(count, dtype=np.int64)
        self.waiting = np.zeros(count, dtype=bool)
        self.waiting_count = 0
        self.walking_speed = 0.0
        self.max_agent_speed = float("inf")


class TimelineWriter:
    def __init__(self, output_dir: Path, total_agents: int = 0, frame_interval: float = 1.0) -> None:
        self.directory = output_dir / "timeline"
        self.directory.mkdir(parents=True, exist_ok=True)
        self.sequence = 0
        self.frames: list[dict[str, Any]] = []
        self.exit_events: dict[int, list[dict[str, Any]]] = {}
        self.total_agents = total_agents
        self.frame_interval = frame_interval
        self.frame_rate = 1.0 / frame_interval
        self.next_frame_index = 0
        self.last_time_seconds: float | None = None
        self.last_agent_count: int | None = None

    def add(self, frame: dict[str, Any]) -> dict[str, Any]:
        active = len(frame["agents"])
        frame = {
            "frameIndex": self.next_frame_index,
            "timeSeconds": frame["timeSeconds"],
            "activeAgentCount": active,
            "evacuatedCount": self.total_agents - active,
            "agents": frame["agents"],
        }
        self.next_frame_index += 1
        self.frames.append(frame)
        self.last_time_seconds = float(frame["timeSeconds"])
        self.last_agent_count = active
        if len(self.frames) == FRAMES_PER_CHUNK:
            self.flush()
        return frame

    def add_exit_event(self, time_seconds: float, stable_id: int, exit_id: Any) -> None:
        frame_index = max(0, math.ceil(time_seconds / self.frame_interval - 1e-9))
        sequence = frame_index // FRAMES_PER_CHUNK
        self.exit_events.setdefault(sequence, []).append(
            {
                "frameIndex": frame_index,
                "timeSeconds": _rounded(time_seconds),
                "agentId": stable_id,
                "exitId": exit_id,
            }
        )

    def flush(self) -> None:
        if not self.frames:
            return
        events = self.exit_events.pop(self.sequence, [])
        events.sort(key=lambda item: (item["timeSeconds"], item["agentId"]))
        _write_json(
            self.directory / f"{self.sequence:06d}.json",
            {
                "schemaVersion": 1,
                "coordinateSystem": "FLOOR_PLAN",
                "coordinateUnit": "METER",
                "frameRate": _rounded(self.frame_rate),
                "chunkSequence": self.sequence,
                "startFrame": self.frames[0]["frameIndex"],
                "endFrame": self.frames[-1]["frameIndex"],
                "frames": self.frames,
                "exitEvents": events,
            },
        )
        self.sequence += 1
        self.frames = []

    def finish(self) -> None:
        self.flush()
        if self.exit_events:
            sequences = ", ".join(str(sequence) for sequence in sorted(self.exit_events))
            raise RunnerError(f"exit events have no matching timeline frame in chunks: {sequences}")


class HeatmapWriter:
    def __init__(self, output_dir: Path, bounds, frame_interval: float = 1.0) -> None:
        self.directory = output_dir / "heatmap"
        self.directory.mkdir(parents=True, exist_ok=True)
        self.sequence = 0
        self.frames: list[dict[str, Any]] = []
        self.frame_rate = 1.0 / frame_interval
        min_x, min_y, max_x, max_y = bounds
        self.origin_x = math.floor(min_x / HEATMAP_CELL_SIZE_METERS) * HEATMAP_CELL_SIZE_METERS
        self.origin_y = math.floor(min_y / HEATMAP_CELL_SIZE_METERS) * HEATMAP_CELL_SIZE_METERS
        self.columns = max(1, math.ceil((max_x - self.origin_x) / HEATMAP_CELL_SIZE_METERS))
        self.rows = max(1, math.ceil((max_y - self.origin_y) / HEATMAP_CELL_SIZE_METERS))
        self.max_density = 0.0

    def add(self, timeline_frame: dict[str, Any]) -> None:
        counts: dict[tuple[int, int], int] = {}
        for agent in timeline_frame["agents"]:
            column = math.floor((float(agent["x"]) - self.origin_x) / HEATMAP_CELL_SIZE_METERS)
            row = math.floor((float(agent["y"]) - self.origin_y) / HEATMAP_CELL_SIZE_METERS)
            column = min(self.columns - 1, max(0, column))
            row = min(self.rows - 1, max(0, row))
            counts[(row, column)] = counts.get((row, column), 0) + 1
        cells = [
            [row, column, _rounded(count / (HEATMAP_CELL_SIZE_METERS**2))]
            for (row, column), count in sorted(counts.items())
        ]
        if counts:
            self.max_density = max(self.max_density, max(counts.values()) / (HEATMAP_CELL_SIZE_METERS**2))
        self.frames.append(
            {
                "frameIndex": timeline_frame["frameIndex"],
                "timeSeconds": timeline_frame["timeSeconds"],
                "cells": cells,
            }
        )
        if len(self.frames) == FRAMES_PER_CHUNK:
            self.flush()

    def flush(self) -> None:
        if not self.frames:
            return
        _write_json(
            self.directory / f"{self.sequence:06d}.json",
            {
                "schemaVersion": 1,
                "analysisVersion": "GRID_COUNT_V1",
                "coordinateSystem": "FLOOR_PLAN",
                "coordinateUnit": "METER",
                "densityMethod": "GRID_COUNT",
                "densityUnit": "PERSON_PER_M2",
                "frameRate": _rounded(self.frame_rate),
                "chunkSequence": self.sequence,
                "startFrame": self.frames[0]["frameIndex"],
                "endFrame": self.frames[-1]["frameIndex"],
                "grid": {
                    "originX": _rounded(self.origin_x),
                    "originY": _rounded(self.origin_y),
                    "cellSize": HEATMAP_CELL_SIZE_METERS,
                    "rows": self.rows,
                    "columns": self.columns,
                    "cellOrder": "ROW_COLUMN_VALUE",
                },
                "frames": self.frames,
            },
        )
        self.sequence += 1
        self.frames = []


def _phase_profile_from_environment() -> PhaseProfile | None:
    value = os.environ.get(PHASE_PROFILE_ENVIRONMENT_VARIABLE)
    return PhaseProfile(Path(value).expanduser().resolve()) if value else None


def _positive_float_from_environment(name: str, default: float) -> float:
    value = os.environ.get(name)
    if value is None or value == "":
        return default
    try:
        parsed = float(value)
    except ValueError as exc:
        raise RunnerError(f"environment variable {name} must be a number") from exc
    if parsed <= 0.0:
        raise RunnerError(f"environment variable {name} must be positive")
    return parsed


def _load_dependencies():
    if sys.version_info < (3, 12):
        raise RunnerError("Python 3.12 or newer is required")
    try:
        import jupedsim as jps
        import numpy as np
        import shapely
    except ModuleNotFoundError as exc:
        raise RunnerError(
            f"required engine dependency {exc.name!r} is unavailable; "
            "install apps/simulation-service/engine/requirements.txt"
        ) from exc
    try:
        installed_version = metadata.version("jupedsim")
    except metadata.PackageNotFoundError as exc:
        raise RunnerError("the jupedsim package metadata is unavailable") from exc
    if installed_version != REQUIRED_JUPEDSIM_VERSION:
        raise RunnerError(
            f"jupedsim {REQUIRED_JUPEDSIM_VERSION} is required, found {installed_version}"
        )
    position_property = getattr(getattr(jps, "Agent", None), "position", None)
    if position_property is None or position_property.fset is None:
        raise RunnerError(
            "the local JuPedSim wheel with writable Agent.position is required; "
            "follow apps/simulation-service/engine/README.md"
        )
    return jps, np, shapely, ENGINE_VERSION


def run(
    input_path: Path,
    output_dir: Path,
    *,
    validate_only: bool = False,
    route_preview: bool = False,
) -> dict[str, Any]:
    phase_profile = _phase_profile_from_environment()
    setup_started = time.perf_counter_ns() if phase_profile is not None else 0
    jps, np, _shapely, engine_version = _load_dependencies()
    try:
        from route_planner import (
            AgentRouteUnreachableError,
            GridRouter,
            _id_key,
            build_routing_geometry,
            build_walkable_geometry,
            containing_component,
            parse_exits,
            parse_exit_segments,
            parse_hazards,
            naturalize_exit_approach,
            orthogonalize_display_path,
            relocate_agent_within_bounds,
            relocate_agents,
            split_agent_components,
            usable_exit_segment,
        )
        from route_coverage import RoutePreviewZone, serialize_route_coverage, zone_branch_origins
    except ModuleNotFoundError:
        from .route_planner import (  # type: ignore[no-redef]
            AgentRouteUnreachableError,
            GridRouter,
            _id_key,
            build_routing_geometry,
            build_walkable_geometry,
            containing_component,
            parse_exits,
            parse_exit_segments,
            parse_hazards,
            naturalize_exit_approach,
            orthogonalize_display_path,
            relocate_agent_within_bounds,
            relocate_agents,
            split_agent_components,
            usable_exit_segment,
        )
        from .route_coverage import RoutePreviewZone, serialize_route_coverage, zone_branch_origins

    payload = _read_input(input_path)
    model = _object(payload.get("model"), "model")
    model_profile = model.get("modelProfile", model.get("profile"))
    routing_profile = model.get("routingProfile")
    if model_profile != REQUIRED_MODEL_PROFILE:
        raise RunnerError(f"model.modelProfile must be {REQUIRED_MODEL_PROFILE}")
    if routing_profile != REQUIRED_ROUTING_PROFILE:
        raise RunnerError(f"model.routingProfile must be {REQUIRED_ROUTING_PROFILE}")
    walking_speed = _positive_number(model.get("walkingSpeed"), "model.walkingSpeed")
    initial_response_time_std_dev = _nonnegative_number(
        model.get("initialResponseTimeStdDev", 0.0), "model.initialResponseTimeStdDev"
    )
    if initial_response_time_std_dev > MAX_INITIAL_RESPONSE_TIME_SECONDS:
        raise RunnerError("initial response time standard deviation must not exceed 600 seconds")
    sfm_agent_scale = _positive_float_from_environment(
        SFM_AGENT_SCALE_ENVIRONMENT_VARIABLE, SFM_AGENT_SCALE_NEWTONS
    )
    sfm_force_distance = _positive_float_from_environment(
        SFM_FORCE_DISTANCE_ENVIRONMENT_VARIABLE, SFM_FORCE_DISTANCE_METERS
    )
    sfm_body_force = _positive_float_from_environment(
        SFM_BODY_FORCE_ENVIRONMENT_VARIABLE, SFM_BODY_FORCE
    )
    sfm_friction = _positive_float_from_environment(
        SFM_FRICTION_ENVIRONMENT_VARIABLE, SFM_FRICTION
    )
    max_agent_speed = _positive_float_from_environment(
        MAX_AGENT_SPEED_ENVIRONMENT_VARIABLE, MAX_AGENT_SPEED_METERS_PER_SECOND
    )
    random_seed = _integer_number(payload.get("randomSeed", 0), "randomSeed")
    max_time = _positive_number(
        payload.get("maxSimulationTimeSeconds"), "maxSimulationTimeSeconds"
    )
    if max_time > MAX_SIMULATION_TIME_SECONDS:
        raise RunnerError("maxSimulationTimeSeconds must not exceed 600")
    if max_time + 1e-9 < DT_SECONDS:
        raise RunnerError("maxSimulationTimeSeconds must be at least 0.01")
    frame_interval = _positive_number(
        payload.get("frameIntervalSeconds"), "frameIntervalSeconds"
    )
    frame_steps = round(frame_interval / DT_SECONDS)
    if frame_steps < 1 or not math.isclose(
        frame_steps * DT_SECONDS, frame_interval, abs_tol=1e-9
    ):
        raise RunnerError("frameIntervalSeconds must be a multiple of 0.01")

    drawing = _object(payload.get("drawing"), "drawing")
    agents = _agents(payload.get("agents"))
    if not agents:
        raise RunnerError("at least one agent is required")
    if len(agents) > MAX_AGENTS:
        raise RunnerError("agents must not contain more than 5000 entries")
    initial_response_times = _sample_initial_response_times(
        np,
        len(agents),
        initial_response_time_std_dev,
        random_seed,
    )
    hazards_value = payload.get("hazards")
    if not isinstance(hazards_value, list):
        raise RunnerError("hazards must be an array")
    selected_exit_ids = payload.get("selectedExitIds")
    if not isinstance(selected_exit_ids, list):
        raise RunnerError("selectedExitIds must be an array")
    recovery_value = payload.get("recoveryDetectorEnabled", False)
    if not isinstance(recovery_value, bool):
        raise RunnerError("recoveryDetectorEnabled must be a boolean")
    recovery_enabled = recovery_value
    route_origin_bounds = (
        _route_origin_bounds(payload.get("routeOriginBounds")) if route_preview else None
    )
    route_preview_zones = (
        _route_preview_zones(payload.get("routePreviewZones")) if route_preview else ()
    )

    try:
        hazards = parse_hazards(hazards_value)
        exits = parse_exits(drawing, selected_exit_ids)
        for exit_ in exits:
            usable_exit_segment(exit_, AGENT_RADIUS_METERS)
        walkable = build_walkable_geometry(drawing)
        routing_area = build_routing_geometry(drawing, AGENT_RADIUS_METERS)
        original_agents = tuple(agents)
        if route_origin_bounds is not None:
            if len(agents) != 1:
                raise RunnerError("routeOriginBounds requires exactly one agent")
            relocated = relocate_agent_within_bounds(
                routing_area, agents[0], route_origin_bounds
            )
            if relocated is None:
                output_dir.mkdir(parents=True, exist_ok=True)
                _write_json(
                    output_dir / "error.json",
                    {
                        "schemaVersion": 1,
                        "code": "NO_WALKABLE_ORIGIN_IN_ZONE",
                        "agentId": 1,
                    },
                )
                raise NoWalkableOriginInZoneRunnerError(
                    "NO_WALKABLE_ORIGIN_IN_ZONE"
                )
            agents = (relocated,)
            relocations = ()
        else:
            agents, relocations = relocate_agents(routing_area, agents)
        groups = split_agent_components(routing_area, agents)
    except ValueError as exc:
        raise RunnerError(str(exc)) from exc

    if relocations:
        print(f"relocated {len(relocations)} agent(s) out of obstacles", file=sys.stderr)
        for item in relocations[:RELOCATION_LOG_LIMIT]:
            print(
                f"  agent {item.index + 1}: {item.origin} -> {item.destination}",
                file=sys.stderr,
            )
        if len(relocations) > RELOCATION_LOG_LIMIT:
            print(
                f"  ... and {len(relocations) - RELOCATION_LOG_LIMIT} more",
                file=sys.stderr,
            )

    contexts: list[SimulationContext] = []
    routing_groups: list[tuple[Any, Any, Any]] = []
    failed_components: list[list[tuple[int, tuple[float, float]]]] = []
    for component, indexed_agents in groups:
        try:
            physical_component = containing_component(walkable, component)
            router = GridRouter(
                component,
                hazards,
                exits,
                physical_walkable=physical_component,
                exit_clearance=AGENT_RADIUS_METERS,
            )
        except ValueError as exc:
            if str(exc) != "no selected exit is reachable from this walkable component":
                raise RunnerError(str(exc)) from exc
            failed_components.append(list(indexed_agents))
            continue
        routing_groups.append((physical_component, router, indexed_agents))

    if failed_components and (not route_preview_zones or not routing_groups):
        output_dir.mkdir(parents=True, exist_ok=True)
        affected_indexes = sorted(
            index for indexed_agents in failed_components for index, _position in indexed_agents
        )
        _write_json(
            output_dir / "error.json",
            {
                "schemaVersion": 1,
                "code": "NO_REACHABLE_SELECTED_EXIT",
                "affectedAgentCount": len(affected_indexes),
                "representativeAgentIds": [index + 1 for index in affected_indexes[:3]],
                "componentCount": len(failed_components),
                "selectedExitIds": selected_exit_ids,
                "reason": "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT",
            },
        )
        raise NoReachableSelectedExitRunnerError("NO_REACHABLE_SELECTED_EXIT")

    if phase_profile is not None:
        phase_profile.add("inputAndContextSetup", setup_started)
        route_started = time.perf_counter_ns()
    routes_by_index = {}
    indexed_routers = sorted(
        (index, position, router)
        for _physical_component, router, indexed_agents in routing_groups
        for index, position in indexed_agents
    )
    router_by_index = {index: router for index, _position, router in indexed_routers}
    for index, position, router in indexed_routers:
        try:
            routes_by_index[index] = router.plan(position)
        except AgentRouteUnreachableError:
            try:
                recommendation = router.recommended_position(
                    start=position,
                    other_agents=tuple(
                        other_position
                        for other_index, other_position in enumerate(agents)
                        if other_index != index
                    ),
                    exit_segments=parse_exit_segments(drawing),
                    agent_spacing=AGENT_SPACING_METERS,
                )
            except ValueError as exc:
                raise RunnerError(str(exc)) from exc
            output_dir.mkdir(parents=True, exist_ok=True)
            _write_json(
                output_dir / "error.json",
                {
                    "schemaVersion": 1,
                    "code": "AGENT_ROUTE_UNREACHABLE",
                    "agentId": index + 1,
                    "recommendedPosition": (
                        {
                            "x": _rounded(recommendation[0]),
                            "y": _rounded(recommendation[1]),
                        }
                        if recommendation is not None
                        else None
                    ),
                },
            )
            # ponytail: report only the first original-order failure; add a
            # multi-agent diagnostic only if correction/retry telemetry demands it.
            raise AgentRouteUnreachableRunnerError(
                "AGENT_ROUTE_UNREACHABLE"
            ) from None

    if phase_profile is not None:
        phase_profile.add("routePlanning", route_started)
        setup_started = time.perf_counter_ns()
    # Every failure mode worth reporting has already been handled above: geometry
    # validation, relocation out of obstacles, NO_REACHABLE_SELECTED_EXIT and
    # AGENT_ROUTE_UNREACHABLE. Emitting the planned routes here reuses all of it
    # and stops short of building JuPedSim contexts, which is the expensive part.
    if route_preview:
        output_dir.mkdir(parents=True, exist_ok=True)
        serialized_routes = []
        for index in sorted(routes_by_index):
            route = routes_by_index[index]
            serialized = _serialize_preview_route(
                route,
                agents[index],
                original_agents[index],
                router_by_index[index],
                orthogonalize_display_path,
            )
            serialized["agentId"] = index + 1
            serialized_routes.append(serialized)
        routers = [item[1] for item in routing_groups]
        coverage = serialize_route_coverage(routers, exits)
        zone_routes = _zone_preview_routes(
            route_preview_zones,
            coverage,
            routing_area,
            routers,
            exits,
            hazards,
            relocate_agent_within_bounds,
            GridRouter,
            zone_branch_origins,
            orthogonalize_display_path,
            naturalize_exit_approach,
            AgentRouteUnreachableError,
            _id_key,
        )
        _write_json(
            output_dir / "routes.json",
            {
                "schemaVersion": 1,
                "routes": serialized_routes,
                "coverage": coverage,
                "zoneRoutes": zone_routes,
            },
        )
        if phase_profile is not None:
            phase_profile.write()
        return {"routePreview": True, "routeCount": len(routes_by_index)}
    if validate_only:
        if phase_profile is not None:
            phase_profile.write()
        return {"valid": True}
    for physical_component, router, indexed_agents in routing_groups:
        contexts.append(
            _create_context(
                jps,
                np,
                _shapely,
                physical_component,
                router,
                indexed_agents,
                [routes_by_index[index] for index, _position in indexed_agents],
                walking_speed,
                [initial_response_times[index] for index, _position in indexed_agents],
                sfm_agent_scale,
                sfm_force_distance,
                sfm_body_force,
                sfm_friction,
                max_agent_speed,
                phase_profile,
                len(contexts),
            )
        )

    if phase_profile is not None:
        phase_profile.add("inputAndContextSetup", setup_started)
    output_dir.mkdir(parents=True, exist_ok=True)
    timeline = TimelineWriter(output_dir, len(agents), frame_interval)
    heatmap = HeatmapWriter(output_dir, walkable.bounds, frame_interval)
    snapshot_started = time.perf_counter_ns() if phase_profile is not None else 0
    heatmap.add(timeline.add(_snapshot(contexts, 0.0)))
    if phase_profile is not None:
        phase_profile.add("snapshotAndSerialization", snapshot_started)
        phase_profile.increment("snapshots")
    evacuation_times: list[float] = []
    maximum_iterations = int(math.floor(max_time / DT_SECONDS + 1e-9))
    for context in contexts:
        for agent_id in _initialize_targets(context):
            state = context.states.pop(agent_id)
            evacuation_times.append(0.0)
            timeline.add_exit_event(0.0, state.stable_id, state.exit_id)

    iteration = 0
    stalled_iterations = 0
    while any(context.states for context in contexts) and iteration < maximum_iterations:
        iteration += 1
        elapsed = iteration * DT_SECONDS
        moved = 0.0
        evacuated_this_iteration = False
        recovery_applied_this_iteration = False
        for context in contexts:
            if not context.states:
                continue
            for agent_id in _advance_context(context, iteration):
                state = context.states.pop(agent_id, None)
                if state is not None:
                    evacuated_this_iteration = True
                    evacuation_times.append(elapsed)
                    timeline.add_exit_event(elapsed, state.stable_id, state.exit_id)
            active_slots = context.numpy.flatnonzero(context.active & ~context.waiting)
            if active_slots.size:
                delta = context.positions[active_slots] - context.next_positions[active_slots]
                step_sizes = context.numpy.hypot(delta[:, 0], delta[:, 1])
                moved += float(step_sizes.sum())
                context.stationary_streak[active_slots] = context.numpy.where(
                    step_sizes < STALL_MOVEMENT_EPSILON_METERS,
                    context.stationary_streak[active_slots] + 1,
                    0,
                )
                if iteration % STALL_PROGRESS_SAMPLE_INTERVAL_ITERATIONS == 0:
                    _update_progress(context, active_slots, context.positions, iteration)
                    if recovery_enabled:
                        recovery_applied_this_iteration = (
                            recovery_applied_this_iteration
                            or recovery_scan(context, iteration)
                            or mid_route_recovery_scan(context, iteration)
                        )
        waiting_for_start = any(context.waiting_count > 0 for context in contexts)
        if evacuated_this_iteration or recovery_applied_this_iteration or waiting_for_start:
            stalled_iterations = 0
        elif moved < STALL_MOVEMENT_EPSILON_METERS:
            stalled_iterations += 1
            if stalled_iterations >= STALL_ITERATION_LIMIT:
                break
        else:
            stalled_iterations = 0
        if iteration % frame_steps == 0:
            snapshot_started = time.perf_counter_ns() if phase_profile is not None else 0
            heatmap.add(timeline.add(_snapshot(contexts, elapsed)))
            if phase_profile is not None:
                phase_profile.add("snapshotAndSerialization", snapshot_started)
                phase_profile.increment("snapshots")

    remaining = sum(len(context.states) for context in contexts)
    evacuated = len(agents) - remaining
    if iteration == 0 and remaining > 0:
        raise RunnerError("simulation produced a stalled result without running any iteration")
    if remaining == 0:
        termination_reason = "ALL_EVACUATED"
    elif iteration >= maximum_iterations:
        termination_reason = "MAX_DURATION"
    else:
        termination_reason = "STALLED"
    simulation_duration = min(iteration * DT_SECONDS, max_time)
    serialization_started = time.perf_counter_ns() if phase_profile is not None else 0
    if (
        timeline.last_time_seconds is None
        or not math.isclose(timeline.last_time_seconds, simulation_duration, abs_tol=1e-9)
        or timeline.last_agent_count != remaining
    ):
        heatmap.add(timeline.add(_snapshot(contexts, simulation_duration)))
        if phase_profile is not None:
            phase_profile.increment("snapshots")
    timeline.finish()
    heatmap.flush()
    result = {
        "engineVersion": engine_version,
        "terminationReason": termination_reason,
        "simulationDurationSeconds": _rounded(simulation_duration),
        "evacuatedPeople": evacuated,
        "remainingPeople": remaining,
        "totalEvacuationTimeSeconds": (
            _rounded(max(evacuation_times)) if termination_reason == "ALL_EVACUATED" else None
        ),
        "averageEvacuationTimeSeconds": (
            _rounded(sum(evacuation_times) / len(evacuation_times)) if evacuation_times else None
        ),
        "frameIntervalSeconds": _rounded(frame_interval),
        "timelineChunkCount": timeline.sequence,
        "heatmapChunkCount": heatmap.sequence,
        "maxDensity": _rounded(heatmap.max_density),
    }
    if termination_reason == "STALLED":
        result["terminationDetail"] = _termination_detail(contexts, iteration)
    if recovery_enabled:
        result["recoverySummary"] = _build_recovery_summary(contexts)
    _write_json(output_dir / "result.json", result)
    if phase_profile is not None:
        phase_profile.add("snapshotAndSerialization", serialization_started)
        phase_profile.write()
    return result


def _update_progress(context: SimulationContext, active_slots, positions, iteration: int) -> None:
    np = context.numpy
    delta = positions[active_slots] - context.progress_anchors[active_slots]
    progressed = np.hypot(delta[:, 0], delta[:, 1]) >= PROGRESS_EPSILON_METERS
    progressed_slots = active_slots[progressed]
    if progressed_slots.size:
        context.progress_anchors[progressed_slots] = positions[progressed_slots]
        context.last_progress_iterations[progressed_slots] = iteration


def _termination_detail(contexts: Sequence[SimulationContext], iteration: int) -> dict[str, Any]:
    remaining_states: list[tuple[AgentRouteState, int, tuple[float, float]]] = []
    for context in contexts:
        if not context.states:
            continue
        for slot in context.numpy.flatnonzero(context.active):
            stable_id = int(context.stable_ids[slot])
            state = context.states[int(context.agent_ids[slot])]
            position = (
                float(context.positions[slot, 0]),
                float(context.positions[slot, 1]),
            )
            remaining_states.append(
                (
                    state,
                    int(context.last_progress_iterations[slot]),
                    position,
                )
            )
    reason_counts: dict[str, int] = {}
    stagnant = True
    for state, last_progress_iteration, position in remaining_states:
        if iteration - last_progress_iteration < STALL_ITERATION_LIMIT:
            stagnant = False
        if state.cursor + 1 == len(state.waypoints) and _exit_segment_distance(
            position, state.exit_start, state.exit_end
        ) <= EXIT_PORTAL_COMPLETION_BAND_METERS:
            reason = "EXIT_PORTAL_STUCK"
        else:
            reason = "ROUTE_FOLLOWING_STUCK"
        reason_counts[reason] = reason_counts.get(reason, 0) + 1
    remaining_states.sort(key=lambda item: item[0].stable_id)
    return {
        "schemaVersion": 1,
        "globalReason": "GLOBAL_STALLED" if stagnant else "PARTIAL_STALLED",
        "remainingPeople": len(remaining_states),
        "reasonCounts": reason_counts,
        "representativeAgents": [
            state.stable_id for state, _last_progress, _position in remaining_states[:5]
        ],
    }


def _exit_segment_distance(
    position: tuple[float, float],
    segment_start: tuple[float, float],
    segment_end: tuple[float, float],
) -> float:
    ax, ay = segment_start
    bx, by = segment_end
    vx, vy = bx - ax, by - ay
    length_squared = vx * vx + vy * vy
    if length_squared <= 1e-12:
        return math.dist(position, segment_start)
    projection = max(
        0.0,
        min(1.0, ((position[0] - ax) * vx + (position[1] - ay) * vy) / length_squared),
    )
    return math.dist(position, (ax + projection * vx, ay + projection * vy))


def _add_agent_with_spacing(
    simulation,
    jps,
    np,
    shapely,
    physical_component,
    placed_positions: list[tuple[float, float]],
    journey_id,
    stage_id,
    position: tuple[float, float],
    target,
    walking_speed: float,
    reaction_time: float,
    agent_scale: float,
    force_distance: float,
) -> int:
    min_spacing = AGENT_RADIUS_METERS * 2.0
    candidates = [position]
    for ring in range(1, 5):
        radius = min_spacing * ring
        for step in range(12):
            angle = 2.0 * math.pi * step / 12
            candidates.append((position[0] + radius * math.cos(angle), position[1] + radius * math.sin(angle)))
    last_error: Exception | None = None
    for candidate in candidates:
        if not physical_component.covers(shapely.Point(candidate)):
            continue
        if any(math.dist(candidate, other) < min_spacing - 1e-6 for other in placed_positions):
            continue
        direction = np.asarray(target, dtype=float) - np.asarray(candidate, dtype=float)
        norm = float(np.linalg.norm(direction))
        orientation = (1.0, 0.0) if norm <= 1e-12 else tuple((direction / norm).tolist())
        try:
            agent_id = simulation.add_agent(
                jps.SocialForceModelAgentParameters(
                    position=candidate,
                    orientation=orientation,
                    journey_id=journey_id,
                    stage_id=stage_id,
                    desired_speed=walking_speed,
                    reaction_time=reaction_time,
                    agent_scale=agent_scale,
                    force_distance=force_distance,
                    radius=AGENT_RADIUS_METERS,
                )
            )
        except Exception as exc:
            last_error = exc
            continue
        placed_positions.append(candidate)
        return agent_id
    raise RunnerError(f"could not add agent: {last_error}") from last_error


def _create_context(
    jps,
    np,
    shapely,
    physical_component,
    router,
    indexed_agents,
    routes,
    walking_speed: float,
    initial_response_times,
    sfm_agent_scale: float,
    sfm_force_distance: float,
    sfm_body_force: float,
    sfm_friction: float,
    max_agent_speed: float,
    phase_profile: PhaseProfile | None = None,
    context_index: int = 0,
) -> SimulationContext:
    simulation = jps.Simulation(
        model=jps.SocialForceModel(body_force=sfm_body_force, friction=sfm_friction),
        geometry=physical_component,
        dt=DT_SECONDS,
    )
    stage_id = simulation.add_direct_steering_stage()
    journey_id = simulation.add_journey(jps.JourneyDescription([stage_id]))
    states: dict[int, AgentRouteState] = {}
    positions: dict[int, tuple[float, float]] = {}
    start_iterations: dict[int, int] = {}
    placed_positions: list[tuple[float, float]] = []
    for (index, position), route, response_time in zip(
        indexed_agents, routes, initial_response_times, strict=True
    ):
        start_iteration = _start_iteration(float(response_time))
        target = route.waypoints[1] if len(route.waypoints) > 1 else route.waypoints[0]
        try:
            agent_id = _add_agent_with_spacing(
                simulation,
                jps,
                np,
                shapely,
                physical_component,
                placed_positions,
                journey_id,
                stage_id,
                position,
                target,
                walking_speed if start_iteration == 1 else 0.0,
                SFM_REACTION_TIME_SECONDS,
                sfm_agent_scale,
                sfm_force_distance,
            )
        except RunnerError as exc:
            raise RunnerError(f"could not add agent {index}: {exc}") from exc
        states[agent_id] = AgentRouteState(
            stable_id=index + 1,
            exit_id=route.exit_id,
            waypoints=route.waypoints,
            terminal_point=route.terminal_point,
            exit_start=route.exit_start,
            exit_end=route.exit_end,
            cursor=1 if len(route.waypoints) > 1 else 0,
        )
        positions[agent_id] = position
        start_iterations[agent_id] = start_iteration
    context = SimulationContext(
        simulation,
        router,
        states,
        positions,
        np,
        phase_profile,
        context_index,
    )
    context.start_iterations = np.asarray(
        [start_iterations[int(agent_id)] for agent_id in context.agent_ids], dtype=np.int64
    )
    context.waiting = context.start_iterations > 1
    context.waiting_count = int(np.count_nonzero(context.waiting))
    context.walking_speed = walking_speed
    context.max_agent_speed = max_agent_speed
    return context


def _active_agents_and_positions(context: SimulationContext, positions):
    context.seen.fill(False)
    available = {}
    for agent in context.simulation.agents():
        agent_id = agent.id
        slot = context.slot_by_id.get(agent_id)
        if slot is None or not context.active[slot]:
            continue
        available[agent_id] = agent
        context.seen[slot] = True
        position = agent.position
        positions[slot, 0] = float(position[0])
        positions[slot, 1] = float(position[1])
    missing_slots = context.numpy.flatnonzero(context.active & ~context.seen)
    if missing_slots.size:
        missing = sorted(context.agent_ids[missing_slots].tolist())
        raise RunnerError(f"active JuPedSim agents are missing: {missing}")
    return available


def _initialize_targets(context: SimulationContext) -> list[int]:
    profile = context.phase_profile
    capture_started = time.perf_counter_ns() if profile is not None else 0
    agents = _active_agents_and_positions(context, context.positions)
    if profile is not None:
        profile.add("agentStateCapture", capture_started)
        target_started = time.perf_counter_ns()
    evacuated = _update_targets(context, agents, context.positions)
    if profile is not None:
        profile.add("targetAndExitUpdate", target_started)
    return evacuated


def _activate_due_agents(context: SimulationContext, iteration: int) -> None:
    if context.waiting_count == 0:
        return
    due_slots = context.numpy.flatnonzero(
        context.active & context.waiting & (context.start_iterations <= iteration)
    )
    for slot in due_slots:
        agent_id = int(context.agent_ids[slot])
        context.simulation.agent(agent_id).model.desired_speed = context.walking_speed
    if due_slots.size:
        context.waiting[due_slots] = False
        context.waiting_count -= int(due_slots.size)


def _advance_context(context: SimulationContext, iteration: int) -> list[int]:
    previous = context.positions
    profile = context.phase_profile
    iterate_started = time.perf_counter_ns() if profile is not None else 0
    _activate_due_agents(context, iteration)
    try:
        context.simulation.iterate()
    except Exception as exc:
        raise RunnerError(f"JuPedSim iteration {iteration} failed: {exc}") from exc
    if profile is not None:
        profile.add("iterate", iterate_started)
        profile.increment("contextIterations")
        profile.increment("agentSteps", context.active_count)
        capture_started = time.perf_counter_ns()
    current = context.next_positions
    agents = _active_agents_and_positions(context, current)
    if profile is not None:
        profile.add("agentStateCapture", capture_started)
        movement_started = time.perf_counter_ns()
    crossed = _detect_exit_crossings(context, previous, current)
    _clamp_overspeed_moves(context, previous, agents, current, crossed)
    _rollback_invalid_moves(context, previous, agents, current, crossed, iteration)
    if profile is not None:
        profile.add("moveValidation", movement_started)
        target_started = time.perf_counter_ns()
    evacuated = _update_targets(context, agents, current, crossed)
    context.positions, context.next_positions = current, previous
    if profile is not None:
        profile.add("targetAndExitUpdate", target_started)
    return evacuated


def _detect_exit_crossings(context: SimulationContext, previous, current):
    np = context.numpy
    crossed = np.zeros(len(context.agent_ids), dtype=bool)
    final_slots = np.flatnonzero(
        context.active & ~context.waiting & (context.cursors + 1 == context.waypoint_counts)
    )
    if final_slots.size:
        crossed[final_slots] = context.router.crossed_exits(
            previous[final_slots],
            current[final_slots],
            context.exit_starts[final_slots],
            context.exit_ends[final_slots],
        )
    return crossed


def _clamp_overspeed_moves(
    context: SimulationContext, previous, agents, current, crossed
) -> None:
    np = context.numpy
    max_step = context.max_agent_speed * DT_SECONDS
    slots = np.flatnonzero(context.active & ~crossed)
    if not slots.size:
        return
    delta = current[slots] - previous[slots]
    step = np.hypot(delta[:, 0], delta[:, 1])
    overspeed = step > max_step
    overspeed_slots = slots[overspeed]
    if not overspeed_slots.size:
        return
    overspeed_delta = delta[overspeed]
    scale = max_step / step[overspeed]
    corrected = previous[overspeed_slots] + overspeed_delta * scale[:, None]
    current[overspeed_slots] = corrected
    for offset, slot in enumerate(overspeed_slots.tolist()):
        agent = agents[int(context.agent_ids[slot])]
        agent.position = (float(corrected[offset, 0]), float(corrected[offset, 1]))
        agent.model.velocity = (
            float(overspeed_delta[offset, 0] * scale[offset] / DT_SECONDS),
            float(overspeed_delta[offset, 1] * scale[offset] / DT_SECONDS),
        )


def _rollback_invalid_moves(
    context: SimulationContext, previous, agents, current, crossed, iteration: int
) -> None:
    np = context.numpy
    active_slots = np.flatnonzero(context.active)
    active_slots = active_slots[~crossed[active_slots]]
    valid = np.asarray(
        context.router.valid_moves(previous[active_slots], current[active_slots]), dtype=bool
    )
    invalid_slots = active_slots[~valid]
    for slot in invalid_slots:
        context.last_invalid_iteration[slot] = iteration
        agent_id = int(context.agent_ids[slot])
        agent = agents[agent_id]
        end = tuple(current[slot])
        corrected = context.router.clamp_to_walkable(end)
        full_rollback = not context.router.can_connect(tuple(previous[slot]), corrected)
        if full_rollback:
            corrected = tuple(previous[slot])
        current[slot] = corrected
        agent.position = corrected
        agent.model.velocity = _slide_velocity(end, corrected, agent.model.velocity)
        if context.recovered[slot]:
            context.post_recovery_invalid_moves[slot] += 1
            if full_rollback:
                context.post_recovery_full_rollbacks[slot] += 1


def _slide_velocity(end, corrected, velocity) -> tuple[float, float]:
    dx = end[0] - corrected[0]
    dy = end[1] - corrected[1]
    length = math.hypot(dx, dy)
    if length <= 1e-12:
        return (0.0, 0.0)
    nx, ny = dx / length, dy / length
    dot = velocity[0] * nx + velocity[1] * ny
    return (
        float(velocity[0] - dot * nx),
        float(velocity[1] - dot * ny),
    )


def _update_targets(context: SimulationContext, agents, positions, crossed=None) -> list[int]:
    np = context.numpy
    context.cursor_changed.fill(False)
    context.readiness_any.fill(False)
    context.removal_ready.fill(False)

    while True:
        route_slots = np.flatnonzero(
            context.active & ~context.waiting & (context.cursors + 1 < context.waypoint_counts)
        )
        near, passed = _waypoint_masks(context, route_slots, positions)
        advance = near.copy()
        connector_indices = np.flatnonzero(~near & passed)
        connector_slots = route_slots[connector_indices]
        if connector_slots.size:
            connected = context.router.can_connect_many(
                positions[connector_slots],
                context.waypoints[
                    context.waypoint_offsets[connector_slots]
                    + context.cursors[connector_slots]
                    + 1
                ],
            )
            advance[connector_indices] = connected
        advanced_slots = route_slots[advance]
        if not advanced_slots.size:
            break
        context.cursors[advanced_slots] += 1
        context.cursor_changed[advanced_slots] = True
        for slot in advanced_slots:
            agent_id = int(context.agent_ids[slot])
            context.states[agent_id].cursor = int(context.cursors[slot])

    final_slots = np.flatnonzero(
        context.active & ~context.waiting & (context.cursors + 1 == context.waypoint_counts)
    )
    final_near, _final_passed = _waypoint_masks(context, final_slots, positions)
    ready = (
        np.zeros(len(final_slots), dtype=bool)
        if crossed is None
        else crossed[final_slots].copy()
    )
    near_indices = np.flatnonzero(~ready & final_near)
    near_slots = final_slots[near_indices]
    if near_slots.size:
        ready[near_indices] = context.router.can_reach_exits(
            positions[near_slots], context.terminal_points[near_slots]
        )

    reach_indices = np.flatnonzero(~ready)
    reach_slots = final_slots[reach_indices]
    if reach_slots.size:
        ready[reach_indices] = context.router.reached_exits(
            positions[reach_slots],
            context.exit_starts[reach_slots],
            context.exit_ends[reach_slots],
        )

    context.removal_ready[final_slots] = ready
    context.readiness_any[final_slots] = final_near | ready

    eligible = context.active & ~context.waiting
    if not eligible.any():
        proximity_slots = np.empty(0, dtype=np.int64)
        proximity_labels = np.empty(0, dtype=np.int32)
    else:
        proximity_slots = None if eligible.all() else np.flatnonzero(eligible)
        proximity_positions = positions if proximity_slots is None else positions[proximity_slots]
        proximity_labels = context.router.reached_selected_exit_labels(proximity_positions)
    proximity_matches = np.flatnonzero(proximity_labels >= 0)
    reached_exit_by_slot = {}
    if proximity_matches.size:
        matched_slots = (
            proximity_matches if proximity_slots is None else proximity_slots[proximity_matches]
        )
        context.removal_ready[matched_slots] = True
        context.readiness_any[matched_slots] = True
        reached_exit_by_slot = {
            int(slot): context.router.exits[int(proximity_labels[index])].id
            for slot, index in zip(matched_slots, proximity_matches)
        }

    evacuated = []
    ready_slots = np.flatnonzero(context.removal_ready)
    for slot in ready_slots:
        agent_id = int(context.agent_ids[slot])
        if context.simulation.mark_agent_for_removal(agent_id):
            reached_exit_id = reached_exit_by_slot.get(int(slot))
            if reached_exit_id is not None:
                context.states[agent_id].exit_id = reached_exit_id
            context.active[slot] = False
            context.active_count -= 1
            evacuated.append(agent_id)

    target_mask = (
        context.active.copy()
        if crossed is None
        else context.active & context.cursor_changed
    )
    target_mask[ready_slots] = False
    for slot in np.flatnonzero(target_mask):
        agent_id = int(context.agent_ids[slot])
        state = context.states[agent_id]
        agents[agent_id].target = state.waypoints[state.cursor]
    return evacuated


def _exit_axis_projection(context: SimulationContext):
    def key(slot: int):
        state = context.states[int(context.agent_ids[slot])]
        start = state.exit_start
        end = state.exit_end
        vx, vy = end[0] - start[0], end[1] - start[1]
        length_squared = vx * vx + vy * vy
        position = context.positions[slot]
        projection = (
            (position[0] - start[0]) * vx + (position[1] - start[1]) * vy
        ) / length_squared
        return (projection, int(context.stable_ids[slot]))

    return key


def _point_list(point) -> list[float]:
    return [_rounded(point[0]), _rounded(point[1])]


def _recovery_event(
    context: SimulationContext,
    iteration: int,
    exit_label: int,
    exit_id: Any,
    target: tuple[float, float],
    stable_ids: Sequence[int],
) -> dict[str, Any]:
    return {
        "timeSeconds": _rounded(iteration * DT_SECONDS),
        "iteration": int(iteration),
        "contextIndex": int(context.context_index),
        "exitId": exit_id,
        "exitLabel": int(exit_label),
        "target": [_rounded(target[0]), _rounded(target[1])],
        "stableIds": [int(stable_id) for stable_id in stable_ids],
        "oldTargets": [],
        "newTargets": [],
        "newApproaches": [],
        "seedNodeIds": [],
        "status": "RECOVERED",
        "postRecoveryInvalidMoves": 0,
        "postRecoveryFullRollbacks": 0,
    }


def _record_recovery_infeasible(
    context: SimulationContext,
    event: dict[str, Any],
    reason_code: str,
    seeds: Sequence | None = None,
) -> bool:
    event["status"] = "RECOVERY_INFEASIBLE"
    event["reasonCode"] = reason_code
    if seeds:
        event["newTargets"] = [_point_list(seed[1]) for seed in seeds]
        event["newApproaches"] = [_point_list(seed[2]) for seed in seeds]
        event["seedNodeIds"] = [int(seed[0]) for seed in seeds]
    context.recovery_counters["infeasible_scans"] = (
        context.recovery_counters.get("infeasible_scans", 0) + 1
    )
    return False


def _apply_recovery_mutation(
    context: SimulationContext,
    slots: Sequence[int],
    seeds: Sequence,
    event: dict[str, Any],
) -> bool:
    profile = context.phase_profile
    mutation_started = time.perf_counter_ns() if profile is not None else 0
    snapshot = []
    try:
        for slot, seed in zip(slots, seeds, strict=True):
            agent_id = int(context.agent_ids[slot])
            snapshot.append(
                {
                    "slot": int(slot),
                    "agent_id": agent_id,
                    "state": context.states[agent_id],
                    "old_waypoints": context.states[agent_id].waypoints,
                    "old_terminal_point": context.states[agent_id].terminal_point,
                    "old_target": context.simulation.agent(agent_id).target,
                }
            )
        for entry, seed in zip(snapshot, seeds, strict=True):
            new_approach = seed[2]
            new_target = seed[1]
            entry["state"].waypoints = entry["state"].waypoints[:-1] + (new_approach,)
            context.waypoints[
                context.waypoint_offsets[entry["slot"]] + context.cursors[entry["slot"]]
            ] = new_approach
            entry["state"].terminal_point = new_target
            context.terminal_points[entry["slot"]] = new_target
            context.simulation.agent(entry["agent_id"]).target = new_approach
    except Exception as exc:
        restore_failures = 0
        for entry in reversed(snapshot):
            try:
                entry["state"].waypoints = entry["old_waypoints"]
                entry["state"].terminal_point = entry["old_terminal_point"]
                context.waypoints[
                    context.waypoint_offsets[entry["slot"]] + context.cursors[entry["slot"]]
                ] = entry["old_waypoints"][-1]
                context.terminal_points[entry["slot"]] = entry["old_terminal_point"]
                context.simulation.agent(entry["agent_id"]).target = entry["old_target"]
            except Exception:
                restore_failures += 1
        if restore_failures:
            raise RecoveryMutationRollbackRunnerError(
                f"recovery mutation rollback failed for {restore_failures} agent state(s)"
            ) from exc
        event["status"] = "RECOVERY_MUTATION_FAILED"
        event["reasonCode"] = "MUTATION_APPLY_FAILED"
        event["exceptionClass"] = type(exc).__name__
        return False
    finally:
        if profile is not None:
            profile.add("recoveryMutation", mutation_started)
    recovered_slots = context.numpy.asarray(
        [entry["slot"] for entry in snapshot], dtype=context.numpy.int64
    )
    context.recovered[recovered_slots] = True
    event["newTargets"] = [_point_list(seed[1]) for seed in seeds]
    event["newApproaches"] = [_point_list(seed[2]) for seed in seeds]
    event["seedNodeIds"] = [int(seed[0]) for seed in seeds]
    context.recovery_counters["recovered_groups"] = (
        context.recovery_counters.get("recovered_groups", 0) + 1
    )
    if profile is not None:
        profile.increment("recoveredGroups")
    return True


def recovery_scan(context: SimulationContext, iteration: int) -> bool:
    try:
        from route_planner import _id_key
    except ModuleNotFoundError:
        from .route_planner import _id_key  # type: ignore[no-redef]
    np = context.numpy
    profile = context.phase_profile
    scan_started = time.perf_counter_ns() if profile is not None else 0
    try:
        context.recovery_counters["scans"] = context.recovery_counters.get("scans", 0) + 1
        if profile is not None:
            profile.increment("recoveryScans")
        active = np.flatnonzero(context.active)
        if not active.size:
            return False
        final_mask = context.cursors[active] + 1 == context.waypoint_counts[active]
        eligible_mask = (
            final_mask
            & ~context.readiness_any[active]
            & (context.stationary_streak[active] >= RECOVERY_STATIONARY_STREAK_THRESHOLD)
            & ((iteration - context.last_invalid_iteration[active]) > RECOVERY_INVALID_QUIET_ITERATIONS)
            & ~context.recovered[active]
        )
        eligible = active[eligible_mask]
        if not eligible.size:
            return False
        groups: dict[tuple[int, tuple[float, float]], list[int]] = {}
        for slot in eligible:
            agent_id = int(context.agent_ids[slot])
            state = context.states[agent_id]
            waypoint = context.waypoints[context.waypoint_offsets[slot] + context.cursors[slot]]
            exit_label = context.router._exit_labels[_id_key(state.exit_id)]
            key = (
                int(exit_label),
                (
                    round(float(waypoint[0]), RECOVERY_TARGET_ROUNDING_DIGITS),
                    round(float(waypoint[1]), RECOVERY_TARGET_ROUNDING_DIGITS),
                ),
            )
            groups.setdefault(key, []).append(int(slot))
        ordered_keys = sorted(
            groups,
            key=lambda key: (
                key[0],
                key[1][0],
                key[1][1],
                min(int(context.stable_ids[slot]) for slot in groups[key]),
            ),
        )
        context.recovery_counters["eligible_groups"] = (
            context.recovery_counters.get("eligible_groups", 0) + len(ordered_keys)
        )
        chosen: tuple | None = None
        skipped_groups = 0
        for key in ordered_keys:
            slots = groups[key]
            if len(slots) != RECOVERY_GROUP_SIZE:
                skipped_groups += 1
                continue
            signature = (
                key[0],
                key[1],
                frozenset(int(context.stable_ids[slot]) for slot in slots),
            )
            if signature in context.attempted_group_signatures:
                skipped_groups += 1
                continue
            chosen = (key, slots, signature)
            break
        context.recovery_counters["skipped_groups"] = (
            context.recovery_counters.get("skipped_groups", 0) + skipped_groups
        )
        if chosen is None:
            return False
        key, slots, signature = chosen
        exit_label = key[0]
        slots_sorted = sorted(slots, key=_exit_axis_projection(context))
        stable_ids = [int(context.stable_ids[slot]) for slot in slots_sorted]
        first_state = context.states[int(context.agent_ids[slots_sorted[0]])]
        exit_id = first_state.exit_id
        waypoint = context.waypoints[
            context.waypoint_offsets[slots_sorted[0]] + context.cursors[slots_sorted[0]]
        ]
        current_approach = (float(waypoint[0]), float(waypoint[1]))
        event = _recovery_event(context, iteration, exit_label, exit_id, key[1], stable_ids)
        context.recovery_events.append(event)
        context.attempted_group_signatures.add(signature)
        old_targets = []
        for slot in slots_sorted:
            target = context.simulation.agent(int(context.agent_ids[slot])).target
            old_targets.append(_point_list(target) if target is not None else None)
        event["oldTargets"] = old_targets
        neighbors = context.router.recovery_seed_neighbors(exit_id, current_approach)
        if neighbors is None:
            return _record_recovery_infeasible(context, event, "NO_SEEDS")
        if any(seed is None for seed in neighbors):
            return _record_recovery_infeasible(context, event, "EDGE_SEED")
        seeds = neighbors
        distinct_targets = {
            (
                round(seed[1][0], RECOVERY_TARGET_ROUNDING_DIGITS),
                round(seed[1][1], RECOVERY_TARGET_ROUNDING_DIGITS),
            )
            for seed in seeds
        }
        if len(distinct_targets) < RECOVERY_GROUP_SIZE:
            return _record_recovery_infeasible(context, event, "NON_DISTINCT_TARGETS", seeds)
        exit_ = context.router.exits[exit_label]
        if _id_key(exit_.id) != _id_key(exit_id):
            return _record_recovery_infeasible(context, event, "EXIT_ID_CHANGED", seeds)
        for slot, seed in zip(slots_sorted, seeds, strict=True):
            position = (float(context.positions[slot, 0]), float(context.positions[slot, 1]))
            if not context.router.can_connect(position, seed[2]):
                return _record_recovery_infeasible(context, event, "CONNECT_FAILED", seeds)
            if not context.router.can_reach_exit(position, seed[1]):
                return _record_recovery_infeasible(context, event, "REACH_FAILED", seeds)
        return _apply_recovery_mutation(context, slots_sorted, seeds, event)
    finally:
        if profile is not None:
            profile.add("recoveryScan", scan_started)


def _rebuild_waypoint_arrays(context: SimulationContext) -> None:
    np = context.numpy
    count = len(context.agent_ids)
    flattened: list[tuple[float, float]] = []
    offsets = np.empty(count + 1, dtype=np.int64)
    offsets[0] = 0
    counts = np.empty(count, dtype=np.int64)
    old_waypoints = context.waypoints
    old_offsets = context.waypoint_offsets
    for slot in range(count):
        if context.active[slot]:
            waypoints = context.states[int(context.agent_ids[slot])].waypoints
        else:
            start = int(old_offsets[slot])
            end = int(old_offsets[slot + 1])
            waypoints = tuple(
                (float(old_waypoints[index, 0]), float(old_waypoints[index, 1]))
                for index in range(start, end)
            )
        flattened.extend(waypoints)
        counts[slot] = len(waypoints)
        offsets[slot + 1] = len(flattened)
    context.waypoints = np.asarray(flattened, dtype=float).reshape(-1, 2)
    context.waypoint_offsets = offsets
    context.waypoint_counts = counts


def _apply_reroute_mutation(
    context: SimulationContext, slot: int, route, event: dict[str, Any]
) -> bool:
    agent_id = int(context.agent_ids[slot])
    state = context.states[agent_id]
    new_waypoints = tuple(route.waypoints)
    new_cursor = 1 if len(new_waypoints) > 1 else 0
    snapshot = {
        "waypoints": state.waypoints,
        "terminal_point": state.terminal_point,
        "exit_start": state.exit_start,
        "exit_end": state.exit_end,
        "exit_id": state.exit_id,
        "cursor": state.cursor,
        "target": context.simulation.agent(agent_id).target,
    }
    try:
        state.waypoints = new_waypoints
        state.terminal_point = route.terminal_point
        state.exit_start = route.exit_start
        state.exit_end = route.exit_end
        state.exit_id = route.exit_id
        state.cursor = new_cursor
        context.cursors[slot] = new_cursor
        context.terminal_points[slot] = route.terminal_point
        context.exit_starts[slot] = route.exit_start
        context.exit_ends[slot] = route.exit_end
        _rebuild_waypoint_arrays(context)
        target = new_waypoints[new_cursor]
        context.simulation.agent(agent_id).target = target
        context.recovered[slot] = True
        event["newTargets"] = [_point_list(target)]
        event["status"] = "RECOVERED"
        context.recovery_counters["recovered_mid_route"] = (
            context.recovery_counters.get("recovered_mid_route", 0) + 1
        )
        return True
    except Exception:
        state.waypoints = snapshot["waypoints"]
        state.terminal_point = snapshot["terminal_point"]
        state.exit_start = snapshot["exit_start"]
        state.exit_end = snapshot["exit_end"]
        state.exit_id = snapshot["exit_id"]
        state.cursor = snapshot["cursor"]
        context.cursors[slot] = snapshot["cursor"]
        context.terminal_points[slot] = snapshot["terminal_point"]
        context.exit_starts[slot] = snapshot["exit_start"]
        context.exit_ends[slot] = snapshot["exit_end"]
        _rebuild_waypoint_arrays(context)
        context.simulation.agent(agent_id).target = snapshot["target"]
        event["status"] = "RECOVERY_MUTATION_FAILED"
        return False


def mid_route_recovery_scan(context: SimulationContext, iteration: int) -> bool:
    try:
        from route_planner import AgentRouteUnreachableError, _id_key
    except ModuleNotFoundError:
        from .route_planner import AgentRouteUnreachableError, _id_key  # type: ignore[no-redef]
    np = context.numpy
    active = np.flatnonzero(context.active)
    if not active.size:
        return False
    route_following_mask = context.cursors[active] < context.waypoint_counts[active]
    eligible_mask = (
        route_following_mask
        & ~context.readiness_any[active]
        & (context.stationary_streak[active] >= MID_ROUTE_STATIONARY_STREAK_THRESHOLD)
        & ((iteration - context.last_invalid_iteration[active]) > RECOVERY_INVALID_QUIET_ITERATIONS)
        & ~context.recovered[active]
    )
    eligible = active[eligible_mask]
    if not eligible.size:
        return False
    slot = int(min(eligible.tolist(), key=lambda value: int(context.stable_ids[value])))
    agent_id = int(context.agent_ids[slot])
    state = context.states[agent_id]
    position = (float(context.positions[slot, 0]), float(context.positions[slot, 1]))
    waypoint = context.waypoints[context.waypoint_offsets[slot] + context.cursors[slot]]
    exit_label = context.router._exit_labels[_id_key(state.exit_id)]
    target_key = (
        round(float(waypoint[0]), RECOVERY_TARGET_ROUNDING_DIGITS),
        round(float(waypoint[1]), RECOVERY_TARGET_ROUNDING_DIGITS),
    )
    event = _recovery_event(
        context, iteration, exit_label, state.exit_id, target_key, [int(context.stable_ids[slot])]
    )
    context.recovery_events.append(event)
    target = context.simulation.agent(agent_id).target
    event["oldTargets"] = [_point_list(target) if target is not None else None]
    try:
        route = context.router.plan(position)
    except AgentRouteUnreachableError:
        return _record_recovery_infeasible(context, event, "REROUTE_UNREACHABLE")
    if tuple(route.waypoints) == tuple(state.waypoints):
        return _record_recovery_infeasible(context, event, "REROUTE_UNCHANGED")
    return _apply_reroute_mutation(context, slot, route, event)


def _build_recovery_summary(contexts: Sequence[SimulationContext]) -> dict[str, Any]:
    try:
        from route_planner import _id_key
    except ModuleNotFoundError:
        from .route_planner import _id_key  # type: ignore[no-redef]
    scan_count = 0
    eligible_group_count = 0
    skipped_group_count = 0
    infeasible_scan_count = 0
    recovered_group_count = 0
    recovered_mid_route_count = 0
    attempted_signatures = 0
    recovered_exit_labels: set[int] = set()
    recovered_exit_ids: list[Any] = []
    recovered_exit_id_keys: set[str] = set()
    events: list[dict[str, Any]] = []
    for context in contexts:
        counters = context.recovery_counters
        scan_count += counters.get("scans", 0)
        eligible_group_count += counters.get("eligible_groups", 0)
        skipped_group_count += counters.get("skipped_groups", 0)
        infeasible_scan_count += counters.get("infeasible_scans", 0)
        recovered_group_count += counters.get("recovered_groups", 0)
        recovered_mid_route_count += counters.get("recovered_mid_route", 0)
        attempted_signatures += len(context.attempted_group_signatures)
        for event in context.recovery_events:
            serialized = dict(event)
            stable_id_array = context.numpy.asarray(event["stableIds"], dtype=context.numpy.int64)
            slots = context.numpy.flatnonzero(
                context.numpy.isin(context.stable_ids, stable_id_array)
            )
            serialized["postRecoveryInvalidMoves"] = int(
                context.post_recovery_invalid_moves[slots].sum()
            )
            serialized["postRecoveryFullRollbacks"] = int(
                context.post_recovery_full_rollbacks[slots].sum()
            )
            if serialized["status"] == "RECOVERED":
                recovered_exit_labels.add(int(serialized["exitLabel"]))
                exit_id_key = _id_key(serialized["exitId"])
                if exit_id_key not in recovered_exit_id_keys:
                    recovered_exit_id_keys.add(exit_id_key)
                    recovered_exit_ids.append(serialized["exitId"])
            events.append(serialized)
    events.sort(
        key=lambda event: (
            event["iteration"],
            event["contextIndex"],
            event["exitLabel"],
            event["target"][0],
            event["target"][1],
            min(event["stableIds"]),
        )
    )
    recovered_times = [
        event["timeSeconds"] for event in events if event["status"] == "RECOVERED"
    ]
    return {
        "schemaVersion": 1,
        "scanCount": scan_count,
        "eligibleGroupCount": eligible_group_count,
        "skippedEligibleGroupCount": skipped_group_count,
        "infeasibleScanCount": infeasible_scan_count,
        "recoveredGroupCount": recovered_group_count,
        "recoveredAgentCount": RECOVERY_GROUP_SIZE * recovered_group_count,
        "recoveredMidRouteAgentCount": recovered_mid_route_count,
        "recoveryTimeSeconds": _rounded(min(recovered_times)) if recovered_times else 0.0,
        "recoveredExitLabels": sorted(recovered_exit_labels),
        "recoveredExitIds": sorted(recovered_exit_ids, key=str),
        "attemptedGroupSignatures": attempted_signatures,
        "events": events,
    }


def _waypoint_masks(context: SimulationContext, slots, positions):
    np = context.numpy
    if not slots.size:
        empty = np.empty(0, dtype=bool)
        return empty, empty

    waypoint_indices = context.waypoint_offsets[slots] + context.cursors[slots]
    delta = positions[slots] - context.waypoints[waypoint_indices]
    near = np.hypot(delta[:, 0], delta[:, 1]) <= WAYPOINT_REACHED_DISTANCE_METERS
    passed = np.zeros(len(slots), dtype=bool)
    pass_indices = np.flatnonzero(
        (context.cursors[slots] > 0)
        & (context.cursors[slots] + 1 < context.waypoint_counts[slots])
    )
    if pass_indices.size:
        current_indices = waypoint_indices[pass_indices]
        incoming = (
            context.waypoints[current_indices] - context.waypoints[current_indices - 1]
        )
        pass_delta = delta[pass_indices]
        passed[pass_indices] = (
            pass_delta[:, 0] * incoming[:, 0] + pass_delta[:, 1] * incoming[:, 1] > 1e-9
        )
    return near, passed


def _within_waypoint(position: tuple[float, float], state: AgentRouteState) -> bool:
    return math.dist(position, state.waypoints[state.cursor]) <= WAYPOINT_REACHED_DISTANCE_METERS


def _passed_waypoint(position: tuple[float, float], state: AgentRouteState) -> bool:
    if state.cursor == 0 or state.cursor + 1 >= len(state.waypoints):
        return False
    waypoint = state.waypoints[state.cursor]
    previous = state.waypoints[state.cursor - 1]
    incoming = (waypoint[0] - previous[0], waypoint[1] - previous[1])
    return (
        (position[0] - waypoint[0]) * incoming[0]
        + (position[1] - waypoint[1]) * incoming[1]
        > 1e-9
    )


def _waypoint_reached(
    position: tuple[float, float],
    state: AgentRouteState,
    can_connect: Callable[[tuple[float, float], tuple[float, float]], bool],
) -> bool:
    if _within_waypoint(position, state):
        return True
    if not _passed_waypoint(position, state):
        return False
    return can_connect(position, state.waypoints[state.cursor + 1])


def _snapshot(
    contexts: Sequence[SimulationContext],
    elapsed: float,
) -> dict[str, Any]:
    agents: list[dict[str, Any]] = []
    for context in contexts:
        if not context.states:
            continue
        for slot in context.numpy.flatnonzero(context.active):
            position = context.positions[slot]
            agents.append(
                {
                    "agentId": int(context.stable_ids[slot]),
                    "x": _rounded(position[0]),
                    "y": _rounded(position[1]),
                }
            )
    agents.sort(key=lambda value: value["agentId"])
    return {"timeSeconds": _rounded(elapsed), "agents": agents}


def _read_input(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as source:
            payload = json.load(source)
    except (OSError, json.JSONDecodeError) as exc:
        raise RunnerError(f"could not read input JSON: {exc}") from exc
    return _object(payload, "input")


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise RunnerError(f"{label} must be an object")
    return value


def _positive_number(value: Any, label: str) -> float:
    if isinstance(value, bool):
        raise RunnerError(f"{label} must be a positive number")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise RunnerError(f"{label} must be a positive number") from exc
    if not math.isfinite(result) or result <= 0:
        raise RunnerError(f"{label} must be a positive finite number")
    return result


def _nonnegative_number(value: Any, label: str) -> float:
    if isinstance(value, bool):
        raise RunnerError(f"{label} must be a non-negative number")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise RunnerError(f"{label} must be a non-negative number") from exc
    if not math.isfinite(result) or result < 0:
        raise RunnerError(f"{label} must be a non-negative finite number")
    return result


def _integer_number(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise RunnerError(f"{label} must be an integer")
    try:
        result = int(value)
    except (TypeError, ValueError, OverflowError) as exc:
        raise RunnerError(f"{label} must be an integer") from exc
    if value != result:
        raise RunnerError(f"{label} must be an integer")
    return result


def _sample_initial_response_times(np, count: int, std_dev: float, seed: int):
    if count < 1 or std_dev <= 0.0:
        return [0.0] * count
    response_times = np.random.default_rng(seed & 0xFFFFFFFF).normal(0.0, std_dev, size=count)
    return response_times - response_times.min()


def _start_iteration(response_time: float) -> int:
    if response_time <= 0.0:
        return 1
    if response_time >= MAX_SIMULATION_TIME_SECONDS:
        return int(MAX_SIMULATION_TIME_SECONDS / DT_SECONDS) + 1
    return int(math.ceil(response_time / DT_SECONDS - 1e-12)) + 1


def _agents(value: Any) -> list[tuple[float, float]]:
    if not isinstance(value, list):
        raise RunnerError("agents must be an array")
    result = []
    for index, item in enumerate(value):
        try:
            if isinstance(item, dict):
                point = (float(item["x"]), float(item["y"]))
            else:
                point = (float(item[0]), float(item[1]))
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise RunnerError(f"agents[{index}] must contain numeric x and y") from exc
        if not all(math.isfinite(coordinate) for coordinate in point):
            raise RunnerError(f"agents[{index}] coordinates must be finite")
        result.append(point)
    return result


def _route_origin_bounds(value: Any) -> tuple[float, float, float, float] | None:
    if value is None:
        return None
    item = _object(value, "routeOriginBounds")
    try:
        bounds = tuple(float(item[key]) for key in ("x", "y", "width", "height"))
    except (KeyError, TypeError, ValueError) as exc:
        raise RunnerError(
            "routeOriginBounds must contain numeric x, y, width and height"
        ) from exc
    if not all(math.isfinite(number) for number in bounds):
        raise RunnerError("routeOriginBounds values must be finite")
    if bounds[2] <= 0 or bounds[3] <= 0:
        raise RunnerError("routeOriginBounds width and height must be positive")
    return bounds


def _route_preview_zones(value: Any) -> tuple[dict[str, Any], ...]:
    if value is None:
        return ()
    if not isinstance(value, list):
        raise RunnerError("routePreviewZones must be an array")
    zones = []
    zone_ids = set()
    for index, value_item in enumerate(value):
        item = _object(value_item, f"routePreviewZones[{index}]")
        zone_id = item.get("zoneId")
        default_exit_id = item.get("defaultExitId")
        if not isinstance(zone_id, int) or isinstance(zone_id, bool) or zone_id in zone_ids:
            raise RunnerError("routePreviewZones zoneId must be a unique integer")
        if default_exit_id is not None and (
            not isinstance(default_exit_id, int) or isinstance(default_exit_id, bool)
        ):
            raise RunnerError("routePreviewZones defaultExitId must be an integer or null")
        try:
            bounds = tuple(float(item[key]) for key in ("x", "y", "width", "height"))
        except (KeyError, TypeError, ValueError) as exc:
            raise RunnerError(
                "routePreviewZones must contain numeric x, y, width and height"
            ) from exc
        if not all(math.isfinite(number) for number in bounds):
            raise RunnerError("routePreviewZones bounds must be finite")
        if bounds[2] <= 0 or bounds[3] <= 0:
            raise RunnerError("routePreviewZones width and height must be positive")
        zone_ids.add(zone_id)
        zones.append(
            {
                "zoneId": zone_id,
                "x": bounds[0],
                "y": bounds[1],
                "width": bounds[2],
                "height": bounds[3],
                "defaultExitId": default_exit_id,
            }
        )
    return tuple(zones)


def _serialize_preview_route(
    route,
    origin,
    original_origin,
    router,
    display_path,
    final_approach_path=None,
) -> dict[str, Any]:
    waypoints = list(route.waypoints)
    if not waypoints or math.dist(origin, waypoints[0]) > 1e-9:
        waypoints.insert(0, origin)
    if not waypoints or math.dist(waypoints[-1], route.terminal_point) > 1e-9:
        waypoints.append(route.terminal_point)
    waypoints = (
        final_approach_path(
            waypoints,
            router.can_connect,
            segment_cost=router.display_connection_cost,
        )
        if final_approach_path is not None
        else display_path(waypoints, router.can_connect)
    )
    distance_meters = sum(
        math.dist(start, end) for start, end in zip(waypoints, waypoints[1:])
    )
    return {
        "exitId": route.exit_id,
        "routeOrigin": {"x": _rounded(origin[0]), "y": _rounded(origin[1])},
        "originAdjusted": math.dist(original_origin, origin) > 1e-9,
        "distanceMeters": _rounded(distance_meters),
        "waypoints": [
            {"x": _rounded(x), "y": _rounded(y)} for x, y in waypoints
        ],
        "terminalPoint": {
            "x": _rounded(route.terminal_point[0]),
            "y": _rounded(route.terminal_point[1]),
        },
    }


def _zone_preview_routes(
    zones,
    coverage,
    routing_area,
    routers,
    exits,
    hazards,
    relocate_within_bounds,
    router_type,
    branch_origin_builder,
    display_path,
    final_approach_path,
    route_unreachable_error,
    id_key,
) -> list[dict[str, Any]]:
    serialized_routes = []
    assigned_routers = {}
    exits_by_id = {id_key(exit_.id): exit_ for exit_ in exits}
    for zone in zones:
        bounds = (zone["x"], zone["y"], zone["width"], zone["height"])
        default_exit_id = zone["defaultExitId"]
        if default_exit_id is None:
            candidates = branch_origin_builder(coverage, zone)
        else:
            candidates = [
                (
                    default_exit_id,
                    (
                        zone["x"] + zone["width"] / 2,
                        zone["y"] + zone["height"] / 2,
                    ),
                )
            ]
        for exit_id, requested_origin in candidates:
            origin = relocate_within_bounds(routing_area, requested_origin, bounds)
            if origin is None:
                continue
            planned = None
            planned_router = None
            for router in routers:
                candidate_router = router
                if default_exit_id is not None:
                    key = (id(router), id_key(exit_id))
                    if key not in assigned_routers:
                        target_exit = exits_by_id.get(id_key(exit_id))
                        if target_exit is None:
                            assigned_routers[key] = None
                        else:
                            try:
                                assigned_routers[key] = router_type(
                                    router.walkable,
                                    hazards,
                                    [target_exit],
                                    physical_walkable=router.physical_walkable,
                                    exit_clearance=AGENT_RADIUS_METERS,
                                    fast_single_exit_field=True,
                                )
                            except ValueError:
                                assigned_routers[key] = None
                    candidate_router = assigned_routers[key]
                    if candidate_router is None:
                        continue
                try:
                    route = candidate_router.plan(origin)
                except (ValueError, route_unreachable_error):
                    continue
                if id_key(route.exit_id) != id_key(exit_id):
                    continue
                planned = route
                planned_router = candidate_router
                break
            if planned is None or planned_router is None:
                continue
            serialized = _serialize_preview_route(
                planned,
                origin,
                requested_origin,
                planned_router,
                display_path,
                final_approach_path,
            )
            serialized["zoneId"] = zone["zoneId"]
            serialized_routes.append(serialized)
    return serialized_routes


def _rounded(value: Any) -> float:
    return round(float(value), 6)


def _write_json(path: Path, value: Any) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as target:
        json.dump(value, target, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
        target.write("\n")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", action="store_true", help="print the installed engine version")
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="validate initial routes without running simulation iterations",
    )
    parser.add_argument(
        "--route-preview",
        action="store_true",
        help=(
            "write routes.json with the planned route per agent and exit without "
            "running simulation iterations; wins over --validate-only"
        ),
    )
    parser.add_argument("input", nargs="?", type=Path, help="input JSON path")
    parser.add_argument("output_dir", nargs="?", type=Path, help="output directory")
    args = parser.parse_args(argv)
    try:
        if args.version:
            if (
                args.validate_only
                or args.route_preview
                or args.input is not None
                or args.output_dir is not None
            ):
                parser.error("--version does not accept input or output paths")
            *_dependencies, version = _load_dependencies()
            print(f"jupedsim {version}")
            return 0
        if args.input is None or args.output_dir is None:
            parser.error("input and output_dir are required")
        run(
            args.input,
            args.output_dir,
            validate_only=args.validate_only,
            route_preview=args.route_preview,
        )
        return 0
    except NoReachableSelectedExitRunnerError:
        print("runner error: NO_REACHABLE_SELECTED_EXIT", file=sys.stderr)
        return 3
    except NoWalkableOriginInZoneRunnerError:
        print("runner error: NO_WALKABLE_ORIGIN_IN_ZONE", file=sys.stderr)
        return 3
    except AgentRouteUnreachableRunnerError:
        print("runner error: AGENT_ROUTE_UNREACHABLE", file=sys.stderr)
        return 3
    except RunnerError as exc:
        print(f"runner error: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:
        print(f"runner error: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
