# JuPedSim runner

시뮬레이션 구조, SCRUM-60 성능 개선 내용, dev 대비 실측 결과는
[PERFORMANCE.md](./PERFORMANCE.md)에 쉽게 풀어서 기록했다.

CPython 3.12 독립 프로세스로 실행하며 데이터베이스에는 접근하지 않는다. 로컬 실행 환경은 저장소
루트에서 다음 명령 하나로 구성한다.

```bash
pnpm engine:setup
```

설정 스크립트는 프로젝트 내부에 고정 버전 uv를 설치하고, uv가 관리하는 Python 3.12와 `.venv`를
생성한 뒤 `gugukorn/jupedsim-hwalro`의 플랫폼별 wheel을 설치한다. 사용자 PATH, 기존 Python,
Visual Studio 또는 Xcode 빌드 도구는 사용하지 않는다.

지원 환경:

- Windows x64
- macOS 12 이상 Apple Silicon
- macOS 12 이상 Intel

플랫폼별 직접 실행도 가능하다.

```powershell
.\apps\simulation-service\engine\setup-windows.ps1
```

```bash
sh ./apps/simulation-service/engine/setup-macos.sh
```

설치가 끝나면 커스텀 `Agent.position` setter와 `runner.py --version`을 자동 검증한다. 활로 실행기는
에이전트가 유효 영역을 이탈했을 때 직전 위치로 복원하기 위해 이 커스텀 JuPedSim 1.4.2 wheel을
사용한다.

simulation-service는 설정된 `SIMULATION_ENGINE_PYTHON`이 없으면 위 프로젝트 venv를 먼저 사용하고,
venv가 없을 때만 `python` 명령으로 대체한다.

입력 계약:

```json
{
  "model": {
    "modelProfile": "SFM_DEFAULT_V2",
    "routingProfile": "HAZARD_RADIAL_EXP_V3",
    "walkingSpeed": 1.2,
    "reactionTime": 0.5
  },
  "drawing": {
    "outsideBoundary": [
      { "x": 0, "y": 0 },
      { "x": 10, "y": 0 },
      { "x": 10, "y": 5 },
      { "x": 0, "y": 5 }
    ],
    "walls": [],
    "pillars": [],
    "fabrics": [],
    "exits": [{ "id": 1, "startX": 10, "startY": 2, "endX": 10, "endY": 3 }]
  },
  "agents": [{ "x": 1, "y": 2.5 }],
  "hazards": [],
  "selectedExitIds": [1],
  "maxSimulationTimeSeconds": 600,
  "frameIntervalSeconds": 1
}
```

`output_dir/result.json`과 `output_dir/timeline/000000.json`부터 시작하는 청크를 생성한다. 타임라인 청크는 최대 20프레임이다.

구현은 JuPedSim 1.4의 [Simulation API](https://www.jupedsim.org/v1.4.0/api/jupedsim/index.html)와 [Direct Steering 예제](https://www.jupedsim.org/v1.4.0/notebooks/direct_steering.html)를 따른다.

## 배포

현재 커스텀 wheel 자동 설치는 로컬 Windows와 macOS만 지원한다. Linux용 커스텀 wheel을
배포하기 전까지 Linux와 Docker 이미지는 guarded simulation 실행 환경으로 사용하지 않는다.
`requirements.txt`도 writable `Agent.position`이 없는 공식 Linux wheel을 설치하지 않으며,
`pnpm engine:setup`은 Linux에서 실행 전에 지원하지 않는 플랫폼 오류를 반환한다. 현재 Dockerfile은
애플리케이션 이미지를 만들 수 있지만 JuPedSim 실행 요청은 지원하지 않는다.

```bash
docker build -t hwalro-simulation-service apps/simulation-service
```

실행 경로와 실제 제한시간은 `SIMULATION_ENGINE_PYTHON`, `SIMULATION_ENGINE_SCRIPT`,
`SIMULATION_ENGINE_TIMEOUT`으로 조정할 수 있다. 현재 실행기 1개와 대기열 20개 및 재시작 복구는
애플리케이션 인스턴스 기준이므로, 분산 lease를 도입하기 전까지 AWS 배포 replica는 1개로 유지한다.
