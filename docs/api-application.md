# 공공데이터 API 신청 체크리스트

## 신청해야 할 API

공공데이터포털에서 아래 API를 각각 활용신청한다.

1. 중앙선거관리위원회_코드 정보
   - 목적: `sgId`, `sgTypecode`, `sgName`, `sgVotedate` 확인
   - 서비스: `CommonCodeService`
   - 사용 흐름: 사용 가능한 과거/현재 선거 코드 조회
2. 중앙선거관리위원회_후보자 정보
   - 목적: 선거ID, 선거종류코드, 시도명, 선거구명 기준 후보자 조회
   - 서비스: `PofelcddInfoInqireService`
   - 개발계정: 자동승인, 기본 10,000건 트래픽
3. 중앙선거관리위원회_선거공약 정보
   - 목적: 후보자ID 기준 후보자별 공약 조회
   - 서비스: `ElecPrmsInfoInqireService`
   - 개발계정: 자동승인, 기본 10,000건 트래픽
   - 후보자 정보 API의 `huboid` 값을 공약 API의 `cnddtid` 파라미터에 넣는다.

## 신청 사유 예시

```text
공식 선거 후보자 및 공약 데이터를 기반으로 유권자가 공약을 먼저 평가하고,
본인의 응답과 후보자별 공약의 일치도를 확인할 수 있는 비상업 MVP 서비스를 개발하기 위함입니다.
개인정보, 성별, 나이, 직업, 지지율 정보는 수집하거나 표시하지 않으며,
공식 공약 원문과 후보자 기본 정보만 활용합니다.
```

## 키 발급 후 로컬 검증

`.env.example`을 참고해 로컬 환경에 키를 넣는다.

```sh
export DATA_GO_KR_SERVICE_KEY='발급받은_서비스키'
```

먼저 2022 지방선거 샘플을 확인한다.

```sh
java tools/nec-probe/NecDataProbe.java --target 2022-local --candidate-limit 30
```

특정 지역만 볼 때:

```sh
java tools/nec-probe/NecDataProbe.java --target 2022-local --sdName 서울특별시 --candidate-limit 30
```

2025 대통령선거 보조 검증:

```sh
java tools/nec-probe/NecDataProbe.java --target 2025-president
```

후보자 API에서 `INFO-03 데이터 정보가 없습니다`가 나오면 실패라기보다 해당 `sgId/sgTypecode` 조합에 후보자 데이터가 없다는 뜻일 수 있다. 이때는 `data/probes/common-codes/common-codes.md`에서 실제 제공 중인 선거코드를 확인한 뒤 custom target으로 다시 검증한다.

## GitHub Secrets

CI/CD에서 실데이터 검증이나 배포를 연결할 때 아래 secrets를 설정한다.

- `DATA_GO_KR_SERVICE_KEY`: 공공데이터포털 인증키
- `DEPLOY_HOOK_URL`: 배포 플랫폼에서 발급한 deploy hook URL

`DATA_GO_KR_SERVICE_KEY`는 서버 런타임 환경변수에도 등록한다. 프론트엔드로 노출하면 안 된다.

## 주의사항

- 선거공약 API는 공약 제출 대상 선거종류코드만 제공한다.
  - `1`: 대통령선거
  - `3`: 시·도지사선거
  - `4`: 구·시·군의 장선거
  - `11`: 교육감선거
- 선거 종료 후에는 후보자 전체 공약이 아니라 당선인 공약 중심으로 제공될 수 있다.
- 후보자별 공약 개수가 다를 수 있으므로 서비스 점수는 평균 정규화로 계산한다.
- 후보자 API에는 생년월일, 성별, 주소, 직업 같은 필드가 포함될 수 있으나 MVP DB와 화면에는 저장/표시하지 않는다.
