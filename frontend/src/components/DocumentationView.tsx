import { useEffect, useState } from 'react'
import { getDocumentationLines, type DocumentationLineResponse } from '../api/documentation'
import { errorMessage } from '../api/http'

// Unlike QaResultView/DesignProposalSetView there's no 'not-found' state here - the backend
// returns 200 + an empty array for "nothing generated yet" (see documentation.ts), so that's just
// a normal 'loaded' state with an empty list, not a distinct ViewState branch.
type ViewState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; lines: DocumentationLineResponse[] }

interface DocumentationViewProps {
  projectId: string
  // Bumped by the parent whenever a website generation run just succeeded, so this re-fetches
  // instead of silently keeping stale (or absent) documentation on screen.
  refreshKey?: number
}

function DocumentationView({ projectId, refreshKey }: DocumentationViewProps) {
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    getDocumentationLines(projectId)
      .then((lines) => {
        if (!cancelled) {
          setState({ kind: 'loaded', lines })
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ kind: 'error', message: errorMessage(err) })
        }
      })
    return () => {
      cancelled = true
    }
  }, [projectId, refreshKey])

  return (
    <section id="documentation-view" className="input-section">
      <h2>Documentation</h2>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}
      {state.kind === 'loaded' && state.lines.length === 0 && <p>No documentation generated yet.</p>}
      {state.kind === 'loaded' && state.lines.length > 0 && (
        <ul>
          {state.lines.map((line) => (
            <DocumentationLineItem key={line.documentationLineId} line={line} />
          ))}
        </ul>
      )}
    </section>
  )
}

function DocumentationLineItem({ line }: { line: DocumentationLineResponse }) {
  return (
    <li>
      <strong>{line.profileRef}</strong> ({line.locale}) - revision {line.currentRevision}
      {line.currentPackageVersion ? (
        <ul>
          <li>Audience: {line.currentPackageVersion.audience ?? 'unknown'}</li>
          <li>Policy: {line.currentPackageVersion.policyRef ?? 'unknown'}</li>
          <li>Semantic artifacts: {line.currentPackageVersion.semanticArtifactCount}</li>
          <li>Deterministic reports: {line.currentPackageVersion.deterministicReportCount}</li>
          {line.currentPackageVersion.generationReasons.length > 0 && (
            <li>Generation reasons: {line.currentPackageVersion.generationReasons.join(', ')}</li>
          )}
        </ul>
      ) : (
        <p>No current package version.</p>
      )}
    </li>
  )
}

export default DocumentationView
