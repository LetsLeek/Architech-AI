import { requestJson } from './http'

// Mirrors QaResultResponse.java (AIW-208) exactly - never the raw domainResultsJson/provenanceJson
// blob, just the gate outcome, hold reasons, and per-kind counts.
export interface QaResultResponse {
  qaResultId: string
  qaExecutionId: string
  testedCandidateId: string
  qaProfileRef: string
  evaluationState: string
  gateOutcome: string
  holdReasons: string[]
  findingCount: number
  authorityIssueCount: number
  evaluationIssueCount: number
  createdAt: string
}

export function getLatestQaResult(projectId: string): Promise<QaResultResponse> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/qa-results`)
}
