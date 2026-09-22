import { requestJson } from './http'

export interface DesignerReadiness {
  customerProfileExists: boolean
  websiteRequirementsExists: boolean
  ready: boolean
  running: boolean
  latestExecutionId: string | null
  latestExecutionStatus: string | null
  latestExecutionFailureReason: string | null
  designProposalSetExists: boolean
  designProposalSetArtifactId: string | null
  designProposalSetVersionNumber: number | null
}

export interface DesignProposalGenerationResult {
  executionId: string
  status: string
  succeeded: boolean
  validationIssues: string[]
  provider: string | null
  model: string | null
  promptTokens: number | null
  completionTokens: number | null
  costUsd: number | null
}

export function getDesignerReadiness(projectId: string): Promise<DesignerReadiness> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/design-readiness`)
}

export function startDesignProposalGeneration(projectId: string): Promise<DesignProposalGenerationResult> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/design-proposals`, {
    method: 'POST',
  })
}
