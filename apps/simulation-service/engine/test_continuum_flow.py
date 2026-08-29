from __future__ import annotations

import math

import numpy as np
from shapely.geometry import LineString, box

from configuration_space import Pose, RectangleFootprint
from configuration_space_search_types import FabricId, Layout, LayoutPose
from continuum_flow import ContinuumEvaluator, ContinuumFabric, ContinuumScenario
from continuum_flow_types import ContinuumField
from continuum_sampling import sample_field


def _layout(*poses: tuple[int, Pose]) -> Layout:
    return Layout.of(tuple(LayoutPose(FabricId(fabric_id), pose) for fabric_id, pose in poses))


def test_uniform_corridor_has_unit_speed_travel_time() -> None:
    # Given: an empty ten-metre corridor with a full-width exit at its right edge.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 10.0, 2.0),
            exits=(LineString(((10.0, 0.0), (10.0, 2.0))),),
            agents=((1.0, 1.0),),
            fabrics=(),
            cell_size=0.25,
        )
    )

    # When: the Eikonal travel-time field is solved.
    result = evaluator.evaluate(_layout())

    # Then: the source travel time matches the nine-metre straight path.
    assert math.isclose(result.objective.total, 9.0, abs_tol=0.5)


def test_non_grid_aligned_boundary_exit_seeds_reachable_field() -> None:
    # Given: a corridor whose right boundary is not aligned to the 0.25 m grid.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 10.1, 2.0),
            exits=(LineString(((10.1, 0.0), (10.1, 2.0))),),
            agents=((1.0, 1.0),),
            fabrics=(),
            cell_size=0.25,
        )
    )

    # When: the continuum field is solved from the non-aligned boundary exit.
    result = evaluator.evaluate(_layout())

    # Then: an inside free cell is seeded and the agent remains reachable.
    seeded = result.field.free & np.isclose(result.field.travel_time, 0.0)
    assert np.any(seeded)
    assert math.isfinite(result.objective.total)


def test_obstacle_increases_travel_time_by_forcing_a_detour() -> None:
    # Given: the same route once open and once narrowed by a tall obstacle.
    common = {
        "walkable": box(0.0, 0.0, 10.0, 4.0),
        "exits": (LineString(((10.0, 0.0), (10.0, 4.0))),),
        "agents": ((1.0, 2.0),),
        "cell_size": 0.25,
    }
    open_result = ContinuumEvaluator(ContinuumScenario(fabrics=(), **common)).evaluate(_layout())
    blocked = ContinuumEvaluator(
        ContinuumScenario(
            fabrics=(ContinuumFabric(FabricId(7), RectangleFootprint(1.0, 3.0)),),
            **common,
        )
    )

    # When: the obstacle is placed across most of the corridor height.
    detour_result = blocked.evaluate(_layout((7, Pose(5.0, 1.5))))

    # Then: its continuum arrival time is strictly longer than the open route.
    assert detour_result.objective.total > open_result.objective.total + 0.5


def test_two_exits_apply_independent_zero_boundary_conditions() -> None:
    # Given: two agents near opposite exits of a symmetric corridor.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 10.0, 2.0),
            exits=(
                LineString(((0.0, 0.0), (0.0, 2.0))),
                LineString(((10.0, 0.0), (10.0, 2.0))),
            ),
            agents=((2.0, 1.0), (8.0, 1.0)),
            fabrics=(),
            cell_size=0.25,
        )
    )

    # When: both exit boundaries seed one shared field.
    result = evaluator.evaluate(_layout())

    # Then: each agent reaches its nearest exit in about two metres.
    assert math.isclose(result.objective.average, 2.0, abs_tol=0.5)


def test_boundary_sampling_normalizes_only_free_cell_weights() -> None:
    # Given: interpolation support with one free cell and one outside cell.
    field = ContinuumField(
        x=np.array((0.125, 0.375), dtype=np.float64),
        y=np.array((0.125,), dtype=np.float64),
        free=np.array(((True, False),), dtype=np.bool_),
        travel_time=np.array(((2.0, math.inf),), dtype=np.float64),
        density=np.zeros((1, 2), dtype=np.float64),
    )

    # When: a point halfway between those cell centres is sampled.
    sampled = sample_field(field, (0.25, 0.125))

    # Then: the outside weight is discarded and the free value is normalized.
    assert sampled == 2.0


def test_sampling_preserves_unreachable_free_cell_semantics() -> None:
    # Given: interpolation support containing a genuinely unreachable free cell.
    field = ContinuumField(
        x=np.array((0.125, 0.375), dtype=np.float64),
        y=np.array((0.125,), dtype=np.float64),
        free=np.array(((True, True),), dtype=np.bool_),
        travel_time=np.array(((2.0, math.inf),), dtype=np.float64),
        density=np.zeros((1, 2), dtype=np.float64),
    )

    # When: both free cells contribute to the sample.
    sampled = sample_field(field, (0.25, 0.125))

    # Then: unreachable free support is not normalized away.
    assert math.isinf(sampled)


def test_higher_agent_density_slows_the_same_open_route() -> None:
    # Given: identical open corridors with one agent versus forty colocated agents.
    common = {
        "walkable": box(0.0, 0.0, 10.0, 2.0),
        "exits": (LineString(((10.0, 0.0), (10.0, 2.0))),),
        "fabrics": (),
        "cell_size": 0.25,
    }
    low_density = ContinuumEvaluator(
        ContinuumScenario(agents=((1.0, 1.0),), **common)
    )
    high_density = ContinuumEvaluator(
        ContinuumScenario(agents=((1.0, 1.0),) * 40, **common)
    )

    # When: both density fields are solved on the same geometry.
    low_result = low_density.evaluate(_layout())
    high_result = high_density.evaluate(_layout())

    # Then: crowd density independently increases per-agent travel time.
    assert high_result.objective.average > low_result.objective.average


def test_symmetric_layout_has_zero_force_and_torque() -> None:
    # Given: symmetric agents, exits, and a centred movable rectangle.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 10.0, 6.0),
            exits=(
                LineString(((0.0, 0.0), (0.0, 6.0))),
                LineString(((10.0, 0.0), (10.0, 6.0))),
            ),
            agents=((2.0, 3.0), (8.0, 3.0)),
            fabrics=(ContinuumFabric(FabricId(4), RectangleFootprint(0.5, 2.0)),),
            cell_size=0.25,
        )
    )

    # When: boundary sensitivity is sampled around the symmetric pose.
    signal = evaluator.signals(_layout((4, Pose(5.0, 3.0))))[0]

    # Then: no translation or rotation direction is preferred.
    assert math.isclose(signal.force_x, 0.0, abs_tol=1e-9)
    assert math.isclose(signal.force_y, 0.0, abs_tol=1e-9)
    assert math.isclose(signal.torque, 0.0, abs_tol=1e-9)


def test_disconnected_agent_has_infinite_evacuation_objective() -> None:
    # Given: a wall-height movable fabric separating an agent from the only exit.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 10.0, 4.0),
            exits=(LineString(((10.0, 0.0), (10.0, 4.0))),),
            agents=((1.0, 2.0),),
            fabrics=(ContinuumFabric(FabricId(9), RectangleFootprint(1.0, 4.0)),),
            cell_size=0.25,
        )
    )

    # When: the separating fabric is evaluated.
    result = evaluator.evaluate(_layout((9, Pose(5.0, 2.0))))

    # Then: the unreachable source is never mistaken for an improvement.
    assert math.isinf(result.objective.total)


def test_continuum_evaluation_replays_deterministically() -> None:
    # Given: one non-trivial obstacle detour scenario.
    evaluator = ContinuumEvaluator(
        ContinuumScenario(
            walkable=box(0.0, 0.0, 8.0, 4.0),
            exits=(LineString(((8.0, 0.0), (8.0, 4.0))),),
            agents=((1.0, 1.0), (1.0, 3.0)),
            fabrics=(ContinuumFabric(FabricId(3), RectangleFootprint(1.0, 2.0)),),
            cell_size=0.25,
        )
    )
    layout = _layout((3, Pose(4.0, 2.0, 15.0)))

    # When: identical immutable input is evaluated twice.
    first = evaluator.evaluate(layout)
    second = evaluator.evaluate(layout)

    # Then: objectives, fields, and compatible shape signals are bitwise stable.
    assert first.objective == second.objective
    assert np.array_equal(first.field.travel_time, second.field.travel_time, equal_nan=True)
    assert np.array_equal(first.field.density, second.field.density)
    assert evaluator.signals(layout) == evaluator.signals(layout)
