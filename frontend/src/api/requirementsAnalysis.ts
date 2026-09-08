import { requestJson } from './http'

export interface RequirementsAnalysisResult {
  executionId: string
  status: string
  succeeded: boolean
  validationIssues: string[]
}

export function startRequirementsAnalysis(projectId: string): Promise<RequirementsAnalysisResult> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/requirements-analysis`, {
    method: 'POST',
  })
}
