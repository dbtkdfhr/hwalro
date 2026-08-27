# HWALRO

test

## 인프라 초기화 및 역할 마이그레이션

새 MySQL 볼륨으로 인프라를 다시 초기화하려면 다음 명령을 순서대로 실행합니다.

```bash
pnpm infra:reset
pnpm infra
```

`pnpm infra`는 MySQL 컨테이너 초기화 과정에서 스키마, 서비스별 DB 사용자 및 초기 역할 데이터를 적용합니다.

위 과정을 한 번에 실행하려면 `pnpm reset`을 사용합니다. 이 명령은 기존 Docker 볼륨을 삭제하고 MySQL healthcheck가 완료될 때까지 대기한 뒤, MySQL이 실제로 준비될 때까지 역할 DML 적용을 최대 60초 재시도합니다.

각 서비스의 `db/schema.sql`은 새 MySQL 볼륨을 초기화하기 위한 스키마이며 기존 볼륨을
`ALTER`하는 마이그레이션이 아닙니다. 스키마가 변경된 브랜치를 적용할 때는 개발 DB 데이터를
백업한 뒤 `pnpm reset`으로 볼륨을 삭제하고 다시 생성해야 합니다. 기존 볼륨을 유지한 채
`pnpm infra`만 다시 실행해도 변경된 simulation-service·regulation-service 스키마는 적용되지 않습니다.

기존 MySQL 볼륨에 역할 DML만 다시 적용하려면 Docker 인프라가 실행 중인 상태에서 다음 명령을 사용합니다. Windows와 macOS/Linux 모두 같은 명령을 사용하며, 내부에서 운영체제에 맞는 스크립트를 실행합니다.

```bash
pnpm infra:migrate:auth-roles
```

운영 RDS에 V1.5 이후 스키마 변경을 적용할 때는
[`deploy/RDS_MIGRATION_V1_5_TO_CURRENT.md`](deploy/RDS_MIGRATION_V1_5_TO_CURRENT.md)의
배포 전·후 순서를 따른다. 운영 배포는 `DB_INIT_MODE=never`이므로 Jenkins가 스키마를 자동 변경하지 않는다.

> 팝업·전시·행사 공간의 배치안을 대피 시뮬레이션으로 사전 검토하고, 안전한 공간 운영을 위한 의사결정을 지원하는 서비스

## 서비스 소개

활로(HWALRO)는 백화점과 복합 쇼핑 공간에서 운영되는 팝업·전시·행사의 안전성을 개장 전에 검토할 수 있도록 돕는 공간 안전 지원 시스템입니다.

운영 담당자는 공간의 도면과 배치안을 등록하고 인원·출구 등의 조건을 설정해 대피 시뮬레이션을 실행할 수 있습니다. 시뮬레이션 결과에서는 대피 시간, 밀집도, 병목 등의 지표를 확인하고 배치안을 비교할 수 있습니다.

활로의 AI는 안전 지표를 직접 계산하지 않습니다. 시뮬레이션 엔진이 산출한 결과와 안전 법령을 바탕으로 결과를 설명하고 보고서 초안을 작성하는 역할만 담당합니다.

## 해결하려는 문제

팝업과 행사는 공간 구조, 집기 배치, 예상 방문 인원과 운영 조건이 매번 달라집니다. 개장 전에 배치안의 위험을 정량적으로 비교하기 어렵고, 시뮬레이션 결과·안전 점검·보고서 작성이 서로 분리되면 검토 과정의 맥락을 유지하기도 어렵습니다.

활로는 다음 과정을 하나의 시스템에서 지원하는 것을 목표로 합니다.

- 도면과 배치안을 기반으로 한 사전 대피 검토
- 대피 시간·밀집도·병목 등 객관적인 결과 확인
- 배치안별 시뮬레이션 결과 비교
- 주의 항목과 안전 점검 체크리스트 관리
- 시뮬레이션 결과와 안전 법령을 기반으로 한 AI 보고서 작성

## 핵심 검토 파이프라인

```text
검토 등록
  → 도면·배치 작성
  → 시뮬레이션 설정·실행
  → 결과 분석
```

결과 분석이 완료되면 해당 시뮬레이션 결과를 바탕으로 AI 보고서 작성 화면으로 이동할 수 있습니다. 보고서는 독립된 메뉴에서도 조회하고 작성할 수 있습니다.

## 주요 업무 메뉴

핵심 검토 파이프라인과 별도로 다음 업무 메뉴를 제공합니다.

- **주의 항목 관리**: 검토 과정에서 확인한 주의 항목을 관리합니다.
- **안전 점검 체크리스트**: 공간 운영에 필요한 안전 점검 항목을 확인하고 기록합니다.
- **안전 법령**: 보고서와 안전 검토에 참고할 법령 정보를 조회합니다.
- **보고서**: 시뮬레이션 결과와 법령 정보를 기반으로 보고서를 작성하고 관리합니다.

활로는 보고서 작성까지만 담당합니다. 보고서 작성 이후의 결재와 승인은 현대퓨처넷의 기존 결재 시스템에서 처리합니다.

## MSA 서비스 구성

백엔드는 책임에 따라 세 개의 서비스로 분리되어 있습니다.

| 애플리케이션         | 책임                                                 | 기본 포트 |
| -------------------- | ---------------------------------------------------- | --------: |
| `frontend`           | 사용자 화면과 클라이언트 상태                        |    `3000` |
| `auth-service`       | 인증·인가·사용자·권한 관리                           |    `8080` |
| `simulation-service` | 검토, 도면·배치, 시뮬레이션 실행과 결과 분석         |    `8081` |
| `regulation-service` | 주의 항목, 안전 점검, 안전 법령, AI 보고서 작성 |    `8082` |

각 백엔드 서비스는 자신의 데이터와 비즈니스 규칙을 소유하며, 다른 서비스의 데이터베이스나 내부 구현에 직접 의존하지 않는 것을 원칙으로 합니다.

## 기술 스택

### Frontend

- React 19
- TypeScript
- Vite
- ESLint
- Prettier

### Backend

- Java 17
- Spring Boot 3.4
- Gradle
- MyBatis
- MySQL Connector/J
- Spring AI
- Spotless

### Workspace

- pnpm 10
- Turborepo
- GitHub Actions

## 저장소 구조

```text
hwalro/
├─ apps/
│  ├─ frontend/
│  ├─ auth-service/
│  ├─ simulation-service/
│  └─ regulation-service/
├─ .github/
│  ├─ ISSUE_TEMPLATE/
│  └─ workflows/
├─ AGENTS.md
├─ package.json
├─ pnpm-workspace.yaml
└─ turbo.json
```

## 시작하기

### 요구 환경

- Node.js `22` 이상
- pnpm `10` 이상
- JDK `17`

현재 프로젝트가 요구하는 pnpm 버전은 루트 `package.json`의 `packageManager` 필드에서 확인할 수 있습니다.

### 의존성 설치

저장소 루트에서 실행합니다.

```bash
pnpm install --frozen-lockfile
```

### 전체 애플리케이션 실행

```bash
pnpm dev
```

Turborepo가 프론트엔드와 각 백엔드 서비스의 `dev` 스크립트를 실행합니다.

### 프론트엔드만 실행

```bash
pnpm --filter @hwalro/frontend dev
```

안전 법령 화면은 공통 사이드바의 **안전 법령** 메뉴가 연결할 경로인
`http://localhost:3000/regulations`에서 확인합니다. 루트 경로(`/`)는 법령 화면을
직접 렌더링하지 않습니다.

### 백엔드 서비스 개별 실행

Windows:

```powershell
cd apps/auth-service
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
cd apps/auth-service
./gradlew bootRun
```

다른 서비스를 실행할 때는 디렉터리를 `simulation-service` 또는 `regulation-service`로 변경합니다.

## 빌드와 검증

### 전체 워크스페이스

```bash
pnpm lint
pnpm build
```

### 프론트엔드

```bash
pnpm --filter @hwalro/frontend lint
pnpm --filter @hwalro/frontend build
```

### 백엔드

변경한 서비스의 디렉터리에서 실행합니다.

Windows:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat test
```

macOS/Linux:

```bash
./gradlew spotlessCheck
./gradlew test
```

## 환경 변수

실제 비밀번호, API 키와 토큰은 Git에 커밋하지 않습니다.

외부 시스템 연동을 활성화할 때는 다음과 같은 환경 변수가 필요할 수 있습니다.

| 환경 변수                | 용도                                                                                            |
| ------------------------ | ----------------------------------------------------------------------------------------------- |
| `JWT_SECRET`             | JWT 서명 키 (32바이트 이상, auth·simulation·regulation 서비스 공용, 미설정 시 서비스 기동 불가) |
| `AUTH_DB_PASSWORD`       | auth-service MySQL 접속 비밀번호                                                                |
| `SIMULATION_DB_PASSWORD` | simulation-service MySQL 접속 비밀번호                                                          |
| `REGULATION_DB_PASSWORD` | regulation-service MySQL 접속 비밀번호                                                          |
| `OPENAI_API_KEY`         | Regulation 서비스의 AI 보고서 작성                                                              |

개발 초기 설정에서는 외부 DB와 OpenAI 연결 없이 각 서비스의 기본 실행 상태를 확인할 수 있도록 자동 구성이 제외되어 있습니다. 외부 연동을 활성화할 때는 각 서비스의 `application.yml`과 팀의 환경 설정 기준을 함께 갱신해야 합니다.

## 협업

- 패키지 설치와 워크스페이스 명령에는 pnpm을 사용합니다.
- 기능을 수정하기 전에 담당 서비스의 경계를 확인합니다.
- 다른 서비스의 데이터베이스나 내부 클래스를 직접 참조하지 않습니다.
- PR에는 변경 목적, 영향받는 서비스와 실행한 검증을 기록합니다.
- 상세한 Codex 작업 규칙은 [`AGENTS.md`](./AGENTS.md)를 따릅니다.

## 프로젝트 범위

활로의 범위는 시뮬레이션 기반 안전 검토와 보고서 작성까지입니다.

- 활로 내부에 결재선, 승인자 또는 승인 상태 관리 기능을 구현하지 않습니다.
- AI가 생성한 값을 공식 대피 시간·밀집도·병목 지표로 사용하지 않습니다.
- 공식 안전 지표는 시뮬레이션 엔진의 결과를 기준으로 합니다.
