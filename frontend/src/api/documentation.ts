import { requestJson } from './http'

// Mirrors DocumentationLineResponse.java (AIW-209) exactly. currentPackageVersion is null only
// for a line with no current package version yet - never happens today (see the Java record's own
// javadoc) but the field stays nullable there, so it stays nullable here too.
export interface DocumentationPackageVersionSummary {
  packageVersionId: string
  revision: number
  audience: string | null
  policyRef: string | null
  semanticArtifactCount: number
  deterministicReportCount: number
  generationReasons: string[]
  createdAt: string
}

export interface DocumentationLineResponse {
  documentationLineId: string
  profileRef: string
  locale: string
  currentRevision: number
  currentPackageVersion: DocumentationPackageVersionSummary | null
}

// Always 200 + an array, never 404 for "nothing generated yet" - see DocumentationController's
// own javadoc. A 404 here only ever means PROJECT_NOT_FOUND.
export function getDocumentationLines(projectId: string): Promise<DocumentationLineResponse[]> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/documentation-packages`)
}
