import {
  AlertCircle,
  Check,
  CircleHelp,
  Database,
  ListChecks,
  Loader2,
  RefreshCw,
  Send,
  ThumbsDown,
  ThumbsUp
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  calculateResult,
  importNecElection,
  listElections,
  listPledges,
  type ElectionSummary,
  type NecImportResponse,
  type PledgePlay,
  type ResponseChoice,
  type ResultResponse
} from "./api";

const choices = [
  { value: "POSITIVE", label: "긍정", score: 3, Icon: ThumbsUp },
  { value: "NEUTRAL", label: "모르겠다", score: 2, Icon: CircleHelp },
  { value: "NEGATIVE", label: "부정", score: 1, Icon: ThumbsDown }
] satisfies Array<{ value: ResponseChoice; label: string; score: number; Icon: typeof ThumbsUp }>;

const choiceLabel: Record<ResponseChoice, string> = {
  POSITIVE: "긍정",
  NEUTRAL: "모르겠다",
  NEGATIVE: "부정"
};

export default function App() {
  const [elections, setElections] = useState<ElectionSummary[]>([]);
  const [selectedElectionId, setSelectedElectionId] = useState<number | null>(null);
  const [pledges, setPledges] = useState<PledgePlay[]>([]);
  const [answers, setAnswers] = useState<Record<number, ResponseChoice>>({});
  const [result, setResult] = useState<ResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoadingElections, setIsLoadingElections] = useState(false);
  const [isLoadingPledges, setIsLoadingPledges] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [importResult, setImportResult] = useState<NecImportResponse | null>(null);
  const [importForm, setImportForm] = useState({
    sgId: "20250603",
    sgTypecode: "1",
    sdName: "",
    sggName: "",
    candidateLimit: 10
  });

  const selectedElection = useMemo(
    () => elections.find((election) => election.id === selectedElectionId) ?? null,
    [elections, selectedElectionId]
  );
  const answeredCount = Object.keys(answers).length;
  const progress = pledges.length === 0 ? 0 : Math.round((answeredCount / pledges.length) * 100);

  const refreshElections = useCallback(async () => {
    setIsLoadingElections(true);
    setError(null);
    try {
      const items = await listElections();
      setElections(items);
      setSelectedElectionId((current) => current ?? items.find((item) => item.pledgeCount > 0)?.id ?? items[0]?.id ?? null);
      return items;
    } catch (err) {
      setError(toMessage(err));
      return [];
    } finally {
      setIsLoadingElections(false);
    }
  }, []);

  useEffect(() => {
    void refreshElections();
  }, [refreshElections]);

  useEffect(() => {
    if (!selectedElectionId) {
      setPledges([]);
      return;
    }

    setIsLoadingPledges(true);
    setError(null);
    setAnswers({});
    setResult(null);
    listPledges(selectedElectionId)
      .then(setPledges)
      .catch((err) => setError(toMessage(err)))
      .finally(() => setIsLoadingPledges(false));
  }, [selectedElectionId]);

  async function handleImport() {
    setIsImporting(true);
    setError(null);
    setImportResult(null);
    try {
      const imported = await importNecElection(importForm);
      setImportResult(imported);
      const items = await refreshElections();
      const nextElection = items.find(
        (item) => item.sgId === imported.sgId && item.sgTypecode === imported.sgTypecode
      );
      if (nextElection) {
        setSelectedElectionId(nextElection.id);
      }
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setIsImporting(false);
    }
  }

  async function handleSubmit() {
    setIsSubmitting(true);
    setError(null);
    try {
      const responses = Object.entries(answers).map(([pledgeId, choice]) => ({
        pledgeId: Number(pledgeId),
        choice
      }));
      setResult(await calculateResult({ responses }));
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Pledge Decoding</p>
          <h1>공약 디코딩</h1>
        </div>
        <button className="icon-button" type="button" onClick={() => void refreshElections()} title="새로고침">
          <RefreshCw size={18} />
        </button>
      </header>

      {error && (
        <section className="notice error">
          <AlertCircle size={18} />
          <span>{error}</span>
        </section>
      )}

      <section className="workspace">
        <aside className="side-panel">
          <ImportPanel
            form={importForm}
            onChange={setImportForm}
            onImport={() => void handleImport()}
            isImporting={isImporting}
            importResult={importResult}
            open={elections.length === 0}
          />

          <section className="panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Election</p>
                <h2>선거 선택</h2>
              </div>
              {isLoadingElections && <Loader2 className="spin" size={18} />}
            </div>

            <div className="election-list">
              {elections.length === 0 && !isLoadingElections && (
                <div className="empty-state">아직 적재된 선거 데이터가 없습니다.</div>
              )}
              {elections.map((election) => (
                <button
                  key={election.id}
                  className={`election-item ${election.id === selectedElectionId ? "selected" : ""}`}
                  type="button"
                  onClick={() => setSelectedElectionId(election.id)}
                >
                  <span>
                    <strong>{election.name}</strong>
                    <small>
                      {election.sgId} / type {election.sgTypecode}
                    </small>
                  </span>
                  <em>{election.pledgeCount}개</em>
                </button>
              ))}
            </div>
          </section>
        </aside>

        <section className="play-panel">
          <div className="panel-heading compact">
            <div>
              <p className="eyebrow">Pledge Check</p>
              <h2>{selectedElection ? selectedElection.name : "공약 평가"}</h2>
            </div>
            <div className="progress-pill">
              <ListChecks size={16} />
              <span>{answeredCount}/{pledges.length}</span>
            </div>
          </div>

          <div className="progress-track" aria-label="응답 진행률">
            <span style={{ width: `${progress}%` }} />
          </div>

          {isLoadingPledges && <LoadingBlock label="공약을 불러오는 중" />}

          {!isLoadingPledges && selectedElection && pledges.length === 0 && (
            <div className="empty-state tall">이 선거에는 표시할 공약이 없습니다.</div>
          )}

          {!selectedElection && !isLoadingPledges && (
            <div className="empty-state tall">선거 데이터를 먼저 적재해 주세요.</div>
          )}

          <div className="pledge-list">
            {pledges.map((pledge, index) => (
              <article className="pledge-card" key={pledge.id}>
                <div className="pledge-meta">
                  <span>{index + 1}</span>
                  <em>{pledge.category || "분야 미분류"}</em>
                </div>
                <h3>{pledge.summary || pledge.title}</h3>
                <details>
                  <summary>공식 원문</summary>
                  <p>{pledge.officialText}</p>
                </details>
                <div className="choice-row">
                  {choices.map(({ value, label, score, Icon }) => (
                    <button
                      key={value}
                      className={`choice-button ${answers[pledge.id] === value ? "active" : ""}`}
                      type="button"
                      onClick={() => {
                        setAnswers((prev) => ({ ...prev, [pledge.id]: value }));
                        setResult(null);
                      }}
                    >
                      <Icon size={17} />
                      <span>{label}</span>
                      <small>{score}</small>
                    </button>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="result-panel">
          <section className="panel sticky">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Result</p>
                <h2>공약 일치도</h2>
              </div>
            </div>

            <button
              className="primary-button"
              type="button"
              disabled={answeredCount === 0 || isSubmitting}
              onClick={() => void handleSubmit()}
            >
              {isSubmitting ? <Loader2 className="spin" size={18} /> : <Send size={18} />}
              <span>결과 보기</span>
            </button>

            {result ? <ResultView result={result} /> : <div className="empty-state">응답한 공약 기준으로 계산됩니다.</div>}
          </section>
        </aside>
      </section>
    </main>
  );
}

function ImportPanel({
  form,
  onChange,
  onImport,
  isImporting,
  importResult,
  open
}: {
  form: { sgId: string; sgTypecode: string; sdName: string; sggName: string; candidateLimit: number };
  onChange: (form: { sgId: string; sgTypecode: string; sdName: string; sggName: string; candidateLimit: number }) => void;
  onImport: () => void;
  isImporting: boolean;
  importResult: NecImportResponse | null;
  open: boolean;
}) {
  return (
    <details className="panel import-panel" open={open}>
      <summary>
        <Database size={17} />
        <span>NEC import</span>
      </summary>
      <div className="form-grid">
        <label>
          <span>sgId</span>
          <input value={form.sgId} onChange={(event) => onChange({ ...form, sgId: event.target.value })} />
        </label>
        <label>
          <span>type</span>
          <input value={form.sgTypecode} onChange={(event) => onChange({ ...form, sgTypecode: event.target.value })} />
        </label>
        <label>
          <span>시도</span>
          <input value={form.sdName} onChange={(event) => onChange({ ...form, sdName: event.target.value })} />
        </label>
        <label>
          <span>선거구</span>
          <input value={form.sggName} onChange={(event) => onChange({ ...form, sggName: event.target.value })} />
        </label>
        <label>
          <span>후보 제한</span>
          <input
            min={0}
            type="number"
            value={form.candidateLimit}
            onChange={(event) => onChange({ ...form, candidateLimit: Number(event.target.value) })}
          />
        </label>
      </div>
      <button className="secondary-button" type="button" disabled={isImporting} onClick={onImport}>
        {isImporting ? <Loader2 className="spin" size={17} /> : <Database size={17} />}
        <span>가져오기</span>
      </button>
      {importResult && (
        <div className="import-result">
          <Check size={16} />
          <span>
            후보 {importResult.candidatesSaved}명, 공약 {importResult.pledgesSaved}개
          </span>
        </div>
      )}
    </details>
  );
}

function ResultView({ result }: { result: ResultResponse }) {
  return (
    <div className="result-stack">
      <div className="ranking-list">
        {result.rankings.map((candidate) => (
          <article className="ranking-card" key={candidate.candidateId}>
            <div className="rank-badge">{candidate.rank}</div>
            <div>
              <strong>{candidate.candidateName}</strong>
              <small>
                {candidate.region || "지역 정보 없음"} · {candidate.evaluatedPledgeCount}개 응답
              </small>
            </div>
            <em>{candidate.matchPercentage}%</em>
          </article>
        ))}
      </div>

      <div className="evidence-list">
        <h3>내 응답 근거</h3>
        {result.evidence.map((item) => (
          <article className="evidence-card" key={item.pledgeId}>
            <div>
              <span>{choiceLabel[item.choice]}</span>
              <small>{item.candidateName}</small>
            </div>
            <p>{item.summary || item.title}</p>
          </article>
        ))}
      </div>
    </div>
  );
}

function LoadingBlock({ label }: { label: string }) {
  return (
    <div className="empty-state tall">
      <Loader2 className="spin" size={22} />
      <span>{label}</span>
    </div>
  );
}

function toMessage(error: unknown) {
  return error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.";
}
