# HWALRO Design System

## 1. Atmosphere & Identity

활로는 안전 검토를 위한 차분한 작업 도구다. 밝은 그린 틴트 작업면 위에 딥그린 편집 패널을 띄우고, 라임색은 현재 도구·삽입 위치처럼 즉시 알아야 하는 상태에만 사용한다.

## 2. Color

실제 토큰은 `apps/frontend/src/index.css`의 `@theme`가 소유한다.

| 역할 | 토큰 | 사용 |
| --- | --- | --- |
| 기본 배경 | `--color-background` | 페이지와 캔버스 작업면 |
| 본문 표면 | `--color-surface`, `--color-white` | 카드, 입력, 팝오버 |
| 브랜드 | `--color-primary`, `--color-primary-soft` | 주요 동작과 포커스 보조 |
| 편집 패널 | `--color-panel`, `--color-panel-soft` | 좌·우 패널과 툴바 |
| 패널 텍스트 | `--color-panel-text`, `--color-panel-muted` | 패널 본문과 보조 정보 |
| 편집 강조 | `--color-panel-accent` | 선택 도구와 드롭 위치 |
| 구분선 | `--color-panel-divider`, `--color-line` | 패널 및 밝은 표면 구분 |
| 오류 | `--color-danger`, `--color-panel-danger` | 검증 오류와 위험 동작 |
| 포커스 | `--color-focus-ring` | 모든 키보드 포커스 링 |

새 색은 컴포넌트에 직접 선언하지 않고 먼저 `@theme`에 의미 토큰으로 추가한다.

## 3. Typography

- 기본 글꼴: `--font-sans`의 Happiness Sans Print와 시스템 한글 폴백.
- 편집 패널 제목: 18px/900, 행: 14px, 보조 정보: 11–12px.
- 도면 수치: 시스템 고정폭 글꼴과 tabular numbers.
- 한국어 레이블은 한 글자 고아 줄바꿈을 만들지 않으며 좁은 행에서는 말줄임한다.

## 4. Spacing & Layout

- 기본 단위는 4px이며 패널 내부 간격은 8/12/16/24px 계열을 사용한다.
- `CanvasWorkspace`가 `100dvh`를 소유하고 캔버스는 고정 작업면이다.
- 좌측 계층과 우측 설정 패널은 각각 독립적인 세로 스크롤 영역을 가진다.
- 하단 도구막대와 줌 컨트롤은 고정 영역이며 패널 스크롤 영역과 겹치지 않아야 한다.
- 전체 프레임 변화는 기존 760/840/1100px 미디어 규칙을 따른다.

## 5. Components

### Canvas Workspace Panel

- 구조: 고정 헤더 + `min-height: 0` 스크롤 본문.
- 상태: 펼침, 접는 중, 접힘, 다시 열기.
- 접근성: 접기/열기 버튼은 `aria-controls`, `aria-expanded`, 명확한 레이블을 제공한다.
- 모션: 기존 240ms 패널 enter/collapse 애니메이션과 reduced-motion 대체를 사용한다.
- 레이아웃: 패널 본문만 스크롤하며 하단 고정 컨트롤 공간을 침범하지 않는다.

### Layer Tree Row

- 구조: 행 전체가 선택 버튼이며 별도 overflow 버튼을 두지 않는다.
- 상태: 기본, hover, 선택, focus, drag, drop-before, drop-after, drop-inside.
- 접근성: 클릭 선택, Ctrl/Shift/Meta 다중 선택, 우클릭 및 Shift+F10 컨텍스트 메뉴.
- 모션: 드롭 표시는 위치가 바뀌는 동안 즉시 갱신되고 불필요한 장식 애니메이션은 사용하지 않는다.

### Layer Context Menu

- 포인터 원점에 열리고 viewport 안으로 위치를 제한한다.
- Escape·바깥 클릭으로 닫고, 키보드 호출 시 첫 항목으로 포커스를 이동한다.
- 선택 요소 묶기와 구역 이동처럼 현재 행에 유효한 작업만 노출한다.

### Canvas Controls

- 도구막대와 줌 컨트롤은 캔버스 위 고정 영역이다.
- 패널 접힘 여부와 관계없이 조작 가능하고 서로 중첩되지 않는다.

## 6. Motion & Interaction

- 마이크로 상태는 100–150ms ease-out, 패널 전환은 기존 240ms 곡선을 사용한다.
- 공간 이동은 `transform`, 상태 전환은 `opacity` 중심으로 표현한다.
- 드래그 삽입 표시는 라임 선과 양 끝 마커로 정확한 before/after 위치를 알린다.
- `prefers-reduced-motion: reduce`에서는 패널 전환을 1ms로 줄이고 기능은 유지한다.
- 대피 동선은 8px 선과 6px 간격의 dash가 초당 18px 이동해 비상구 방향의 흐름을 나타내며, reduced-motion에서는 같은 dash를 정지 상태로 유지한다.

## 7. Depth & Surface

혼합 전략을 사용한다. 편집 패널은 반투명 딥그린 표면과 얇은 테두리·기존 그림자로 캔버스에서 분리하며, 컨텍스트 메뉴는 밝은 elevated surface와 `--shadow-raised`를 사용한다. 새로운 임의 그림자나 radius 체계를 추가하지 않는다.

## 8. Accessibility Constraints & Accepted Debt

- WCAG 2.2 AA를 목표로 하며 모든 행과 메뉴 항목은 키보드로 도달 가능해야 한다.
- 네이티브 HTML5 DnD의 키보드 한계는 Shift+F10 컨텍스트 메뉴 동작으로 보완한다.
- 숨긴 스크롤바는 스크롤 기능·포커스 이동·자동 `scrollIntoView`를 제거하지 않는다.
- 현재 좁은 viewport에서 양쪽 편집 패널이 겹치는 기존 데스크톱 우선 레이아웃은 이번 요청의 좌측 하단 중첩 수정과 별개인 후속 디자인 부채다.

