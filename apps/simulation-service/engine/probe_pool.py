"""Offline probe for the layout search candidate pool. Runs no JuPedSim trial.

Answers the question the E2E result cannot: when a search reports "no
improvement", was the pool empty, was it full of routing no-ops, or did the
ranker simply not surface the good candidate into the trial budget?

Takes the `input.json` the Java runner hands the engine (capture one by setting
`LAYOUT_SEARCH_KEEP_JOB_DIRECTORY=true`), and reports the raw pool the generator
can reach under EXHAUSTIVE generation next to what BOUNDED selection would
actually have tried.

    python probe_pool.py input.json [--max-candidates 2]
"""

from __future__ import annotations

import argparse
import collections
import json
import time
from typing import Any

import layout_search


def _routing_inert(candidate: dict[str, Any]) -> bool:
    """True when the move leaves every routing statistic untouched.

    A candidate the router cannot tell apart from the baseline cannot change
    evacuation time either, so these are provably wasted trials. This is the
    offline half of the 67% zero-delta rate measured on the training corpus.
    """
    metrics = (candidate.get("rationale", {}).get("surrogateContext", {}) or {}).get("searchMetrics") or {}
    pairs = (
        ("congestionBefore", "congestionAfter"),
        ("corridorCellsBefore", "corridorCellsAfter"),
        ("imbalanceBefore", "imbalanceAfter"),
    )
    return all(metrics.get(before) == metrics.get(after) for before, after in pairs)


def _fabric_ids(candidate: dict[str, Any]) -> tuple[Any, ...]:
    return tuple(op.get("fabricId") for op in candidate.get("ops", []))


def _histogram(title: str, counter: collections.Counter, total: int) -> None:
    print(f"\n{title}")
    if not counter:
        print("  (없음)")
        return
    for key, count in counter.most_common():
        share = f"{count / total:6.1%}" if total else "     -"
        print(f"  {str(key):<28} {count:4d}  {share}")


def _run(input_data: dict[str, Any], exhaustive: bool, max_candidates: int | None) -> tuple[dict[str, Any], float]:
    probe = dict(input_data)
    if exhaustive:
        probe["generationMode"] = layout_search.GENERATION_EXHAUSTIVE
        probe["exhaustive"] = True
    else:
        probe["generationMode"] = layout_search.GENERATION_BOUNDED
        probe["exhaustive"] = False
        if max_candidates is not None:
            probe["maxCandidates"] = max_candidates
    started = time.perf_counter()
    result = layout_search.generate(probe)
    return result, time.perf_counter() - started


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", help="엔진 input.json 경로")
    parser.add_argument(
        "--max-candidates",
        type=int,
        default=None,
        help="BOUNDED 비교 실행에 쓸 후보 상한 (미지정 시 input.json의 maxCandidates)",
    )
    args = parser.parse_args()

    with open(args.input, encoding="utf-8") as handle:
        input_data = json.load(handle)

    findings = input_data.get("findings", [])
    print("=" * 72)
    print(f"입력: {args.input}")
    print(f"  에이전트 {len(input_data.get('agents', []))}명 · 구조물 {len(input_data['drawing'].get('fabrics', []))}개 "
          f"· 출구 {len(input_data.get('selectedExitIds', []))}개 · 부모후보 {len(input_data.get('parents', []))}개")
    print(f"  진단 finding {len(findings)}개: {collections.Counter(f.get('type') for f in findings)}")

    exhaustive, exhaustive_seconds = _run(input_data, exhaustive=True, max_candidates=None)
    candidates = exhaustive["candidates"]
    total = len(candidates)

    print("\n" + "=" * 72)
    print("① Generator — EXHAUSTIVE 생성 (랭킹 없음, 도달 가능한 후보 전량)")
    print(f"  raw 후보 {exhaustive['rawCandidateCount']}개 · 생성 소요 {exhaustive_seconds:.1f}초")

    rejected = exhaustive.get("rejectedCounts", {})
    rejected_total = sum(rejected.values())
    print(f"  거부 {rejected_total}건")
    for reason, count in sorted(rejected.items(), key=lambda pair: -pair[1]):
        print(f"    {reason:<28} {count:4d}  {count / rejected_total:6.1%}")

    inert = [c for c in candidates if _routing_inert(c)]
    print("\n④ 무변화율 — 라우팅 통계가 baseline과 완전히 동일한 후보 (실행해도 의미 없음)")
    print(f"  {len(inert)}/{total} = {len(inert) / total:.1%}" if total else "  후보 없음")

    _histogram("operator별 후보 분포", collections.Counter(c["operatorType"] for c in candidates), total)
    _histogram(
        "구조물(fabricId)별 후보 분포 — 타겟이 몇 개로 좁혀졌는지",
        collections.Counter(_fabric_ids(c) for c in candidates),
        total,
    )
    _histogram(
        "이동 거리별 후보 분포",
        collections.Counter(c["rationale"].get("distanceMeters") for c in candidates),
        total,
    )

    print("\nproxy 상위 10개 (선정 순서. inert = 라우팅 무변화)")
    ranked = sorted(candidates, key=lambda c: -(c.get("proxyScore") or 0.0))
    zero_scores = sum(1 for c in candidates if not c.get("proxyScore"))
    for index, candidate in enumerate(ranked[:10], start=1):
        flag = " [inert]" if _routing_inert(candidate) else ""
        print(f"  {index:2d}. proxy={candidate.get('proxyScore'):<8} {candidate['operatorType']:<18} "
              f"fabric={_fabric_ids(candidate)} {candidate['rationale'].get('direction')}"
              f" {candidate['rationale'].get('distanceMeters')}{flag}")
    print(f"  proxy == 0 인 후보: {zero_scores}/{total}" + (f" = {zero_scores / total:.1%}" if total else ""))

    cap = args.max_candidates if args.max_candidates is not None else input_data.get("maxCandidates")
    if cap is not None:
        bounded, bounded_seconds = _run(input_data, exhaustive=False, max_candidates=int(cap))
        selected = bounded["candidates"]
        print("\n" + "=" * 72)
        print(f"② Ranker — BOUNDED 선정 (maxCandidates={cap}, 실제로 trial 돌 후보) · {bounded_seconds:.1f}초")
        selected_inert = sum(1 for c in selected if _routing_inert(c))
        for index, candidate in enumerate(selected, start=1):
            flag = " [inert]" if _routing_inert(candidate) else ""
            print(f"  {index:2d}. proxy={candidate.get('proxyScore'):<8} {candidate['operatorType']:<18} "
                  f"fabric={_fabric_ids(candidate)} {candidate['rationale'].get('direction')}"
                  f" {candidate['rationale'].get('distanceMeters')}{flag}")
        print(f"  선정 {len(selected)}개 중 무변화 {selected_inert}개")
        print(f"  → raw {exhaustive['rawCandidateCount']}개 중 {len(selected)}개만 실행됨 "
              f"({len(selected) / max(exhaustive['rawCandidateCount'], 1):.1%})")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
