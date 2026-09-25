import { useEffect, useState } from 'react'
import { getLatestQaResult, type QaResultResponse } from '../api/qaResults'
import { ApiError, errorMessage } from '../api/http'

// Same ViewState shape as DesignProposalSetView - 404 (QA_RESULT_NOT_FOUND) reads as a normal
// "nothing yet" state, not an error.
type ViewState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; result: QaResultResponse }

interface QaResultViewProps {
  projectId: string
  // Bumped by the parent whenever a website generation run just succeeded, so this re-fetches
  // instead of silently keeping a stale (or absent) QA result on screen.
  refreshKey?: number
}

function QaResultView({ projectId, refreshKey }: QaResultViewProps) {
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    getLatestQaResult(projectId)
      .then((result) => {
        if (!cancelled) {
          setState({ kind: 'loaded', result })
        }
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        if (err instanceof ApiError && err.status === 404) {
          setState({ kind: 'not-found' })
        } else {
          setState({ kind: 'error', message: errorMessage(err) })
        }
      })
    return () => {
      cancelled = true
    }
  }, [projectId, refreshKey])

  return (
    <section id="qa-result-view" className="input-section">
      <h2>QA Result</h2>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'not-found' && <p>No QA result yet - generate a website first.</p>}
      {state.kind === 'error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}
      {state.kind === 'loaded' && <QaResultContent result={state.result} />}
    </section>
  )
}

function QaResultContent({ result }: { result: QaResultResponse }) {
  return (
    <div>
      <p>
        Gate outcome: <span className={`status-badge ${result.gateOutcome === 'PASS' ? 'status-succeeded' : 'status-failed'}`}>{result.gateOutcome}</span>
      </p>
      <dl>
        <dt>Evaluation state</dt>
        <dd>{result.evaluationState}</dd>
        <dt>QA profile</dt>
        <dd>{result.qaProfileRef}</dd>
        <dt>Tested candidate</dt>
        <dd>{result.testedCandidateId}</dd>
        <dt>Findings</dt>
        <dd>{result.findingCount}</dd>
        <dt>Authority issues</dt>
        <dd>{result.authorityIssueCount}</dd>
        <dt>Evaluation issues</dt>
        <dd>{result.evaluationIssueCount}</dd>
        <dt>Created</dt>
        <dd>{new Date(result.createdAt).toLocaleString()}</dd>
      </dl>
      {result.holdReasons.length > 0 && (
        <div className="profile-block">
          <h4>Hold reasons</h4>
          <ul>
            {result.holdReasons.map((reason, index) => (
              <li key={index}>{reason}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

export default QaResultView
