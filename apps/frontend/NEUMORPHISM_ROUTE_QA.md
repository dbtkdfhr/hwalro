# 활로 통합 뉴모피즘 라우트 검수표

검수일: 2026-08-24

## 공통 판정 기준

- `raised / sunken / overlay / inverse` 표면과 `8 / 10 / 16 / 20px` 반경 체계를 사용한다.
- 표 행에는 개별 그림자를 적용하지 않고, 검색·필터는 inset, 표 셸은 raised로 구분한다.
- API, DTO, URL, Query key, 폼 필드, 이벤트 핸들러와 캔버스 좌표 계산은 변경하지 않는다.
- loading, empty, error, disabled, modal 상태는 기존 분기를 유지한 채 같은 토큰으로 표시한다.
- 인쇄 영역의 배경은 흰색으로 고정하고 모든 하위 그림자와 text-shadow를 제거한다.

## 20개 라우트

| 번호 | 라우트                                         | 화면             | 디자인 적용 및 상태 검수                                                                         | 근거                                                                 |
| ---: | ---------------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
|    1 | `/login`                                       | 로그인           | inverse 브랜드 패널, raised 폼, inset 필드, error와 disabled 로그인 버튼                         | 브라우저 1440/1024/768/390px, 포커스 링, 가로 overflow, console 검사 |
|    2 | `/`                                            | 홈               | 진행 검토, 빠른 실행, 지표, 최근 표, 위험 항목을 서로 다른 위계로 구성; loading/error/empty 유지 | 홈 컴포넌트와 유틸 테스트                                            |
|    3 | `/risk-management`                             | 위험 예상 항목   | inset 검색·필터, flat row 표, raised 상세; loading/error/empty, 생성·삭제 modal 유지             | 컴포넌트 정적 검수, lint/build                                       |
|    4 | `/reports`                                     | 보고서 목록      | inset 검색, raised 표; loading/error/empty, 삭제 confirm, pagination 유지                        | 컴포넌트 정적 검수, 보고서 상태 테스트                               |
|    5 | `/reports/:reportId`                           | 보고서 상세      | raised 편집·미리보기 패널; loading/error, disabled preview/print, 인쇄 스타일 유지               | 컴포넌트 정적 검수, production build                                 |
|    6 | `/safety-checklists`                           | 안전 점검 구역   | raised 구역 카드, inset 생성 폼; loading/error/empty, QR·삭제 modal 유지                         | 컴포넌트 정적 검수, production build                                 |
|    7 | `/safety-checklists/areas/:areaId`             | 점검 이력        | raised 표와 flat row; loading/error/empty, 생성·삭제 modal, pagination 유지                      | 컴포넌트 정적 검수, lint/build                                       |
|    8 | `/safety-checklists/inspections/:inspectionId` | 점검 상세        | 상태 배지는 flat semantic color, 항목은 inset; loading/error, disabled, 인쇄 유지                | 컴포넌트 정적 검수, production build                                 |
|    9 | `/safety-checklists/areas/:areaId/template`    | 템플릿 관리      | raised 편집 셸과 inset 항목; loading/error/empty, 저장·순서·삭제 disabled 유지                   | 컴포넌트 정적 검수, production build                                 |
|   10 | `/drawings`                                    | 도면 목록        | inset 검색과 단일 raised 표; loading/error/empty, pagination, 삭제 modal 유지                    | 컴포넌트 정적 검수, production build                                 |
|   11 | `/drawings/new`                                | 도면 등록        | raised 폼, inset 입력, pressed 선택 카드; validation/error/disabled 유지                         | 컴포넌트 정적 검수, production build                                 |
|   12 | `/drawings/:drawingId`                         | 편집 리다이렉트  | `/layout/:drawingId` replace 계약 유지                                                           | router 정적 검수, TypeScript build                                   |
|   13 | `/simulations`                                 | 시뮬레이션 목록  | inset 검색과 단일 raised 표; loading/error/empty, 상태·취소·삭제 modal 유지                      | SimulationListPage 테스트 포함 128개 전체 테스트                     |
|   14 | `/regulations`                                 | 안전 법령        | inset 검색·목록과 raised 상세; loading/error/empty/selected 상태 유지                            | CSS·컴포넌트 정적 검수, production build                             |
|   15 | `/system-management`                           | 시스템 관리      | raised 권한·사용자 셸, inset 필드; loading/error/empty, 사용자 modal과 disabled 유지             | CSS·컴포넌트 정적 검수, production build                             |
|   16 | `/layout/:drawingId`                           | 도면 편집        | 공통 캔버스 헤더·inverse 패널·도구바·확대 제어; loading/error와 패널 collapse 유지               | 캔버스 geometry diff 검수, lint/build                                |
|   17 | `/simulations/:simulationId/setup`             | 시뮬레이션 설정  | 공통 inverse 패널·pressed 필드·도구바; loading/error/disabled/삭제 dialog 유지                   | SimulationSetupPage 8개 테스트, geometry diff 검수                   |
|   18 | `/simulations/:simulationId/results`           | 결과 분석        | 공통 workspace 재질, 재생·요약·차트·위험·보고서 dialog 상태 유지                                 | 결과 유틸·렌더링·재생 테스트, production build                       |
|   19 | `/simulations/:simulationId/layout-search`     | 배치 개선        | 공통 inverse 패널·비교·제약·진행 상태; loading/error/empty/disabled 유지                         | LayoutSearchPage 10개 테스트, SVG 렌더 색 보존 검수                  |
|   20 | `/inspect/:areaId`                             | 모바일 현장 점검 | 얕은 raised/inset 표면, sticky header/action; loading/error/완료/disabled 유지                   | 390px 구조·터치 영역 정적 검수, production build                     |

## 접근성 및 반응형

- 본문/배경 대비는 `13.55:1`, muted/배경은 `5.38:1`, faint/raised는 `4.73:1`이다.
- 기본 포커스는 primary, inverse 패널 포커스는 lime을 사용한다.
- `prefers-reduced-motion`, `prefers-reduced-transparency`, `forced-colors` 규칙을 적용했다.
- 공개 로그인 화면은 1440/1024/768/390px에서 실제 렌더링했으며 가로 overflow와 console error가 없었다.
- 보호 라우트는 인증된 브라우저 세션이 없어 실제 데이터 기반 스크린샷 대신 라우트·상태 분기·반응형 CSS·테스트·production build로 검수했다.

## 자동 검증

- `pnpm --filter @hwalro/frontend lint`
- `pnpm --filter @hwalro/frontend test` - 21 files, 128 tests
- `pnpm --filter @hwalro/frontend build`
- 변경 파일 Prettier check
- `git diff --check -- apps/frontend`
