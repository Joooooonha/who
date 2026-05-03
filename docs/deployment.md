# 배포 우선 개발 전략

## 원칙

한 번에 다 만든 뒤 배포하지 않는다. 기능을 아주 작게 세로로 자른 뒤, `main`에 병합될 때마다 CI/CD가 실행되는 구조로 간다.

## 단계

1. 데이터 검증 도구 배포 준비
   - 현재 상태에서는 `NecDataProbe` fixture 검증을 CI의 최소 안전망으로 둔다.
   - API 키가 생기면 수동 workflow로 2022/2025 실데이터 probe를 실행한다.
2. 최소 앱 배포
   - Spring Boot API와 Vite React 앱을 만든다.
   - 첫 배포 기능은 헬스체크, 선거 목록 fixture, 공약 평가 fixture, 결과 산식까지로 제한한다.
3. 실데이터 import 연결
   - 공공데이터 API 키를 서버 환경변수로 넣는다.
   - `POST /api/admin/import/nec`로 과거 선거 데이터를 DB에 적재한다.
   - 배포 환경에서는 import API 접근 제한을 추가하기 전까지 공개 트래픽에 노출하지 않는다.
4. 기능별 반복 배포
   - 지역 선택
   - 공약 평가 UI
   - 결과 일치도
   - 사용자 본인용 선택 근거
   - 인기 공약 대시보드

## CI/CD 기본 구조

- Pull request
  - Java probe fixture 실행
  - JSON 리포트 유효성 검사
  - 이후 Spring/React가 생기면 backend test, frontend build 추가
- `main` push
  - CI 통과
  - `DEPLOY_HOOK_URL` secret이 있으면 배포 hook 호출
- 수동 workflow
  - `DATA_GO_KR_SERVICE_KEY` secret이 있을 때 과거 선거 실데이터 probe 실행

## 배포 플랫폼 기본 선택

MVP에서는 관리 시간을 줄이기 위해 deploy hook을 지원하는 PaaS를 기본값으로 둔다.

- Backend: Spring Boot Docker 배포가 쉬운 Render, Railway, Fly.io 중 하나
- Database: 플랫폼 관리형 PostgreSQL
- Frontend: Vite 정적 빌드를 백엔드에서 같이 서빙하거나, 필요하면 Vercel/Netlify 분리

초기에는 서버 하나로 단순하게 가는 편이 좋다. 프론트 시간을 줄이기 위해 React 앱은 Spring Boot 정적 리소스로 붙일 수 있게 구성한다.
