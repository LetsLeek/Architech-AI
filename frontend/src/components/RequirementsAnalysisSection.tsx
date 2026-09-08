import { useState } from 'react'
import { ApiError, errorMessage } from '../api/http'
import { startRequirementsAnalysis, type RequirementsAnalysisResult } from '../api/requirementsAnalysis'

// Distinguishes every state the backend can actually put this run in (AIW-56 AC). PENDING
// isn't separately observable here - the run is synchronous, so the request is already
// RUNNING by the time anything happens, and it resolves straight to a terminal state.
// 'runtime-failure' (the model/runtime layer failed, no candidate ever produced - backend
// reports it as 502) is kept structurally separate from 'completed' with succeeded: false
// (a candidate WAS produced but failed deterministic validation, reported as 201) - conflating
// them would blur exactly the distinction this ticket asks for.
type AnalysisState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'completed'; result: RequirementsAnalysisResult }
  | { kind: 'runtime-failure'; message: string }
  | { kind: 'start-error'; message: string }

function RequirementsAnalysisSection({ projectId, hasInput }: { projectId: string; hasInput: boolean }) {
  const [state, setState] = useState<AnalysisState>({ kind: 'idle' })

  async function handleStart() {
    setState({ kind: 'running' })
    try {
      const result = await startRequirementsAnalysis(projectId)
      setState({ kind: 'completed', result })
    } catch (err) {
      if (err instanceof ApiError && err.status === 502) {
        setState({ kind: 'runtime-failure', message: err.message })
      } else {
        setState({ kind: 'start-error', message: errorMessage(err) })
      }
    }
  }

  const submitting = state.kind === 'running'

  return (
    <section className="input-section">
      <h2>Requirements Analysis</h2>
      <button type="button" onClick={handleStart} disabled={submitting || !hasInput}>
        {submitting ? 'Starting…' : 'Start Requirements Analysis'}
      </button>
      {!hasInput && state.kind === 'idle' && <p>Add at least one piece of evidence above before starting.</p>}

      {state.kind === 'running' && (
        <p className="status-badge status-running" role="status">
          Running…
        </p>
      )}

      {state.kind === 'start-error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}

      {state.kind === 'runtime-failure' && (
        <div>
          <p className="status-badge status-failed" role="alert">
            Model/runtime failure
          </p>
          <p>{state.message}</p>
          <p>No canonical output was produced.</p>
        </div>
      )}

      {state.kind === 'completed' && (
        <div>
          <p>
            Execution <code>{state.result.executionId}</code>:{' '}
            <span className={`status-badge ${state.result.succeeded ? 'status-succeeded' : 'status-failed'}`}>
              {state.result.status}
            </span>
          </p>
          {!state.result.succeeded && (
            <>
              <p>Validation failed - no canonical output was produced.</p>
              {state.result.validationIssues.length > 0 && (
                <ul>
                  {/* wholesale-replaced per run, never reordered/edited in place - index is fine as a key here */}
                  {state.result.validationIssues.map((issue, index) => (
                    <li key={index}>{issue}</li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}

export default RequirementsAnalysisSection
