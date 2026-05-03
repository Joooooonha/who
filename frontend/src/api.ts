export type SummaryStatus = "PENDING" | "GENERATED" | "FAILED";
export type ResponseChoice = "POSITIVE" | "NEUTRAL" | "NEGATIVE";

export interface ElectionSummary {
  id: number;
  sgId: string;
  sgTypecode: string;
  name: string;
  votedate: string | null;
  candidateCount: number;
  pledgeCount: number;
}

export interface PledgePlay {
  id: number;
  electionId: number;
  category: string | null;
  title: string;
  officialText: string;
  summary: string | null;
  summaryStatus: SummaryStatus;
}

export interface ResultRequest {
  responses: Array<{
    pledgeId: number;
    choice: ResponseChoice;
  }>;
}

export interface CandidateMatch {
  candidateId: number;
  candidateName: string;
  partyName: string | null;
  region: string;
  rank: number;
  evaluatedPledgeCount: number;
  totalScore: number;
  averageScore: number;
  matchPercentage: number;
}

export interface PledgeEvidence {
  pledgeId: number;
  choice: ResponseChoice;
  score: number;
  candidateName: string;
  partyName: string | null;
  region: string;
  category: string | null;
  title: string;
  summary: string | null;
  officialText: string;
}

export interface ResultResponse {
  rankings: CandidateMatch[];
  evidence: PledgeEvidence[];
}

export interface NecImportResponse {
  sgId: string;
  sgTypecode: string;
  electionName: string;
  candidatesFetched: number;
  candidatesSaved: number;
  candidatesWithoutPledges: number;
  pledgesFetched: number;
  pledgesSaved: number;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers
    }
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `HTTP ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function listElections() {
  return request<ElectionSummary[]>("/api/elections");
}

export function listPledges(electionId: number) {
  return request<PledgePlay[]>(`/api/elections/${electionId}/pledges`);
}

export function calculateResult(payload: ResultRequest) {
  return request<ResultResponse>("/api/results", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function importNecElection(params: {
  sgId: string;
  sgTypecode: string;
  sdName?: string;
  sggName?: string;
  candidateLimit?: number;
}) {
  const searchParams = new URLSearchParams();
  searchParams.set("sgId", params.sgId);
  searchParams.set("sgTypecode", params.sgTypecode);
  searchParams.set("candidateLimit", String(params.candidateLimit ?? 10));
  if (params.sdName) searchParams.set("sdName", params.sdName);
  if (params.sggName) searchParams.set("sggName", params.sggName);

  return request<NecImportResponse>(`/api/admin/import/nec?${searchParams.toString()}`, {
    method: "POST"
  });
}
