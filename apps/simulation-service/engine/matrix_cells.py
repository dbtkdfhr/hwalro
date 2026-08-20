"""Builds the E2E test matrix cells from `apps/frontend/e2e/matrix.json`.

One place turns axis values into concrete geometry. `probe_matrix.py` (tier 1,
no JuPedSim) and `run_matrix.py` (tier 2, real trials) both import from here,
and `--emit` writes the same drawings out as JSON so the Playwright seeder POSTs
them instead of re-deriving the axes in TypeScript.

    python matrix_cells.py --emit out/          # drawings for the REST seeder
    python matrix_cells.py --list               # cell ids
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
from typing import Any, Iterator

from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union

import layout_search

REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
DEFAULT_MATRIX = REPO_ROOT / "apps" / "frontend" / "e2e" / "matrix.json"

# Agents need room to stand. The engine treats a body as roughly this wide, so a
# sampled position closer than this to a wall or a fabric is not a valid start.
AGENT_CLEARANCE_METERS = 0.6
WALL_THICKNESS_METERS = 0.15


# --------------------------------------------------------------------------- #
# base geometry
# --------------------------------------------------------------------------- #


def _load_default_drawing(matrix: dict[str, Any]) -> dict[str, Any]:
    path = REPO_ROOT / matrix["base"]
    with path.open(encoding="utf-8") as handle:
        drawing = json.load(handle)
    return {
        "walls": [_segment(item) for item in drawing["walls"]],
        "outsideWalls": [_segment(item) for item in drawing["outsideWalls"]],
        "pillars": [_rect(item) for item in drawing["pillars"]],
        "fabrics": [_rect(item) for item in drawing["fabrics"]],
        "exits": [_segment(item) for item in drawing["exits"]],
    }


def _empty_drawing() -> dict[str, Any]:
    """What a user actually ends up with after starting from a blank drawing.

    A single hall with two dividing walls and three exits. The point of the
    "empty" axis is not that the canvas was blank - it is that the resulting
    layout is sparse and regular, where the default floor plan is dense and
    irregular. Structures come from the B axis on top of this shell.
    """
    width, height = 60.0, 40.0
    corners = [(0.0, 0.0), (width, 0.0), (width, height), (0.0, height)]
    outside = [
        _segment({"startX": corners[i][0], "startY": corners[i][1],
                  "endX": corners[(i + 1) % 4][0], "endY": corners[(i + 1) % 4][1]})
        for i in range(4)
    ]
    walls = [
        _segment({"startX": 20.0, "startY": 0.0, "endX": 20.0, "endY": 14.0}),
        _segment({"startX": 20.0, "startY": 26.0, "endX": 20.0, "endY": 40.0}),
        _segment({"startX": 40.0, "startY": 0.0, "endX": 40.0, "endY": 14.0}),
        _segment({"startX": 40.0, "startY": 26.0, "endX": 40.0, "endY": 40.0}),
    ]
    exits = [
        _segment({"startX": 4.0, "startY": 0.0, "endX": 9.0, "endY": 0.0}),
        _segment({"startX": 51.0, "startY": 40.0, "endX": 56.0, "endY": 40.0}),
        _segment({"startX": 0.0, "startY": 17.0, "endX": 0.0, "endY": 22.0}),
    ]
    return {"walls": walls, "outsideWalls": outside, "pillars": [], "fabrics": [], "exits": exits}


def _empty_structures(count_fabrics: int, count_pillars: int) -> tuple[list, list]:
    """A regular grid of structures for the hall above.

    Deliberately regular: the default floor plan already covers irregular, and a
    grid makes an off-by-one in the rotation or span logic obvious by eye.
    """
    fabrics, pillars = [], []
    columns = 6
    for index in range(count_fabrics):
        column, row = index % columns, index // columns
        x = 3.0 + column * 9.0
        y = 4.0 + row * 5.5
        fabrics.append(_rect({
            "name": f"구조물 {index + 1}", "startX": x, "startY": y,
            "endX": x + 4.0, "endY": y + 2.0, "rotation": 0.0,
        }))
    for index in range(count_pillars):
        x = 12.0 + index * 16.0
        pillars.append(_rect({
            "name": f"기둥 {index + 1}", "startX": x, "startY": 19.0,
            "endX": x + 1.2, "endY": 20.2, "rotation": 0.0,
        }))
    return fabrics, pillars


def _segment(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": item.get("name"),
        "startX": float(item["startX"]), "startY": float(item["startY"]),
        "endX": float(item["endX"]), "endY": float(item["endY"]),
    }


def _rect(item: dict[str, Any]) -> dict[str, Any]:
    """Normalized the way `DrawingService` normalizes on save.

    The default drawing resource ships 12 of 18 pillars and 10 of 58 fabrics
    with start > end. Java rewrites those on insert, so a fixture that skipped
    this step would be testing geometry the database can never hold.
    """
    start_x, end_x = sorted((float(item["startX"]), float(item["endX"])))
    start_y, end_y = sorted((float(item["startY"]), float(item["endY"])))
    return {
        "name": item.get("name"),
        "startX": start_x, "startY": start_y, "endX": end_x, "endY": end_y,
        "rotation": float(item.get("rotation", 0.0)),
    }


def _assemble_boundary(outside_walls: list[dict[str, Any]]) -> list[dict[str, float]]:
    """Walk the outside wall segments into one closed ring.

    Mirrors `SimulationGeometry.assembleBoundary` - same closed-loop requirement,
    same failure when an endpoint does not have exactly two neighbours.
    """
    graph: dict[tuple, list[tuple]] = {}
    for wall in outside_walls:
        start = (round(wall["startX"], 4), round(wall["startY"], 4))
        end = (round(wall["endX"], 4), round(wall["endY"], 4))
        if start == end:
            raise ValueError("외곽선에 길이가 0인 선분이 있습니다.")
        graph.setdefault(start, []).append(end)
        graph.setdefault(end, []).append(start)
    bad = [point for point, neighbors in graph.items() if len(neighbors) != 2]
    if len(graph) < 3 or bad:
        raise ValueError(f"외곽선이 폐곡선이 아닙니다. 끝점 {len(bad)}개의 연결이 2개가 아닙니다.")

    for neighbors in graph.values():
        neighbors.sort()
    start = min(graph)
    ordered, previous, current = [], None, start
    while True:
        ordered.append(current)
        neighbors = graph[current]
        following = neighbors[0] if previous is None or neighbors[0] != previous else neighbors[1]
        if following == start:
            break
        if len(ordered) > len(graph):
            raise ValueError("외곽선이 하나의 연결된 폐곡선을 만들지 못합니다.")
        previous, current = current, following
    return [{"x": x, "y": y} for x, y in ordered]


# --------------------------------------------------------------------------- #
# axes
# --------------------------------------------------------------------------- #


def _apply_structure_count(drawing: dict[str, Any], spec: dict[str, Any], is_empty: bool) -> None:
    want_fabrics, want_pillars = spec.get("fabrics"), spec.get("pillars")
    if is_empty:
        fabrics, pillars = _empty_structures(want_fabrics or 24, want_pillars or 3)
        drawing["fabrics"], drawing["pillars"] = fabrics, pillars
        return
    if want_fabrics is not None:
        drawing["fabrics"] = drawing["fabrics"][:want_fabrics]
    if want_pillars is not None:
        drawing["pillars"] = drawing["pillars"][:want_pillars]


def _apply_rotations(drawing: dict[str, Any], spec: dict[str, Any]) -> None:
    """Cycle the requested angles across fabrics.

    Rotation is stored, not baked into the coordinates - the span stays put and
    the angle rides along. That is the contract the search engine has to respect
    when it rotates a fabric, so the fixture has to set it the same way.
    """
    rotations = [float(value) for value in spec["rotations"]]
    for index, fabric in enumerate(drawing["fabrics"]):
        fabric["rotation"] = rotations[index % len(rotations)]


def _walkable(drawing: dict[str, Any], boundary: list[dict[str, float]]) -> Any:
    area = Polygon([(point["x"], point["y"]) for point in boundary])
    # Rotated fabrics cover different ground than their stored bounding box, so
    # the blocker has to be the rotated polygon the router itself builds. Using
    # the axis-aligned box here drops agents inside structures on the `mixed`
    # rotation cells, and the router then refuses to connect them to the grid.
    blockers = [
        layout_search._rect_geometry(item).buffer(AGENT_CLEARANCE_METERS)
        for item in drawing["fabrics"] + drawing["pillars"]
    ]
    for wall in drawing["walls"]:
        segment = box(
            min(wall["startX"], wall["endX"]) - WALL_THICKNESS_METERS,
            min(wall["startY"], wall["endY"]) - WALL_THICKNESS_METERS,
            max(wall["startX"], wall["endX"]) + WALL_THICKNESS_METERS,
            max(wall["startY"], wall["endY"]) + WALL_THICKNESS_METERS,
        )
        blockers.append(segment.buffer(AGENT_CLEARANCE_METERS))
    free = area.buffer(-AGENT_CLEARANCE_METERS)
    if blockers:
        free = free.difference(unary_union(blockers))
    return free


def _place_agents(drawing: dict[str, Any], boundary: list[dict[str, float]],
                  spec: dict[str, Any], count: int) -> list[dict[str, float]]:
    """Deterministic placement - no RNG, so a failing cell reproduces exactly."""
    free = _walkable(drawing, boundary)
    if free.is_empty:
        return []
    min_x, min_y, max_x, max_y = free.bounds

    if spec["mode"] == "cluster":
        exits = drawing["exits"]
        index = min(int(spec["towardExitIndex"]), len(exits) - 1)
        target = exits[index]
        center = ((target["startX"] + target["endX"]) / 2.0, (target["startY"] + target["endY"]) / 2.0)
        clustered = int(count * float(spec["fraction"]))
        points = _spiral_fill(free, center, float(spec["radius"]), clustered)
        points += _grid_fill(free, min_x, min_y, max_x, max_y, count - len(points), skip=points)
        return points[:count]

    return _grid_fill(free, min_x, min_y, max_x, max_y, count)[:count]


def _grid_fill(free: Any, min_x: float, min_y: float, max_x: float, max_y: float,
               count: int, skip: list | None = None) -> list[dict[str, float]]:
    if count <= 0:
        return []
    taken = {(round(p["x"], 2), round(p["y"], 2)) for p in (skip or [])}
    # Start from the aspect-correct grid for `count` and shrink the step until
    # enough sampled cells actually land in walkable space.
    for divisor in (1.0, 1.4, 2.0, 2.8, 4.0):
        area = max((max_x - min_x) * (max_y - min_y), 1.0)
        step = max(math.sqrt(area / max(count, 1)) / divisor, AGENT_CLEARANCE_METERS)
        points: list[dict[str, float]] = []
        y = min_y + step / 2.0
        while y < max_y and len(points) < count:
            x = min_x + step / 2.0
            while x < max_x and len(points) < count:
                key = (round(x, 2), round(y, 2))
                if key not in taken and free.contains(Point(x, y)):
                    points.append({"x": round(x, 4), "y": round(y, 4)})
                x += step
            y += step
        if len(points) >= count:
            return points
    return points


def _spiral_fill(free: Any, center: tuple[float, float], radius: float, count: int) -> list[dict[str, float]]:
    """Pack `count` agents around `center`, growing outward in rings."""
    points: list[dict[str, float]] = []
    if count <= 0:
        return points
    if free.contains(Point(*center)):
        points.append({"x": round(center[0], 4), "y": round(center[1], 4)})
    ring = 1
    while len(points) < count and ring * AGENT_CLEARANCE_METERS <= radius * 2:
        distance = ring * AGENT_CLEARANCE_METERS * 1.6
        slots = max(6 * ring, 1)
        for slot in range(slots):
            if len(points) >= count:
                break
            angle = 2.0 * math.pi * slot / slots
            x = center[0] + distance * math.cos(angle)
            y = center[1] + distance * math.sin(angle)
            if free.contains(Point(x, y)):
                points.append({"x": round(x, 4), "y": round(y, 4)})
        ring += 1
    return points


class UnbuildableCellError(RuntimeError):
    """The cell cannot hold the agents the matrix asks for."""


def _routable_agents(drawing: dict[str, Any], boundary: list[dict[str, float]],
                     spec: dict[str, Any], count: int,
                     hazards: list[dict[str, float]]) -> list[dict[str, float]]:
    """Place agents, then keep only the ones the router can actually plan for.

    Shapely's idea of free space and the router's grid do not agree at the
    edges, and a single unroutable agent aborts the whole search. Over-sample,
    filter against the real router, and fail loudly if the cell still comes up
    short - a fixture that silently ships 40 agents where the matrix says 120
    would make every downstream number incomparable.
    """
    routable = {**drawing, "outsideBoundary": boundary}
    parsed_hazards = layout_search.parse_hazards(hazards)
    parsed_exits = layout_search.parse_exits(routable, [item["id"] for item in drawing["exits"]])
    routing = layout_search.build_routing_geometry(routable, layout_search.CORRIDOR_CLEARANCE_METERS)
    router = layout_search.GridRouter(
        routing, parsed_hazards, parsed_exits,
        step=layout_search.GRID_STEP_METERS,
        physical_walkable=layout_search.build_walkable_geometry(routable),
        exit_clearance=0.3,
    )

    for oversample in (1.0, 1.6, 2.5, 4.0):
        placed = _place_agents(drawing, boundary, spec, int(count * oversample))
        relocated, _ = layout_search.relocate_agents(routing, [(p["x"], p["y"]) for p in placed])
        kept: list[dict[str, float]] = []
        for x, y in relocated:
            try:
                router.plan((x, y))
            except Exception:
                continue
            kept.append({"x": round(x, 4), "y": round(y, 4)})
            if len(kept) == count:
                return kept
    raise UnbuildableCellError(
        f"경로를 낼 수 있는 위치가 {len(kept)}개뿐이다 (요청 {count}개)")


def _place_hazards(drawing: dict[str, Any], boundary: list[dict[str, float]],
                   spec: dict[str, Any]) -> list[dict[str, float]]:
    """Hazards sit away from exits - a hazard on top of an exit just deletes it,
    which tests the exit-removal path rather than the routing-around path."""
    count = int(spec["count"])
    if count == 0:
        return []
    radius = float(spec["radius"])
    area = Polygon([(point["x"], point["y"]) for point in boundary])
    min_x, min_y, max_x, max_y = area.bounds
    exits = [
        Point((item["startX"] + item["endX"]) / 2.0, (item["startY"] + item["endY"]) / 2.0)
        for item in drawing["exits"]
    ]
    zones: list[dict[str, float]] = []
    for step in range(1, count * 6):
        fraction = step / (count * 6.0)
        x = min_x + (max_x - min_x) * (0.2 + 0.6 * fraction)
        y = min_y + (max_y - min_y) * (0.25 + 0.5 * ((step * 7) % 10) / 10.0)
        candidate = Point(x, y)
        if not area.contains(candidate):
            continue
        if any(candidate.distance(item) < radius * 2.5 for item in exits):
            continue
        if any(candidate.distance(Point(zone["centerX"], zone["centerY"])) < radius * 2.0 for zone in zones):
            continue
        zones.append({"centerX": round(x, 4), "centerY": round(y, 4), "radius": radius})
        if len(zones) == count:
            break
    return zones


def _synthesize_findings(matrix: dict[str, Any], drawing: dict[str, Any],
                         boundary: list[dict[str, float]]) -> list[dict[str, Any]]:
    area = Polygon([(point["x"], point["y"]) for point in boundary])
    centroid = area.centroid
    findings: list[dict[str, Any]] = []
    for spec in matrix["findings"]["synthetic"]:
        if spec["anchor"] is None:
            # Production ships EXIT_IMBALANCE with no region at all
            # (ExitBalanceFindingExtractor.java:68). Handing one to the engine
            # here would exercise a branch that never runs for real users.
            findings.append({"type": spec["type"], "region": None})
            continue
        half = float(spec["halfSize"])
        if spec["anchor"] == "exit":
            exits = drawing["exits"]
            if not exits:
                continue
            item = exits[min(int(spec["exitIndex"]), len(exits) - 1)]
            center = ((item["startX"] + item["endX"]) / 2.0, (item["startY"] + item["endY"]) / 2.0)
        else:
            center = (centroid.x, centroid.y)
        findings.append({
            "type": spec["type"],
            "region": {
                "startX": round(center[0] - half, 4), "startY": round(center[1] - half, 4),
                "endX": round(center[0] + half, 4), "endY": round(center[1] + half, 4),
            },
        })
    return findings


# --------------------------------------------------------------------------- #
# cells
# --------------------------------------------------------------------------- #


def load_matrix(path: pathlib.Path = DEFAULT_MATRIX) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def cell_ids(matrix: dict[str, Any]) -> list[str]:
    return [cell["id"] for cell in cells(matrix)]


def cells(matrix: dict[str, Any]) -> list[dict[str, Any]]:
    axes = matrix["axes"]
    order = ["A", "B", "C", "D", "E"]
    result: list[dict[str, Any]] = []
    for combination in _product([list(axes[name]["values"]) for name in order]):
        picked = dict(zip(order, combination))
        result.append({"id": "-".join(combination), **picked})
    return result


def _product(options: list[list[str]]) -> Iterator[tuple[str, ...]]:
    if not options:
        yield ()
        return
    for head in options[0]:
        for tail in _product(options[1:]):
            yield (head,) + tail


def build(matrix: dict[str, Any], cell: dict[str, Any], scale: str = "scaled") -> dict[str, Any]:
    """One cell as an engine input, ready for `layout_search.generate`."""
    axes = matrix["axes"]
    is_empty = cell["A"] == "empty"
    drawing = _empty_drawing() if is_empty else _load_default_drawing(matrix)

    _apply_structure_count(drawing, axes["B"]["values"][cell["B"]], is_empty)
    _apply_rotations(drawing, axes["C"]["values"][cell["C"]])

    for index, fabric in enumerate(drawing["fabrics"], start=1):
        fabric["id"] = index
    for index, item in enumerate(drawing["exits"], start=1):
        item["id"] = index

    boundary = _assemble_boundary(drawing["outsideWalls"])
    agent_count = int(matrix["scale"][scale]["agents"])
    hazards = _place_hazards(drawing, boundary, axes["E"]["values"][cell["E"]])
    agents = _routable_agents(
        drawing, boundary, axes["D"]["values"][cell["D"]], agent_count, hazards)

    return {
        "cellId": cell["id"],
        "drawing": {
            "outsideBoundary": boundary,
            "walls": drawing["walls"],
            "pillars": drawing["pillars"],
            "fabrics": drawing["fabrics"],
            "exits": drawing["exits"],
            "outsideWalls": drawing["outsideWalls"],
        },
        "agents": agents,
        "hazards": hazards,
        "selectedExitIds": [item["id"] for item in drawing["exits"]],
        "findings": _synthesize_findings(matrix, drawing, boundary),
        "parents": [],
        "constraints": None,
        "maxCandidates": 6,
        "round": 1,
    }


# --------------------------------------------------------------------------- #
# constraints
# --------------------------------------------------------------------------- #


def constraint_combos(matrix: dict[str, Any]) -> list[dict[str, Any]]:
    dimensions = matrix["constraintCombos"]["dimensions"]
    combos: list[dict[str, Any]] = []
    for bits in range(1 << len(dimensions)):
        flags = {name: bool(bits >> index & 1) for index, name in enumerate(dimensions)}
        label = "+".join(name for name in dimensions if flags[name]) or "none"
        combos.append({"id": label, **flags})
    return combos


def apply_constraints(engine_input: dict[str, Any], combo: dict[str, Any],
                      matrix: dict[str, Any]) -> dict[str, Any]:
    """Attach one constraint combination to an already built cell.

    Targets are picked by position, not by score, so the same combo always hits
    the same fabrics and a failure reproduces from the cell id alone.
    """
    spec = matrix["constraintCombos"]
    fabrics = engine_input["drawing"]["fabrics"]
    if not fabrics:
        return {**engine_input, "constraints": None, "constraintId": combo["id"]}

    count = min(int(spec["targetFabricCount"]), len(fabrics))
    targets = [fabrics[index] for index in range(0, len(fabrics), max(len(fabrics) // count, 1))][:count]
    target_ids = [str(fabric["id"]) for fabric in targets]

    constraints: dict[str, Any] = {
        "moveRadii": {}, "forbiddenZones": [], "rotationAllowed": {}, "wallAnchored": {},
    }
    if combo["fixed"]:
        constraints["moveRadii"] = {identifier: 0.0 for identifier in target_ids}
    if combo["noRotate"]:
        constraints["rotationAllowed"] = {identifier: False for identifier in target_ids}
    if combo["wallAnchor"]:
        constraints["wallAnchored"] = {identifier: True for identifier in target_ids}
    if combo["forbidden"]:
        size = float(spec["forbiddenZone"]["size"])
        points = [(point["x"], point["y"]) for point in engine_input["drawing"]["outsideBoundary"]]
        min_x = min(x for x, _ in points)
        min_y = min(y for _, y in points)
        max_x = max(x for x, _ in points)
        max_y = max(y for _, y in points)
        for index in range(int(spec["forbiddenZone"]["count"])):
            fraction = 0.25 + 0.4 * index
            constraints["forbiddenZones"].append({
                "x": round(min_x + (max_x - min_x) * fraction, 4),
                "y": round(min_y + (max_y - min_y) * 0.35, 4),
                "width": size, "height": size,
            })

    return {**engine_input, "constraints": constraints, "constraintId": combo["id"],
            "constraintTargets": [int(identifier) for identifier in target_ids]}


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=pathlib.Path, default=DEFAULT_MATRIX)
    parser.add_argument("--emit", type=pathlib.Path, help="셀별 도면 JSON을 이 디렉터리에 쓴다")
    parser.add_argument("--scale", choices=("scaled", "full"), default="scaled")
    parser.add_argument("--list", action="store_true")
    arguments = parser.parse_args()

    matrix = load_matrix(arguments.matrix)
    if arguments.list:
        for identifier in cell_ids(matrix):
            print(identifier)
        return

    if not arguments.emit:
        parser.error("--emit 또는 --list 중 하나가 필요합니다.")

    arguments.emit.mkdir(parents=True, exist_ok=True)
    for cell in cells(matrix):
        built = build(matrix, cell, arguments.scale)
        path = arguments.emit / f"{cell['id']}.json"
        with path.open("w", encoding="utf-8") as handle:
            json.dump(built, handle, ensure_ascii=False, indent=2)
        print(f"{cell['id']:<40} fabrics={len(built['drawing']['fabrics']):3d} "
              f"agents={len(built['agents']):4d} hazards={len(built['hazards'])} "
              f"findings={len(built['findings'])}")


if __name__ == "__main__":
    main()
