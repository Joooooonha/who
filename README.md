# 공약 디코딩 2026

공식 선거 공약 데이터를 기반으로 사용자가 공약을 먼저 평가하고, 응답과 가장 잘 맞는 후보를 "공약 일치도"로 보여주는 MVP입니다.

현재 구현 범위는 2026 데이터가 공개되기 전까지 사용할 **과거 선거 데이터 검증 파이프라인**입니다. API 키가 없어도 fixture 모드로 점수 산식과 리포트 포맷을 검증할 수 있고, 키가 생기면 실제 중앙선거관리위원회 공공데이터 API를 호출합니다.

## Quick Start

Fixture 모드:

```sh
java tools/nec-probe/NecDataProbe.java --fixture
```

실제 API 검증:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2022-local --candidate-limit 30
```

서울특별시 시도지사/교육감 등 특정 지역 샘플:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2022-local --sdName 서울특별시 --candidate-limit 30
```

2025 대통령선거 보조 검증:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2025-president
```

공식 문서 예시값 smoke test:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target docs-sample
```

공식 문서 예시 후보자 ID로 공약 API만 직접 확인:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target docs-sample --candidate-id 1000000000
```

모든 선거코드 조합에서 공약 데이터가 붙는지 샘플 확인:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target pledge-availability
```

이 모드는 API 쿼터를 아끼기 위해 각 `sgId/sgTypecode` 조합에서 후보자 최대 3명만 샘플링합니다.

결과는 `data/probes/` 아래에 Markdown/JSON 리포트로 저장됩니다.

실제 API 모드에서는 `data/probes/common-codes/`에 선거코드 목록도 함께 저장됩니다. 후보자 API에서 `INFO-03 데이터 정보가 없습니다`가 나오면 이 목록에서 실제로 제공되는 `sgId`와 `sgTypecode` 조합을 확인한 뒤 custom target으로 다시 실행하세요.

## API 신청과 배포

- 필요한 공공데이터 API 신청 절차는 [docs/api-application.md](docs/api-application.md)에 정리했습니다.
- 기능을 작게 만든 뒤 계속 배포하는 CI/CD 방향은 [docs/deployment.md](docs/deployment.md)에 정리했습니다.
- 현재 CI는 fixture probe, JSON 리포트 검증, backend test를 실행합니다.
- API 키를 GitHub secret `DATA_GO_KR_SERVICE_KEY`로 넣으면 수동 workflow에서 실데이터 probe를 돌릴 수 있습니다.
- 배포 플랫폼의 deploy hook을 GitHub secret `DEPLOY_HOOK_URL`로 넣으면 `main` CI 성공 후 자동 배포됩니다.

## Backend

Spring Boot 백엔드는 `backend/`에 있습니다.

```sh
cd backend
./gradlew test
./gradlew bootRun
```

기본 로컬 프로필은 H2 in-memory DB를 사용합니다. 배포에서는 `SPRING_PROFILES_ACTIVE=prod`와 아래 환경변수를 설정합니다.

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

초기 API:

```text
GET /api/elections
GET /api/elections/{electionId}/pledges
POST /api/results
POST /api/admin/import/nec
GET /actuator/health
```

NEC 데이터 import:

```sh
cd backend
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' ./gradlew bootRun
```

다른 터미널에서:

```sh
curl -X POST "http://localhost:8080/api/admin/import/nec?sgId=20250603&sgTypecode=1&candidateLimit=10"
```

2026 지방선거 데이터가 공개되면 같은 API에 `sgId=20260603`과 `sgTypecode=3`, `4`, `11`을 넣어 재실행합니다.

## Frontend

프론트엔드는 `frontend/`의 Vite React 앱입니다. 로컬 개발에서는 Vite proxy가 `localhost:8080` 백엔드로 `/api` 요청을 전달합니다.

```sh
cd frontend
npm install
npm run dev
```

프로덕션 빌드 확인:

```sh
cd frontend
npm run build
```

## Data Flow

```text
CommonCodeService
→ Candidate API
→ Candidate pledge API
→ sample user responses
→ candidate average score
→ normalized pledge match ranking
→ user-only result evidence
```

자세한 검증 절차는 [docs/data-validation.md](docs/data-validation.md)를 참고하세요.
