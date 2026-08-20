"""Tier 1 of the layout-search E2E campaign: generation only, no JuPedSim.

Runs every matrix cell against every constraint combination and checks the
things that do not need a measured evacuation time - that the pool is not
empty, that no finding is silently dropped, that spans survive, and that
constraints actually filter. The defects fixed in SCRUM-98 all showed up here
and all of them were invisible from the API, so this is where they are cheapest
to catch again.

    python probe_matrix.py                  # 32 cells x 16 combos
    python probe_matrix.py --jobs 16 --cell default-many-mixed-skewed-some
"""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
import json
import pathlib
import time
from typing import Any

import constraints as constraints_module
import layout_search
import matrix_cells

# Findings the planner has no operator for contribute nothing by design, so
# "every finding contributes" only applies to the ones it can act on.
ACTIONABLE_FINDING_TYPES = frozenset(layout_search._PRIMARY_OPERATOR)

SPAN_TOLERANCE_METERS = 1e-6


def _spans(rect: dict[str, Any]) -> tuple[float, float]:
    return (
        abs(float(rect["endX"]) - float(rect["startX"])),
        abs(float(rect["endY"]) - float(rect["startY"])),
    )


def check_pool_not_empty(result: dict[str, Any], _: dict[str, Any]) -> str | None:
    if result["rawCandidateCount"] > 0:
        return None
    counts = result["rejectedCounts"]
    return f"원시 풀이 비었다 (거부: {counts or '없음'})"


def check_every_finding_contributes(result: dict[str, Any], engine_input: dict[str, Any]) -> str | None:
    if engine_input.get("constraints") and any(
            reason.startswith("CONSTRAINT_") for reason in result["rejectedCounts"]):
        # The user forbade somewhere and the generator obeyed. A finding with
        # nowhere legal left to move is the constraint working, not a lost
        # candidate, so this check only means something on an unconstrained run.
        return None
    actionable = {
        index for index, finding in enumerate(engine_input["findings"])
        if finding["type"] in ACTIONABLE_FINDING_TYPES
    }
    covered = {
        candidate["rationale"]["findingIndex"] for candidate in result["candidates"]
        if candidate["rationale"].get("findingIndex") is not None
    }
    missing = sorted(actionable - covered)
    if not missing:
        return None
    types = [engine_input["findings"][index]["type"] for index in missing]
    return f"후보를 하나도 못 낸 finding {missing} ({', '.join(types)})"


def check_no_invalid_geometry(result: dict[str, Any], _: dict[str, Any]) -> str | None:
    count = result["rejectedCounts"].get("INVALID_GEOMETRY", 0)
    return None if count == 0 else f"INVALID_GEOMETRY 거부 {count}건"


def check_spans_preserved(result: dict[str, Any], _: dict[str, Any]) -> str | None:
    """A move or a rotation must not resize the structure.

    This is the inverted-rectangle class of defect: the width silently flips
    sign, the far corner is re-derived from a normalized bounding box, and the
    candidate comes out geometrically valid but physically wrong.
    """
    for candidate in result["candidates"]:
        for op in candidate["ops"]:
            before, after = op.get("before"), op.get("after")
            if not before or not after:
                continue
            width_before, height_before = _spans(before)
            width_after, height_after = _spans(after)
            # No swap allowance: rotation is stored as an angle, so `_spans_match`
            # in the engine demands the stored width and height come out
            # decimal-identical even on a quarter turn. Accepting a swap here
            # would hide exactly the defect this check exists for.
            if (abs(width_after - width_before) > SPAN_TOLERANCE_METERS
                    or abs(height_after - height_before) > SPAN_TOLERANCE_METERS):
                return (f"{op['type']} fabric={op.get('fabricId')} 크기 변형 "
                        f"{width_before:.4f}x{height_before:.4f} -> {width_after:.4f}x{height_after:.4f}")
    return None


def check_constraints_honoured(result: dict[str, Any], engine_input: dict[str, Any]) -> str | None:
    raw = engine_input.get("constraints")
    if not raw:
        return None
    parsed = constraints_module.parse_constraints(raw)
    walls = engine_input["drawing"]["walls"]

    for candidate in result["candidates"]:
        for op in candidate["ops"]:
            fabric_id = op.get("fabricId")
            before, after = op.get("before") or {}, op.get("after") or {}
            if parsed.move_radius_of(fabric_id) == 0.0:
                return f"고정된 fabric={fabric_id}이(가) {op['type']}로 움직였다"
            if not parsed.rotation_allowed_of(fabric_id):
                if abs(float(after.get("rotation", 0.0)) - float(before.get("rotation", 0.0))) > 1e-9:
                    return f"회전 금지된 fabric={fabric_id}이(가) 회전했다"
            if parsed.is_wall_anchored(fabric_id) and after:
                if not constraints_module.touches_wall({**before, **after}, walls):
                    return f"벽 고정된 fabric={fabric_id}이(가) 벽에서 떨어졌다"
            if after and parsed.intersects_forbidden_zone(
                    constraints_module._fabric_geometry({**before, **after})):
                return f"fabric={fabric_id}이(가) 금지구역 안에 놓였다"
    return None


CHECKS = (
    ("P2-pool", check_pool_not_empty),
    ("P2-findings", check_every_finding_contributes),
    ("P3-geometry", check_no_invalid_geometry),
    ("P5-span", check_spans_preserved),
    ("P6-constraints", check_constraints_honoured),
)


def probe(engine_input: dict[str, Any]) -> dict[str, Any]:
    payload = dict(engine_input)
    payload["generationMode"] = layout_search.GENERATION_EXHAUSTIVE
    payload["exhaustive"] = True
    payload.pop("cellId", None)
    payload.pop("constraintId", None)
    payload.pop("constraintTargets", None)

    started = time.perf_counter()
    result = layout_search.generate(payload)
    elapsed = time.perf_counter() - started

    failures = []
    for name, check in CHECKS:
        try:
            message = check(result, engine_input)
        except Exception as error:  # a crashing check is itself a finding
            message = f"검사가 예외로 죽었다: {type(error).__name__}: {error}"
        if message:
            failures.append({"check": name, "message": message})

    return {
        "cell": engine_input["cellId"],
        "constraint": engine_input.get("constraintId", "none"),
        "poolSize": result["rawCandidateCount"],
        "rejected": result["rejectedCounts"],
        "seconds": round(elapsed, 2),
        "operators": dict(collections.Counter(
            candidate["operatorType"] for candidate in result["candidates"])),
        "failures": failures,
    }


def self_test() -> None:
    """Every check must actually fire. A check that can only pass is decoration.

    Feeds each one a result that violates exactly what it looks for, then a
    clean one, and asserts it flags the first and stays quiet on the second.
    """
    drawing = {"walls": [{"startX": 0.0, "startY": 0.0, "endX": 10.0, "endY": 0.0}]}
    findings = [{"type": "BOTTLENECK", "region": {"startX": 0, "startY": 0, "endX": 5, "endY": 5}}]
    base_input = {"drawing": drawing, "findings": findings, "constraints": None}

    def op(fabric_id, before, after, kind="CLEAR_CORRIDOR"):
        return {"type": kind, "fabricId": fabric_id, "before": before, "after": after}

    def result(ops, pool=1, rejected=None, finding_index=0):
        return {
            "rawCandidateCount": pool,
            "rejectedCounts": rejected or {},
            "candidates": [{"operatorType": "CLEAR_CORRIDOR", "ops": ops,
                            "rationale": {"findingIndex": finding_index}}],
        }

    square = {"startX": 1.0, "startY": 1.0, "endX": 3.0, "endY": 3.0, "rotation": 0.0}
    moved = {"startX": 2.0, "startY": 1.0, "endX": 4.0, "endY": 3.0, "rotation": 0.0}
    stretched = {"startX": 1.0, "startY": 1.0, "endX": 4.0, "endY": 3.0, "rotation": 0.0}
    turned = {**moved, "rotation": 45.0}

    assert check_pool_not_empty(result([], pool=0), base_input)
    assert check_pool_not_empty(result([op(1, square, moved)]), base_input) is None

    assert check_every_finding_contributes(result([], finding_index=1), base_input)
    assert check_every_finding_contributes(result([], finding_index=0), base_input) is None
    constrained = {**base_input, "constraints": {"forbiddenZones": [{"x": 0, "y": 0, "width": 1, "height": 1}]}}
    blocked = result([], rejected={"CONSTRAINT_ZONE": 7}, finding_index=1)
    assert check_every_finding_contributes(blocked, constrained) is None
    assert check_every_finding_contributes(result([], rejected={"OVERLAP": 7}, finding_index=1), constrained)

    assert check_no_invalid_geometry(result([], rejected={"INVALID_GEOMETRY": 2}), base_input)
    assert check_no_invalid_geometry(result([], rejected={"OVERLAP": 9}), base_input) is None

    assert check_spans_preserved(result([op(1, square, stretched)]), base_input)
    assert check_spans_preserved(result([op(1, square, moved)]), base_input) is None

    fixed = {**base_input, "constraints": {"moveRadii": {"1": 0.0}}}
    assert check_constraints_honoured(result([op(1, square, moved)]), fixed)
    assert check_constraints_honoured(result([op(2, square, moved)]), fixed) is None

    locked = {**base_input, "constraints": {"rotationAllowed": {"1": False}}}
    assert check_constraints_honoured(result([op(1, square, turned)]), locked)
    assert check_constraints_honoured(result([op(1, square, moved)]), locked) is None

    anchored = {**base_input, "constraints": {"wallAnchored": {"1": True}}}
    off_wall = {"startX": 4.0, "startY": 4.0, "endX": 6.0, "endY": 6.0, "rotation": 0.0}
    assert check_constraints_honoured(result([op(1, square, off_wall)]), anchored)

    banned = {**base_input,
              "constraints": {"forbiddenZones": [{"x": 0.0, "y": 0.0, "width": 5.0, "height": 5.0}]}}
    assert check_constraints_honoured(result([op(1, square, moved)]), banned)
    outside = {"startX": 20.0, "startY": 20.0, "endX": 22.0, "endY": 22.0, "rotation": 0.0}
    assert check_constraints_honoured(result([op(1, square, outside)]), banned) is None

    print(f"검사 {len(CHECKS)}종 자체 점검 통과")


def _job(packed: tuple[dict[str, Any], dict[str, Any], dict[str, Any]]) -> dict[str, Any]:
    matrix, cell, combo = packed
    built = matrix_cells.build(matrix, cell, scale="scaled")
    return probe(matrix_cells.apply_constraints(built, combo, matrix))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=pathlib.Path, default=matrix_cells.DEFAULT_MATRIX)
    parser.add_argument("--jobs", type=int, default=8)
    parser.add_argument("--cell", action="append", help="이 셀만 (여러 번 지정 가능)")
    parser.add_argument("--constraint", action="append", help="이 제약 조합만")
    parser.add_argument("--report", type=pathlib.Path,
                        help="결과를 한 줄에 하나씩(JSONL) 쓸 경로. 구성이 끝날 때마다 즉시 기록한다")
    parser.add_argument("--resume", action="store_true",
                        help="--report 파일에 이미 있는 구성은 건너뛴다")
    parser.add_argument("--self-test", action="store_true", help="검사들이 실제로 발화하는지만 확인한다")
    arguments = parser.parse_args()

    if arguments.self_test:
        self_test()
        return

    matrix = matrix_cells.load_matrix(arguments.matrix)
    selected_cells = [
        cell for cell in matrix_cells.cells(matrix)
        if not arguments.cell or cell["id"] in arguments.cell
    ]
    selected_combos = [
        combo for combo in matrix_cells.constraint_combos(matrix)
        if not arguments.constraint or combo["id"] in arguments.constraint
    ]
    work = [(matrix, cell, combo) for cell in selected_cells for combo in selected_combos]

    # A full sweep runs for hours. Finished rows are appended as they land so a
    # crash, a reboot, or a Ctrl-C costs one configuration instead of the lot.
    rows = _previous_rows(arguments.report) if arguments.resume else []
    if rows:
        already = {(row["cell"], row["constraint"]) for row in rows}
        skipped = len(work)
        work = [item for item in work if (item[1]["id"], item[2]["id"]) not in already]
        print(f"이어서 실행: 이미 끝난 {skipped - len(work)}개 건너뜀")

    print(f"셀 {len(selected_cells)}개 x 제약 {len(selected_combos)}개, 남은 구성 {len(work)}개, "
          f"동시 {arguments.jobs}개\n")

    started = time.perf_counter()
    with concurrent.futures.ProcessPoolExecutor(max_workers=arguments.jobs) as pool:
        for done, row in enumerate(pool.map(_job, work), start=1):
            rows.append(row)
            _append_row(arguments.report, row)
            mark = "FAIL" if row["failures"] else "ok  "
            print(f"[{done:4d}/{len(work)}] {mark} {row['cell']:<40} {row['constraint']:<38} "
                  f"pool={row['poolSize']:4d} {row['seconds']:7.1f}s", flush=True)
            for failure in row["failures"]:
                print(f"          -> {failure['check']}: {failure['message']}", flush=True)

    _summarize(rows, time.perf_counter() - started)
    if arguments.report:
        print(f"\n결과: {arguments.report}")


def _previous_rows(report: pathlib.Path | None) -> list[dict[str, Any]]:
    if report is None or not report.exists():
        return []
    rows = []
    with report.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def _append_row(report: pathlib.Path | None, row: dict[str, Any]) -> None:
    if report is None:
        return
    report.parent.mkdir(parents=True, exist_ok=True)
    with report.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def _summarize(rows: list[dict[str, Any]], elapsed: float) -> None:
    failed = [row for row in rows if row["failures"]]
    print(f"\n{'=' * 78}")
    print(f"구성 {len(rows)}개, 실패 {len(failed)}개, {elapsed:.1f}초")

    signatures = collections.Counter(
        (failure["check"], failure["message"].split("(")[0].strip())
        for row in failed for failure in row["failures"]
    )
    if signatures:
        # Repeated signatures are the pause-the-campaign trigger: two or more
        # cells sharing one means the cause is not that cell's configuration.
        print("\n실패 시그니처 (2건 이상이면 캠페인 일시정지 대상)")
        for (check, message), count in signatures.most_common():
            flag = "  <-- 반복성" if count >= 2 else ""
            print(f"  {count:4d}  {check:<16} {message[:70]}{flag}")

    rejects: collections.Counter = collections.Counter()
    for row in rows:
        rejects.update(row["rejected"])
    if rejects:
        print("\n거부 사유 합계")
        for reason, count in rejects.most_common():
            print(f"  {reason:<28} {count}")

    empty = [row for row in rows if row["poolSize"] == 0]
    if empty:
        print(f"\n풀이 빈 구성 {len(empty)}개")
        for row in empty[:20]:
            print(f"  {row['cell']} / {row['constraint']}")


if __name__ == "__main__":
    main()
