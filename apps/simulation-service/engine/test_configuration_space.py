import math

import pytest
from shapely.geometry import LineString, Polygon, box

from configuration_space import (
    ConfigurationSpace,
    InvalidConfigurationValueError,
    Pose,
    RectangleFootprint,
    adaptive_angles,
    is_feasible,
    project_pose,
    rectangle_at,
)
from constraints import FIXED, FREE, WITHIN_ZONE, SearchConstraints


def test_pose_normalizes_rotation_wraparound_and_negative_zero() -> None:
    # Given: equivalent poses written with floating-point boundary values.
    # When: they cross the configuration-space boundary.
    pose = Pose(-0.0, 1.00000000001, 720.00004)

    # Then: they share one stable, serializable representation.
    assert pose == Pose(0.0, 1.0, 0.0)


@pytest.mark.parametrize(
    ("width", "height", "name"),
    ((0.00001, 1.0, "width"), (1.0, 0.00001, "height")),
)
def test_footprint_rejects_a_positive_dimension_that_quantizes_to_zero(
    width: float,
    height: float,
    name: str,
) -> None:
    # Given: a positive dimension smaller than half the stored-coordinate quantum.
    # When: the footprint crosses the configuration-space boundary.
    with pytest.raises(InvalidConfigurationValueError) as error:
        RectangleFootprint(width, height)

    # Then: the degenerate canonical dimension is rejected before geometry or angle math.
    assert error.value.name == name
    assert error.value.value == 0.00001


def test_free_pose_may_touch_the_room_boundary_but_not_cross_it() -> None:
    # Given: a freely movable 2 m square in a 10 m room.
    space = ConfigurationSpace(
        1,
        Pose(5.0, 5.0),
        RectangleFootprint(2.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
    )

    # When: final poses touch or cross the outside boundary.
    touching = is_feasible(space, Pose(1.0, 5.0))
    crossing = is_feasible(space, Pose(0.9999, 5.0))

    # Then: touching is valid while even a stored-coordinate quantum outside is rejected.
    assert touching is True
    assert crossing is False


def test_solid_contact_is_rejected_but_a_coordinate_quantum_gap_is_valid() -> None:
    # Given: the same fabric beside either a touching or epsilon-separated obstacle.
    touching_space = ConfigurationSpace(
        1,
        Pose(1.0, 1.0),
        RectangleFootprint(2.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
        solids=(box(2.0, 0.0, 3.0, 2.0),),
    )
    gap_space = ConfigurationSpace(
        1,
        Pose(1.0, 1.0),
        RectangleFootprint(2.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
        solids=(box(2.0001, 0.0, 3.0, 2.0),),
    )

    # When: exact final-placement collision is evaluated.
    touching = is_feasible(touching_space, touching_space.source)
    separated = is_feasible(gap_space, gap_space.source)

    # Then: an actual intersection is rejected without swallowing the representable gap.
    assert touching is False
    assert separated is True


def test_within_zone_requires_the_rotated_footprint_to_be_covered() -> None:
    # Given: a 2 by 1 m object restricted to a 4 m square zone.
    constraints = SearchConstraints(
        {7: WITHIN_ZONE},
        {7: {"x": 0.0, "y": 0.0, "width": 4.0, "height": 4.0}},
    )
    space = ConfigurationSpace(
        7,
        Pose(2.0, 2.0),
        RectangleFootprint(2.0, 1.0),
        box(-10.0, -10.0, 10.0, 10.0),
        constraints,
    )

    # When: the rotated object is placed wholly inside or partly outside its zone.
    inside = is_feasible(space, Pose(0.5, 2.0, 90.0))
    outside = is_feasible(space, Pose(0.4999, 2.0, 90.0))

    # Then: containment is based on the rotated polygon, not its unrotated dimensions.
    assert inside is True
    assert outside is False


def test_fixed_space_accepts_only_the_canonical_source_pose() -> None:
    # Given: a fixed object whose angle is represented at the wrap boundary.
    space = ConfigurationSpace(
        9,
        Pose(3.0, 4.0, 360.0),
        RectangleFootprint(1.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({9: FIXED}),
    )

    # When: equivalent and translated final poses are checked.
    equivalent = is_feasible(space, Pose(3.0, 4.0, 0.0))
    translated = is_feasible(space, Pose(3.0001, 4.0, 0.0))

    # Then: only the exact canonical source pose is feasible.
    assert equivalent is True
    assert translated is False


def test_wall_contact_is_allowed_but_crossing_a_wall_is_rejected() -> None:
    # Given: a vertical wall and a 2 m square fabric.
    wall = LineString(((4.0, 0.0), (4.0, 10.0)))
    space = ConfigurationSpace(
        3,
        Pose(2.0, 5.0),
        RectangleFootprint(2.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({3: FREE}),
        walls=(wall,),
    )

    # When: the fabric edge touches the wall or its interior straddles it.
    touching = is_feasible(space, Pose(3.0, 5.0))
    crossing = is_feasible(space, Pose(4.0, 5.0))

    # Then: final-placement semantics match the existing wall guard.
    assert touching is True
    assert crossing is False


def test_adaptive_angles_refine_near_constraints_and_wrap_deterministically() -> None:
    # Given: the same elongated object in open space and touching a wall.
    outside = box(0.0, 0.0, 20.0, 20.0)
    open_space = ConfigurationSpace(
        1,
        Pose(10.0, 10.0, 350.0),
        RectangleFootprint(4.0, 1.0),
        outside,
        SearchConstraints({1: FREE}),
    )
    tight_space = ConfigurationSpace(
        1,
        Pose(2.0, 10.0, 350.0),
        RectangleFootprint(4.0, 1.0),
        outside,
        SearchConstraints({1: FREE}),
    )

    # When: configuration-space angles are sampled from each clearance.
    open_angles = adaptive_angles(open_space, open_space.source)
    tight_angles = adaptive_angles(tight_space, tight_space.source)

    # Then: tighter clearance gets finer samples with stable normalized wraparound.
    assert len(tight_angles) > len(open_angles)
    assert tight_angles[0] == 350.0
    assert len(tight_angles) == len(set(tight_angles))
    assert all(0.0 <= angle < 360.0 for angle in tight_angles)


def test_adaptive_angles_refine_for_a_longer_rotation_radius() -> None:
    # Given: compact and elongated objects with the same center clearance.
    outside = box(0.0, 0.0, 20.0, 20.0)
    compact = ConfigurationSpace(
        1,
        Pose(10.0, 10.0),
        RectangleFootprint(1.0, 1.0),
        outside,
        SearchConstraints({1: FREE}),
    )
    elongated = ConfigurationSpace(
        1,
        Pose(10.0, 10.0),
        RectangleFootprint(8.0, 1.0),
        outside,
        SearchConstraints({1: FREE}),
    )

    # When: their angular lattices limit corner displacement.
    compact_angles = adaptive_angles(compact, compact.source)
    elongated_angles = adaptive_angles(elongated, elongated.source)

    # Then: the longer rotation radius receives finer angular resolution.
    assert len(elongated_angles) > len(compact_angles)


def test_blocked_region_contact_is_rejected() -> None:
    # Given: an exit guard represented as a blocked final-placement region.
    space = ConfigurationSpace(
        1,
        Pose(2.0, 2.0),
        RectangleFootprint(1.0, 1.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
        blocked=(box(4.0, 4.0, 5.0, 5.0),),
    )

    # When: a proposal touches that guarded region.
    feasible = is_feasible(space, Pose(3.5, 4.5))

    # Then: contact is rejected exactly like an exit guard in V2.
    assert feasible is False


def test_projection_returns_the_nearest_feasible_pose_on_the_proposed_segment() -> None:
    # Given: a feasible source and a proposed center beyond the room boundary.
    space = ConfigurationSpace(
        1,
        Pose(2.0, 5.0),
        RectangleFootprint(2.0, 2.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
    )

    # When: the proposal is projected through configuration space.
    projected = project_pose(space, Pose(-3.0, 5.0))

    # Then: the pose stops exactly where its footprint touches the boundary.
    assert projected == Pose(1.0, 5.0)


def test_projection_checks_only_the_final_pose_not_the_transport_path() -> None:
    # Given: valid endpoints separated by a solid obstacle.
    space = ConfigurationSpace(
        1,
        Pose(2.0, 5.0),
        RectangleFootprint(1.0, 1.0),
        box(0.0, 0.0, 10.0, 10.0),
        SearchConstraints({1: FREE}),
        solids=(box(4.0, 4.0, 6.0, 6.0),),
    )
    proposal = Pose(8.0, 5.0)

    # When: the final pose is projected despite the blocked path between endpoints.
    projected = project_pose(space, proposal)

    # Then: transport-path collision is intentionally out of scope.
    assert projected == proposal


def test_rotated_rectangle_preserves_footprint_area_in_a_concave_room() -> None:
    # Given: a valid pose inside a concave outside boundary.
    outside = Polygon(((0.0, 0.0), (6.0, 0.0), (6.0, 2.0), (2.0, 2.0), (2.0, 6.0), (0.0, 6.0)))
    space = ConfigurationSpace(
        1,
        Pose(1.0, 3.0, 45.0),
        RectangleFootprint(0.5, 1.0),
        outside,
        SearchConstraints({1: FREE}),
    )

    # When: its rotated footprint is built and evaluated.
    geometry = rectangle_at(space.footprint, space.source)

    # Then: concavity is respected and rotation does not change object area.
    assert math.isclose(geometry.area, 0.5, abs_tol=1e-12)
    assert is_feasible(space, space.source) is True
