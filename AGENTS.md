# AGENTS.md

## Project Overview
- Project name: 공약 디코딩 2026
- Goal: Let voters evaluate official pledges first, then show which candidates' pledges best match their own responses.
- MVP priority: Validate official election data import, pledge evaluation, candidate scoring, and result evidence before building broad UI polish.
- Current data strategy: Use past elections for verification until 2026 local election candidate and pledge data is published.

## Development Rules
- Build the simplest working MVP path first.
- Use official NEC/public data fields as the source of truth.
- Do not collect age, gender, job, IP-derived identity, or social login data.
- Use browser UUID only for anonymous repeat submission handling in the product.
- Avoid wording that implies recommendation, support, approval rating, or vote share. Prefer "내 응답과 공약 일치도가 높은 후보".
- Preserve raw official pledge text. Any generated summary must be stored and displayed as a derivative aid, not as a replacement for the official source.
- Treat past NEC data as API/import validation data. Treat 2026 local election data as unavailable until candidates and pledges are officially published.

## NEC Data Findings
- API flow: CommonCode `sgId/sgTypecode` -> Candidate `huboid` -> Pledge `cnddtId=huboid`.
- Pledge request parameter is `cnddtId`; pledge content fields are `prmmCont1..N`.
- Pledge data is not available for every election type.
- Current observed pledge-bearing election types:
  - `1`: 대통령선거
  - `3`: 시·도지사선거
  - `4`: 구·시·군의 장선거
  - `11`: 교육감선거
- Legislative/member elections such as `2`, `5`, `6`, `8`, `9` should not be assumed to have pledge data for MVP.
- 2026 local election target is `sgId=20260603`, with likely MVP types `3`, `4`, `11`, but current candidate/pledge data is empty.
- Use `20250603/type=1` and `20220601/type=3,4` for development/import validation.

## Tech Direction
- Backend target: Java 17+, Spring Boot 3.x, PostgreSQL.
- Frontend target: Vite React TypeScript when UI work starts.
- Data probe tools may use dependency-free Java so they can run before the full backend scaffold exists.
- Candidate photos are out of MVP unless an official and license-safe source is added.
- Budget/funding fields are out of MVP unless official data or manually verified metadata is added.
- Backend is the source of truth for scoring, import, raw pledge storage, and generated summary status.
- Keep API keys server-side only. Never expose `DATA_GO_KR_SERVICE_KEY` or AI provider keys to the frontend.

## AI Summary Rules
- Generated pledge summaries are required for readability, but raw official text must remain available in result evidence.
- Summaries must be neutral, non-persuasive, and phrased as "공식 공약 요약".
- Do not infer political intent, feasibility, ideology, or support. Summarize only what the official pledge text says.
- Store summary metadata: model/provider, generated time, source pledge version/hash, and status.
- If summary generation fails, fall back to showing the official title and raw text excerpt rather than blocking the user flow.
- Add human-review capability later if summaries are shown prominently in production.

## Scoring Rules
- Pledge response mapping: positive = 3, unknown/neutral = 2, negative = 1.
- Candidate score is calculated from the average score across that candidate's evaluated pledges.
- Normalize average score to 0-100 using `(average - 1) / 2 * 100`.
- Break ties by evaluated pledge count, then candidate name for deterministic output.

## Result Evidence
- The result page should show the user which evaluated pledges belonged to which candidate.
- This evidence is for the current user's explanation only, not a public share artifact.
- Public dashboards must aggregate pledge trends, not expose individual response histories.

## Communication
- Reply in Korean by default.
- Keep updates technical and concise.
- Report changed files, executed commands, and failures clearly.
