import { requestJson } from './http'

// Mirrors WebsiteGenerationResponse.java (AIW-212) exactly - one entry per A/B/C sibling.
// executionId/candidateId/infrastructureFailureMessage are only ever null together, for a
// sibling that never even got an AgentExecution (an infrastructure failure before that point).
export interface WebsiteGenerationSiblingResult {
  proposalLocalRef: string
  executionId: string | null
  status: string
  candidateId: string | null
  infrastructureFailureMessage: string | null
}

export interface WebsiteGenerationResponse {
  allSucceeded: boolean
  siblings: WebsiteGenerationSiblingResult[]
}

export function startWebsiteGeneration(projectId: string): Promise<WebsiteGenerationResponse> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/website-generation`, {
    method: 'POST',
  })
}
