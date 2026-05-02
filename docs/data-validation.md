# 과거 선거 데이터 검증

## 목적

2026 지방선거 데이터가 아직 안정적으로 공개되기 전까지, 과거 선거 데이터로 다음 흐름을 검증한다.

```text
선거코드 조회
→ 후보자 조회
→ 후보자별 공약 조회
→ 공약별 3단계 평가
→ 후보별 평균 점수 정규화
→ 결과 근거 생성
```

## 검증 대상

- 1차: 2022 제8회 전국동시지방선거
  - `sgId`: `20220601`
  - `sgTypecode`: `3`, `4`, `11`
  - 목적: 2026 지방선거와 가장 비슷한 지역/후보/공약 구조 검증
- 보조: 2025 제21대 대통령선거
  - `sgId`: `20250603`
  - `sgTypecode`: `1`
  - 목적: 최신 대통령선거 데이터 구조 검증
- 문서 예시 smoke test
  - `sgId`: `20231011`
  - `sgTypecode`: `4`
  - `cnddtid`: `1000000000`
  - 목적: 공식 활용 문서 예시값 기준 요청 파라미터 검증

`CommonCodeService` 결과가 위 값과 다르면 API 응답을 우선한다.

## 공식 API 흐름

1. 선거코드 조회
   - `CommonCodeService/getCommonSgCodeList`
   - `sgId`, `sgTypecode`, `sgName`, `sgVotedate` 확인
2. 후보자 조회
   - `PofelcddInfoInqireService/getPofelcddRegistSttusInfoInqire`
   - `huboid`, `name`, `jdName`, `sdName`, `sggName`, `wiwName`, `status` 확인
3. 후보자별 공약 조회
   - `ElecPrmsInfoInqireService/getCnddtElecPrmsInfoInqire`
   - 후보자 API의 `huboid` 값을 공약 API의 `cnddtid` 파라미터에 넣는다.
   - `prmsRealmNameN`, `prmsTitleN`, `prmsContN` 확인

## 실행

API 키 없이 fixture 검증:

```sh
java tools/nec-probe/NecDataProbe.java --fixture
```

2022 지방선거 실데이터 샘플:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2022-local --candidate-limit 30
```

2025 대통령선거 실데이터:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2025-president
```

API가 특정 조합에서 `INFO-03 데이터 정보가 없습니다`를 반환하면 `data/probes/common-codes/common-codes.md`를 먼저 확인한다. GitHub Actions에서는 artifact `nec-api-probe-reports` 안에 같은 파일이 포함된다.

문서와 실제 제공 코드가 다르면 custom target으로 재시도한다.

```sh
java tools/nec-probe/NecDataProbe.java --target custom --sgId 20231011 --sgTypecode 4
```

특정 시도/선거구 필터:

```sh
DATA_GO_KR_SERVICE_KEY='발급받은_서비스키' \
  java tools/nec-probe/NecDataProbe.java --target 2022-local --sdName 서울특별시 --sggName 강서구
```

## 점수 산식

- 긍정적이다: `3`
- 모르겠다: `2`
- 부정적이다: `1`
- 후보 평균 점수: `후보 공약 응답 점수 합 / 평가된 후보 공약 수`
- 정규화 일치도: `(평균 점수 - 1) / 2 * 100`

예: 평균 3.0은 100%, 평균 2.0은 50%, 평균 1.0은 0%.

## 리포트에서 확인할 것

- 후보자 API에서 후보가 조회되는지
- 후보자별 공약 수가 충분한지
- 공약 없는 후보가 얼마나 있는지
- 지역별 후보 수 차이가 어느 정도인지
- 공약 제목/내용/분야가 UI에 바로 쓸 수 있는 품질인지
- 후보별 평균 정규화 점수가 의도대로 계산되는지

## MVP 정책

- 결과 근거는 외부 공개용이 아니라 현재 사용자 본인에게만 보여주는 설명 자료다.
- 예산/재원 정보는 공식 공약 API 기본 필드가 아니므로 MVP에서는 표시하지 않는다.
- 후보 사진은 공식 API에서 안정적으로 제공되지 않으므로 MVP에서는 이름/지역 중심으로 표시한다.
- 2026 데이터가 공개되면 동일 파이프라인에서 `sgId`, `sgTypecode`만 바꿔 재검증한다.
