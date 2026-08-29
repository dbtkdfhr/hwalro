# 인계: 배치 개선안 엔진 (E3 / configuration space)

작성 2026-08-29 · 브랜치 `refactor/SCRUM-152`
상태: **E3가 런타임 기본 플래너. 동작하지만 느리고, 목적함수에 정확도 결함이 있다.**

---

## 0. 30초 요약

배치 개선안 엔진은 3세대다.

| 세대 | 플래너 모드 | 상태 |
| --- | --- | --- |
| E1 | `DIAGNOSTIC_BEAM` | 레거시. 명시 호출로만 도달. Java enum에 없어 서비스에서는 불가 |
| E2 | `IDEAL_ROUTE_DOCKING` | 레거시. 명시 호출로만 도달 |
| **E3** | **`CONFIGURATION_SPACE_SHAPE_GRID`** | **기본값**. `..._PDE`는 선택 |

`plannerMode`가 없거나 모르는 값이면 `UnknownPlannerModeError`로 즉시 실패한다. 예전처럼
E1으로 조용히 떨어지지 않는다.

**남은 일은 세 갈래다. 순서대로 하는 것을 권한다.**

- **A. 성능** — 실측 프로파일 기준 런타임의 92%가 함수 하나에 있다. 답이 바뀌지 않는 수정.
- **B. 정확도** — 목적함수가 큰 집기를 점으로 취급해 방해량을 과소평가한다. 답이 바뀐다.
- **C. 탐색 범위** — 이동 상한이 임의 상수다. 답이 바뀐다.

A를 먼저 해야 B·C를 실측으로 검증할 수 있다. B를 고쳐야 C의 효과가 제대로 보인다.

---

## 1. 배치 공간(configuration space)이 무엇인지

집기 하나를 어디에 놓을지는 `(x, y, 각도)` 세 숫자로 완전히 정해진다. 그 세 숫자가 만드는
공간이 배치 공간이고, **배치 하나 = 점 하나**다. 이렇게 보면 집기는 점으로 줄어들고 장애물이
집기 크기만큼 부풀어난다. "도형끼리 겹치나"가 "이 점이 금지 구역인가"로 바뀐다.

`apps/simulation-service/engine/configuration_space.py`:

| 심볼 | 역할 |
| --- | --- |
| `Pose(x, y, theta)` | 배치 공간의 점 하나 |
| `RectangleFootprint` | 집기 크기. 점을 도형으로 되돌릴 때만 필요 |
| `rectangle_at(footprint, pose)` | 점 → 실제 도형 |
| `ConfigurationSpace` | 무엇이 금지인지 (외벽·기둥·벽·출구앞·이동정책) |
| `is_feasible(space, pose)` | 이 점이 자유 공간인가 |
| `project_pose(space, proposal)` | 금지 구역에 떨어진 점을 원위치 방향으로 이분탐색(48회)해 되살림 |
| `adaptive_angles` | 회전축 샘플링 간격 |

집기가 여러 개면 차원이 곱해진다(2개 = 6차원). 그래서 E3는 여러 집기를 동시에 옮기는
**하나의 원자적 배치**를 다룰 수 있다.

탐색은 이렇게 돈다:

1. `ideal_flow.build_ideal_flow` — 이동 가능한 집기를 모두 제거한 이상 도면에서 에이전트별
   최단경로를 구하고, 경로를 토막(`FlowEdge`)으로 쪼개고, 이상경로를 막는 집기 묶음
   (`blocker_hyperedge`)을 만든다.
2. `shape_sensitivity.shape_signals` — 집기를 조금씩 밀고 돌려보며 "어느 방향으로 가면
   방해가 줄어드는지"를 힘/토크로 만든다. 유한차분이라 집기당 **7회** 방해량 계산이 든다.
3. `configuration_space_optimizer.optimize` — 그 힘을 따라 배치 공간을 내려간다. 벽에
   부딪히면 `project_pose`가 되돌린다. 서로 다른 좋은 점들을 Pareto 아카이브에 모은다.
4. `configuration_space_shape_planner.generate_candidates` — blocker 그룹을 우선순위대로
   돌며 후보를 만들고, `_interleave`로 그룹 간 라운드로빈 배분한다.

---

## 2. 작업 A — 성능 (답이 바뀌지 않음)

### 근거

B2 크기 도면(146×100 m)에서 집기 수를 늘리며 측정. 생성 예산 240초:

```text
fabrics=  1   13.4s  candidates=0
fabrics= 25  132.0s  candidates=6
fabrics=100  256.7s  candidates=6
fabrics=385  299.7s  candidates=2   ← 예산 소진으로 잘림
```

`fabrics=385` cProfile (274초 중):

```text
253.3s (92%)  shape_sensitivity.py:33  _edge_obstruction     4,336,794회
117.2s         └ shapely LineString 생성                      4,346,275회
 65.7s         └ distance()
 28.8s         └ centroid()                                  4,337,388회
```

실제 운영 기록도 같은 방향이다. layout "ccc"(집기 33개)에서 생성 단계만:

- search #6 — 14:24:20 시작, 첫 trial 14:30:07 → **약 5.8분**
- search #7 — 14:54:13 시작, 첫 trial 15:01:06 → **약 6.9분**

실측(JuPedSim) trial 자체는 6회에 111초로 빠르다. **생성이 병목이고 이미 10분 예산에
근접했다.** 더현대 B2는 집기가 385개라 예산을 다 쓰고도 후보가 모자랄 것이다.

### 왜 그렇게 느린가

`_obstruction(geometry, edges, clearance)`는 집기 하나를 어떤 자리에 놓고 **모든 토막**을
순회한다. 그런데 그 루프 동안 집기도 토막도 변하지 않는데 매번 다시 계산한다.

```python
def _edge_obstruction(geometry, edge, clearance) -> float:
    ...
    center = geometry.centroid                                     # (2)
    distance = LineString((edge.start, edge.end)).distance(center)  # (1)
    if distance >= 6.0 * clearance:
        return 0.0                                                 # (3)
```

### 고칠 것 셋

1. **`FlowEdge`가 자기 `LineString`을 미리 들고 있게 한다.**
   토막의 좌표는 `build_ideal_flow` 이후 절대 변하지 않는데 434만 번 새로 조립한다.
   `ideal_flow.FlowEdge`에 미리 만든 `LineString`을 담고 재사용.
   → 프로파일상 **117.2초**.

2. **`centroid`를 루프 바깥에서 한 번만 구한다.**
   `_obstruction` 진입 시 한 번 계산해 `_edge_obstruction`에 넘긴다.
   → 프로파일상 **28.8초**.

3. **STRtree로 반경 밖 토막을 아예 꺼내지 않는다.**
   `clearance = GRID_STEP_METERS = 0.25`이므로 컷오프는 `6 × 0.25 = 1.5 m`. 이 값은 물리
   규칙이 아니라 종 모양 감쇠(`exp(-0.5 * (d/clearance)**2)`)의 **6σ 지점**이고, 거기서
   proximity는 `1.5e-8`이라 결과에 영향이 없다. 지금은 "계산해서 0인 걸 확인하고 버리는"
   구조다. `build_ideal_flow`에서 토막들로 `shapely.STRtree`를 한 번 만들고 집기 주변
   1.5 m만 질의하면 나머지는 호출조차 하지 않는다.

**셋 다 반환값이 동일하다.** 기존 엔진 테스트가 그대로 통과하는지로 검증하면 된다.

---

## 3. 작업 B — 목적함수 정확도 (답이 바뀜)

### 문제

`shape_sensitivity.py`의 거리 계산이 집기의 **무게중심 점 하나**를 쓴다. 즉 집기를 크기
없는 점으로 취급한다. 작은 집기는 괜찮지만 큰 집기에서 무너진다.

fabric 741(실제 데이터, 3.2 m × 26.3 m)로 측정한 값:

```text
경로 토막 위치           집기와 실제 거리   중심까지 거리   proximity    판정
집기 정중앙을 관통             0.00 m         0.05 m     9.80e-01   계산됨
집기 위쪽 끝을 관통            0.00 m        11.35 m     0.00e+00   무시됨
집기 아래쪽 끝을 관통          0.00 m        11.65 m     0.00e+00   무시됨
```

세 경우 모두 경로가 집기를 **실제로 관통**하는데(실제 거리 0.00 m), 끝부분을 지나는 경로는
완전히 무시된다. 26 m짜리 집기가 **가운데 3 m 구간만 길을 막는 것으로 취급**된다.

### 이게 설명하는 증상

- search #7의 후보 6개가 **proxy_score 83.973338로 전부 동일**했다. 중심만 1.5 m 밖으로
  빼면 "방해량 0"이 되어, 그 뒤로는 뭘 해도 점수가 같다. 실제로는 집기 몸통이 여전히 길을
  막고 있다. 결과적으로 실측 6회를 순위 없는 동점 후보에 쓴다.
- 집기를 조금만 밀어도 "해결됐다"고 판단한다.

### 고칠 것

거리를 무게중심이 아니라 **집기 도형 자체**까지로 바꾼다. Shapely `distance`는 도형끼리도
계산한다(겹치면 0). 그러면 집기 어느 부분이 막든 잡힌다.

컷오프 `6.0 * clearance`의 의미도 함께 재검토할 것. 지금은 "중심에서 1.5 m"지만 도형 거리로
바꾸면 "표면에서 1.5 m"가 되어 의미가 달라진다.

**주의: 기존 테스트의 기댓값이 바뀐다.** `test_v3_flow_sensitivity.py`의 수치 오라클을
다시 계산해야 하고, 실측으로 검증해야 한다.

---

## 4. 작업 C — 탐색 범위 상한 (답이 바뀜)

`configuration_space_optimizer.py`의 `_fallback_domain`:

```python
translation_span = max(request.steps.translation * 40.0, request.tolerances.pose * 20.0)
```

`_optimize_group`이 `OptimizationRequest`에 `domain`을 넘기지 않으므로 **항상** 이 함수가
쓰인다. 즉 어떤 집기도 원위치 ±`translation_span`을 벗어난 배치를 제안할 수 없다.

### 경위

- 원래 계수는 `4.0`이라 상한이 **2 m**였다. 그 결과 26 m짜리 fabric 741을 **0.74 m**만 밀고
  `refinement_complete`(수렴 완료)로 종료했다. 남은 힘은 `forceY = -215.9`, 방해량은
  `114.7`로 전혀 해소되지 않은 상태였다. 이것이 search #6 / 시뮬 20이다.
- 계수를 `40.0`(상한 20 m)으로 올리자 search #7에서 후보 6개가 모두 실측 개선 확인,
  이동량 9.7~19.9 m로 개선됐다. **이 변경은 되돌리지 말 것.**

### 그래도 남은 문제

search #7의 candidate 20이 **19.88 m** 이동했다. 상한 20 m에 밀착한 것이라 **여전히 걸리고
있다.** 더현대 B2는 146×100 m라 20 m로도 모자랄 가능성이 높다. `×4`든 `×40`이든 같은 종류의
임의 상수다.

### 고칠 것

이동 한계를 스텝 크기의 배수가 아니라 **실제 기하에서 유도**한다. 도면 경계, 그리고
이동정책(`WITHIN_ZONE`이면 지정 구역)이 허용하는 범위. 그러면 작은 방에서는 자동으로 좁아져
탐색이 빨라지고, B2에서는 자동으로 넓어진다. 애초에 배치 공간을 만든 이유가 이것이다.

**주의:** 범위를 넓히면 표본 대부분이 다른 집기 위에 떨어져 `project_pose` 호출이 늘고
느려진다. 그래서 작업 A를 먼저 해야 한다.

---

## 5. 그 밖의 열린 항목

- **회전 중복 후보.** search #7의 candidate 18과 19가 좌표는 같고 회전만 `8.64°` / `351.36°`
  (= ±8.64°)다. 거의 같은 배치에 실측 1회를 낭비한다. 직사각형은 θ와 θ+180이 같은 도형이므로
  Pareto 중복 판정(`configuration_space_pareto.py`)에 이를 반영할 것.
- **T7 실측 승격 게이트 미실행.** E3가 E2보다 대피시간 기준으로 낫다는 것은 아직 측정되지
  않았다. 기본값 승격은 게이트 통과가 아니라 브랜치 목표에 따른 결정이었다.
- **`AGENT_PLACEMENT_FAILED`** (아래 부록 A5). E3의 `_project`는 pose 실현가능성과 상호
  비겹침만 본다. 초기 인원 배치 검증이 없어 실측 예산을 낭비한다. Python에서 새로 흉내내지
  말고 Java `SimulationGeometry`의 공식 준비 경로를 후보 등록 전에 재사용할 것.
- **Java 진단 findings가 E3에 전달되지 않는다.** `GridPlannerRequest`에 필드가 없다. E3는
  자체 `(-delay, -demand)` 순위를 쓴다. 부록 A4가 제안한 방향과 다른 해법이며 미검증.
- **호출되지 않는 E1 잔재(정리 대상, 동작에는 무해).** `beamWidth` 프로퍼티 미사용,
  `LayoutSearchOrchestrator`의 `int rounds = 1;` 하드코딩으로 2라운드 빔 확장 블록 도달 불가,
  `application.yml`의 `rounds: 2`는 무효, `probe_pool.py`/`probe_matrix.py`/`matrix_cells.py`
  1,046줄 미참조, `CandidateSelector.judge`의 `requireExitBalanceImprovement` 파라미터는
  본문에서 읽히지 않음.

---

## 6. 환경 주의사항

- **`pnpm reset`을 실행하지 말 것.** `docker compose down -v`라서 DB 볼륨을 삭제한다.
  search #5(E2) / #6(E3, 상한 2 m) / #7(E3, 상한 20 m)은 **모두 같은 기준 시뮬 17**에서 나온
  유일한 3자 비교 기록이다. 날리면 재현 불가.
- **Python 엔진은 빌드가 없다.** `LayoutSearchRunner`가 매 탐색마다 `engine/layout_search.py`를
  서브프로세스로 새로 띄운다. 저장하면 다음 탐색부터 반영된다.
- **Java 설정 변경은 Gradle 산출물 갱신이 필요하다.** `build/resources/main/application.yml`이
  소스와 같은지 확인할 것.
- **기준 도면.** 더현대 B2 = `layouts.id = 1`, 집기 **385개**, 146.2 × 99.8 m.
  테스트에 쓰던 "ccc" = `layouts.id = 6`, 집기 33개, 123.3 × 77.9 m.

---

## 7. 재현 명령

`apps/simulation-service/engine`에서:

```bash
.venv/Scripts/python.exe -m pytest -q --ignore=train
```

PDE 통합 테스트가 느리다(약 3.5분). 빠르게 돌리려면:

```bash
.venv/Scripts/python.exe -m pytest -q --ignore=train --deselect test_layout_search_v3_integration.py
```

V2 대 V3 벤치마크 비교:

```bash
.venv/Scripts/python.exe -m pytest tests/test_v3_benchmark_harness.py -q
```

시뮬레이션 서비스:

```bash
./gradlew test
```

성능 프로파일은 `cProfile`로 `layout_search.generate`를 감싸 `tottime` 정렬로 보면
`_edge_obstruction`이 바로 드러난다.

---

## 8. 이번 세션에서 이미 끝난 것

- T8: PDE 평가기를 플래너에 배선. 목적함수 메모이제이션(44.2s → 29.9s).
- E1 조용한 폴백 제거 → `UnknownPlannerModeError`.
- E2를 기본값에서 제거, 기본값을 `CONFIGURATION_SPACE_SHAPE_GRID`로.
- `_interleave`: 실측 예산을 blocker 그룹 간 라운드로빈 배분(이전에는 최상위 그룹 하나가
  예산을 전부 소진).
- 회전 출력 `% 360.0` 정규화(무회전이 `360.0`으로 기록되던 문제).
- 벤치마크 코퍼스 정상화: 집기가 이상경로 위에 없어 무의미하던 픽스처 3개 재설계.
- 하네스의 무효한 교차 플래너 proxy 비교를 플래너 중립 관측치로 교체.
- 프론트엔드에 E3의 `IDEAL_FLOW` / `CONFIGURATION_SPACE_SHAPE` 라벨 추가.

근거 문서:

- `.omo/evidence/v3-no-legacy-fallback-and-default.md`
- `.omo/evidence/t8-v3-pde-integration.md`
- `.omo/evidence/v3-benchmark-report.json`
- `.omo/plans/configuration-space-shape-optimization-v3.md`

**아직 커밋하지 않았다.** 작업 트리에 수정 17개, 미추적 파일 28개가 있다.

---
---

# 부록: E2(`IDEAL_ROUTE_DOCKING_V2`) 시절 기록

> 2026-08-28 작성. 아래 A4(병목 우선순위)는 E3가 다른 방식으로 대체했고,
> A5(초기 인원 배치)는 **여전히 미해결**이다.

## A0. 30초 요약

이상경로를 막는 집기를 경로 법선 방향으로 밀고, 긴 변을 경로와 평행하게 회전한 뒤 더 밀 수 있으면 다시 한계까지 이동하는 후보 생성기를 구현했다. 실측한 후보는 개선 여부와 관계없이 배치·지표·결과 시뮬레이션을 볼 수 있고, 정식 추천은 총 대피시간·평균 대피시간·균형 부문별 최고 하나씩 최대 3개로 구분된다.

아직 가장 중요한 문제가 남았다. 후보 구조물 세트 순위가 병목 진단을 사용하지 않고 정적 이상경로 절감량만 사용한다. 다음 작업은 **병목 구간과 겹치는 이상경로의 구조물 세트를 먼저 실측하도록 우선순위를 결합하는 것**이다.

이번 작업 커밋:

- `f6e78e48` `feat(SCRUM-152): 이상경로 법선 투영 배치 생성`
- `ecc09f45` `feat(SCRUM-152): 실측 후보 결과 보존`
- `e283c004` `feat(SCRUM-152): 배치 탐색 실측 비교 흐름 개선`

---

## A1. 현재 구현된 탐색 원리

파일:

- `apps/simulation-service/engine/ideal_route_docking.py`
- `apps/simulation-service/engine/ideal_route_docking_placement.py`

현재 후보 생성 순서:

1. 이동 가능한 집기를 모두 제거한 이상 도면에서 에이전트별 최단 경로 중심선을 계산한다.
2. 각 이상 경로와 겹치는 집기 집합을 이동 단위로 묶는다.
3. 집기와 만나는 경로의 국소 접선을 찾고 양쪽 법선을 계산한다.
4. 각 법선 방향으로 벽·구역·장애물 충돌 직전까지 집기를 민다.
5. 긴 변을 경로와 평행하게 회전할 수 있으면 회전하고 같은 법선 방향으로 다시 한계까지 민다.
6. 모든 이상 경로와의 간섭 면적, 이동거리 순으로 배치를 정렬한다.
7. 동일 구조물 집합의 위치 변형을 반복하기 전에 서로 다른 구조물 집합을 실측 예산에 배정한다.

`test_ideal_route_docking.py`가 양쪽 법선 최대 이동, 회전 후 추가 이동, 후보 집합 다양성, 크기 보존과 제약 준수를 고정한다.

---

## A2. 실측 후보와 정식 추천의 구분

숫자 두 개를 혼동하지 말 것.

- **실측 예산 최대 6개:** 후보의 실제 시뮬레이션을 돌려 비교 데이터를 확보한다.
- **정식 추천 최대 3개:** `CandidateSelector.selectRecommendations`가 아래 부문별 최고 하나를 고른다.
  - 총 대피시간 감축 최고
  - 평균 대피시간 감축 최고
  - 두 지표의 균형 최고
- 같은 후보가 여러 부문 최고면 하나의 추천안에 추천 유형이 합쳐지므로 실제 추천 개수는 1~3개다.

프론트엔드는 `추천 개선안`과 `실측 비교 기록`을 별도 레이블로 표시한다. 추천 밖의 후보도 숨기지 않지만 `개선안`이라고 부르지 않는다.

---

## A3. 실측 결과 보존과 취소

파일:

- `CandidateTrialService.java`
- `LayoutSearchQueryService.java`
- `VerifiedCandidateMaterializer.java`
- `LayoutSearchReplyThread.tsx`
- `SimulationListPage.tsx`

정상 종료된 trial은 개선 여부와 관계없이 엔진 결과를 완료 시뮬레이션으로 저장한다. 따라서 악화·변화 없음 후보도 타임라인, 히트맵과 공식 지표 결과 화면을 열 수 있다. 엔진 실행 자체가 실패한 후보만 결과 시뮬레이션이 없다.

진행 중 탐색은 시뮬레이션 목록의 배치 개선안 스레드 헤더에서 취소할 수 있다. 취소 트랜잭션이 후보를 `FAILED / SEARCH_CANCELLED`로 바꾸며, 프론트는 이를 `취소됨`으로 표시한다. 이미 실행 중이던 trial이 늦게 끝나더라도 후보 상태를 다시 확인한 뒤 물질화하므로 취소 후 결과 시뮬레이션이 생기지 않는다.

현재 엔진 프로세스를 즉시 강제 종료하는 레지스트리는 없다. 취소는 검색 상태와 남은 후보를 즉시 종료하지만, 이미 시작한 Python 프로세스는 자체 종료 시점까지 잠시 자원을 사용할 수 있다. 실제 프로세스 중단까지 필요하면 `SimulationEngineRunner`에 job별 프로세스 등록·취소 계약을 별도 설계해야 한다.

---

## A4. 확인된 미완료 문제: 병목 우선순위

2026-08-28에 관찰한 탐색에서는 병목 구역이 대략 `x=58~119, y=41~62`에 네 구간으로 이어졌지만, 6개 후보 중 일부는 병목에서 멀리 떨어진 단일 집기만 이동했다. 병목 근처 후보도 여러 구조물 세트가 아니라 집기 하나씩이었다.

원인은 명확하다.

- `ideal_route_docking.py`의 그룹 순위는 같은 blocker id 집합별 `route.saving` 합이다.
- `route.saving`은 현재 경로와 이동 집기를 제거한 이상 경로 사이의 정적 거리 차이다.
- Java에서 계산한 `diagnosis.findings[].region / severity / duration`은 Python 도킹 후보 순위에 전달·사용되지 않는다.
- 따라서 실제 병목을 넓히는 구조물 세트보다 정적 거리를 조금 줄이는 집합이 먼저 실측될 수 있다.

다음 구현의 권장 방향:

1. 진단 병목 region과 이상 경로 중심선이 겹치는 구간을 찾는다.
2. 그 구간에서 이상 경로와 겹치는 집기들의 집합을 병목 단위 blocker set으로 만든다.
3. 우선순위를 `병목 지속시간 또는 심각도 × 해당 경로 에이전트 수 × 이상경로 절감량`으로 계산한다.
4. 실측 예산 6개에는 병목별 blocker set을 먼저 하나씩 배정한다.
5. 병목 세트를 모두 배정한 뒤에만 같은 세트의 반대 법선·회전 변형을 추가한다.
6. 공식 채택 판정은 계속 총 대피시간과 평균 대피시간 실측값만 사용한다. 병목 값은 후보 생성·우선순위 프록시일 뿐 공식 개선 지표가 아니다.

정확한 가중치는 임의 상수로 시작하지 말고 최신 실제 검색 입력을 재생해 순위 변화부터 표로 확인할 것.

---

## A5. 확인된 실패 유형: 초기 인원 배치 불가

후보 `#68`에서 관찰한 실패:

```
AGENT_PLACEMENT_FAILED: 변경한 배치에 사람을 배치할 수 없습니다.
```

당시 구조물 `#1014`를 `(121.72, 55.70) → (122.86, 54.56)`으로 옮긴 뒤 일부 에이전트의 초기 위치를 안전하게 재배치하지 못해 시뮬레이션 시작 전에 종료됐다. 프론트는 이제 이를 `변경 배치에서 초기 인원을 안전하게 배치할 공간이 부족합니다`로 표시한다.

후속 개선 후보:

- 실측 전에 Java의 실제 초기 에이전트 배치 검증과 동일한 검사를 후보에 적용해 trial 예산 낭비를 줄인다.
- 단, Python에서 비슷한 검사를 새로 흉내 내지 말고 공식 실행 준비 경로의 검증을 재사용해야 한다.

---

## A6. 검증 상태

이번 작업에서 확인한 결과:

```text
Python engine: 288 passed, 9 skipped, 46 subtests passed
Frontend: 288 passed / 70 files
Frontend lint: passed
Frontend production build: passed
Simulation service Gradle test: passed
변경 Java 파일 scoped Spotless: passed
```

프론트 전체 테스트에는 기존 `SimulationListPage.test.tsx`의 React `act(...)` 경고가 한 건 남지만 테스트는 통과한다.

저장소 전체 `spotlessCheck`는 이번 변경과 무관한 기존 `SimulationGeometry.java`, `SimulationGeometryRelaxationTest.java` 줄바꿈 위반 때문에 실패한다. 이번에 변경한 Java 파일은 `spotlessIdeHook`으로 개별 검사해 모두 clean을 확인했다. 관련 없는 파일을 일괄 `spotlessApply`하지 말 것.

---

## A7. 다음 담당자의 시작 순서

1. 이 문서와 `ideal_route_docking.py`, `LayoutSearchOrchestrator.java`의 후보 생성 입력 경계를 읽는다.
2. 최신 완료 기준 시뮬레이션 하나의 diagnosis region, 이상 경로, blocker set을 한 표로 덤프한다.
3. 병목 우선순위가 없는 현재 순위를 회귀 테스트로 먼저 고정한다.
4. 병목 단위 blocker set 생성과 우선순위를 구현한다.
5. Python 전체 테스트와 최신 입력 재생 후에만 실제 trial을 실행한다.
6. 실측 6개와 추천 최대 3개의 구분을 유지한다.

주의: 이 문서 작성 직전 개발 DB가 새로 초기화된 것으로 보이며 최신 검색 id가 다시 `1`부터 시작했다. 과거 관찰 검색 id와 현재 DB id가 같다고 가정하지 말고, 항상 `ORDER BY created_at DESC`로 최신 행을 찾을 것.
