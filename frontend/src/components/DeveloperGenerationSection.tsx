import { useRef, useState } from 'react'
import { startWebsiteGeneration, type WebsiteGenerationResponse } from '../api/websiteGeneration'
import { ApiError, errorMessage } from '../api/http'

// Same structural split as DesignProposalGenerationSection's own ActionState: a 409
// "already-running" (another generation is in flight for this project, backend guard from
// AIW-212) is kept distinct from a generic 'start-error' the same way that component keeps a 502
// runtime-failure distinct from a generic start-error - each needs its own, differently worded,
// actionable message rather than one generic "something went wrong".
type ActionState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'completed'; result: WebsiteGenerationResponse }
  | { kind: 'already-running'; message: string }
  | { kind: 'start-error'; message: string }

interface DeveloperGenerationSectionProps {
  projectId: string
  onSucceeded?: () => void
}

/**
 * Triggers Website Developer generation (AIW-212's POST /website-generation) for a project.
 *
 * <p>Deliberately does NOT gate the button on any client-side readiness check. Unlike Designer
 * generation (which has a real GET /design-readiness endpoint to drive
 * DesignProposalGenerationSection's disabled/explanatory states), no equivalent readiness endpoint
 * exists for Developer generation - and this ticket's own scope note says not to add one just for
 * this UI. Lifting DesignProposalGenerationSection's internal readiness fetch up to
 * ProjectDetailPage so this component could reuse `designProposalSetExists` was the other option,
 * but it would only ever catch one of the backend's real preconditions (a missing canonical
 * design-proposal-set, CANONICAL_ARTIFACT_NOT_FOUND) while silently missing any other real
 * precondition the backend enforces - worse than just always deferring to the backend's own
 * validation and surfacing whatever it says as a plain error message, which is what this does.
 */
function DeveloperGenerationSection({ projectId, onSucceeded }: DeveloperGenerationSectionProps) {
  const [action, setAction] = useState<ActionState>({ kind: 'idle' })

  // Same double-click guard as DesignProposalGenerationSection's submittingRef - a plain state
  // check isn't enough since two click events dispatched in the same tick would both read the
  // pre-update state.
  const submittingRef = useRef(false)

  async function handleGenerate() {
    if (submittingRef.current) {
      return
    }
    submittingRef.current = true
    setAction({ kind: 'running' })
    try {
      const result = await startWebsiteGeneration(projectId)
      setAction({ kind: 'completed', result })
      onSucceeded?.()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setAction({ kind: 'already-running', message: err.message })
      } else {
        setAction({ kind: 'start-error', message: errorMessage(err) })
      }
    } finally {
      submittingRef.current = false
    }
  }

  const submitting = action.kind === 'running'

  return (
    <section className="input-section">
      <h2>Website Generation</h2>
      <p>Runs Website Developer generation for all three A/B/C design proposals, then QA, and Documentation on a QA pass.</p>

      <button type="button" onClick={handleGenerate} disabled={submitting}>
        {submitting ? 'Generating…' : 'Website generieren'}
      </button>

      {submitting && (
        <p className="status-badge status-running" role="status">
          Generating…
        </p>
      )}

      {action.kind === 'already-running' && (
        <p className="error" role="alert">
          {action.message}
        </p>
      )}

      {action.kind === 'start-error' && (
        <p className="error" role="alert">
          {action.message}
        </p>
      )}

      {action.kind === 'completed' && (
        <div>
          <p>
            Overall:{' '}
            <span className={`status-badge ${action.result.allSucceeded ? 'status-succeeded' : 'status-failed'}`}>
              {action.result.allSucceeded ? 'All succeeded' : 'Not all succeeded'}
            </span>
          </p>
          <ul>
            {action.result.siblings.map((sibling) => (
              <li key={sibling.proposalLocalRef}>
                <strong>{sibling.proposalLocalRef}</strong>:{' '}
                <span
                  className={`status-badge ${sibling.candidateId ? 'status-succeeded' : 'status-failed'}`}
                >
                  {sibling.status}
                </span>
                {sibling.executionId && <> · execution <code>{sibling.executionId}</code></>}
                {sibling.infrastructureFailureMessage && <p>{sibling.infrastructureFailureMessage}</p>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

export default DeveloperGenerationSection
