import { requestJson } from './http'

export interface RequirementsAnalysisResult {
  executionId: string
  status: string
  succeeded: boolean
  validationIssues: string[]
  // AIW-130: null together exactly when the execution never reached a real AI call - never
  // fabricated as zero.
  provider: string | null
  model: string | null
  promptTokens: number | null
  completionTokens: number | null
  costUsd: number | null
}

export function startRequirementsAnalysis(projectId: string): Promise<RequirementsAnalysisResult> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/requirements-analysis`, {
    method: 'POST',
  })
}
