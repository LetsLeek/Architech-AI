import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getDesignerReadiness,
  startDesignProposalGeneration,
  type DesignerReadiness,
  type DesignProposalGenerationResult,
} from '../api/designProposals'
import { ApiError, errorMessage } from '../api/http'

// Same structural split as RequirementsAnalysisSection's own AnalysisState (AIW-56): a
// 'runtime-failure' (no candidate ever produced, backend 502) is kept separate from
// 'completed' with succeeded: false (a candidate WAS produced but failed validation, backend
// 201) - conflating them would blur exactly the distinction AIW-164's AC asks for ("actionable
// failure state without pretending a failed run produced canonical designs").
type ActionState =
  | { kind: 'idle' }
  | { kind: 'confirm-regeneration' }
  | { kind: 'running' }
  | { kind: 'completed'; result: DesignProposalGenerationResult }
  | { kind: 'runtime-failure'; message: string }
  | { kind: 'start-error'; message: string }

interface DesignProposalGenerationSectionProps {
  projectId: string
  onViewDesigns?: () => void
  onSucceeded?: () => void
}

function DesignProposalGenerationSection({ projectId, onViewDesigns, onSucceeded }: DesignProposalGenerationSectionProps) {
  const [readiness, setReadiness] = useState<DesignerReadiness | null>(null)
  const [readinessError, setReadinessError] = useState<string | null>(null)
  const [action, setAction] = useState<ActionState>({ kind: 'idle' })

  // Guards against a genuine double-click firing two requests before the 'running' state's
  // re-render has committed and disabled the button (AIW-164's own "prevent accidental
  // double-click/concurrent duplicate generation" AC) - a plain state check isn't quite enough
  // since two click events dispatched in the same tick would both read the pre-update state.
  const submittingRef = useRef(false)

  const refreshReadiness = useCallback(async () => {
    try {
      const loaded = await getDesignerReadiness(projectId)
      setReadiness(loaded)
      setReadinessError(null)
    } catch (err) {
      setReadinessError(errorMessage(err))
    }
  }, [projectId])

  useEffect(() => {
    let cancelled = false
    getDesignerReadiness(projectId)
      .then((loaded) => {
        if (!cancelled) {
          setReadiness(loaded)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setReadinessError(errorMessage(err))
        }
      })
    return () => {
      cancelled = true
    }
  }, [projectId])

  async function handleGenerate() {
    if (submittingRef.current) {
      return
    }
    submittingRef.current = true
    setAction({ kind: 'running' })
    try {
      const result = await startDesignProposalGeneration(projectId)
      setAction({ kind: 'completed', result })
      if (result.succeeded) {
        // The generation just produced (or replaced) the canonical design-proposal-set - load
        // the now-current persisted state rather than assuming what it looks like.
        await refreshReadiness()
        onSucceeded?.()
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 502) {
        setAction({ kind: 'runtime-failure', message: err.message })
      } else {
        setAction({ kind: 'start-error', message: errorMessage(err) })
      }
    } finally {
      submittingRef.current = false
    }
  }

  if (readinessError) {
    return (
      <section className="input-section">
        <h2>Design Proposals</h2>
        <p className="error" role="alert">
          {readinessError}
        </p>
      </section>
    )
  }

  if (!readiness) {
    return (
      <section className="input-section">
        <h2>Design Proposals</h2>
        <p>Loading design readiness…</p>
      </section>
    )
  }

  const submitting = action.kind === 'running'
  const blocked = submitting || readiness.running
  const hasProposalSet = readiness.designProposalSetExists

  return (
    <section className="input-section">
      <h2>Design Proposals</h2>

      {!readiness.ready && (
        <p>Requires a confirmed customer profile and website requirements before Designer can run.</p>
      )}

      {readiness.running && action.kind !== 'running' && (
        <p className="status-badge status-running" role="status">
          A design proposal generation is already running…
        </p>
      )}

      {!hasProposalSet && action.kind !== 'confirm-regeneration' && (
        <>
          <button type="button" onClick={handleGenerate} disabled={!readiness.ready || blocked}>
            {submitting ? 'Generating…' : '3 Designs generieren'}
          </button>
          <p>Creates three design proposals from the confirmed project requirements.</p>
        </>
      )}

      {hasProposalSet && action.kind !== 'confirm-regeneration' && action.kind !== 'running' && (
        <>
          <button type="button" onClick={() => onViewDesigns?.()}>
            Designs ansehen
          </button>
          <button type="button" onClick={() => setAction({ kind: 'confirm-regeneration' })} disabled={!readiness.ready || blocked}>
            Designs neu generieren
          </button>
        </>
      )}

      {action.kind === 'confirm-regeneration' && (
        <div>
          <p>
            This starts a new, paid AI execution. The existing design proposals stay exactly as they are unless and
            until a new run completes successfully - a failed regeneration never overwrites them.
          </p>
          <button type="button" onClick={handleGenerate}>
            Confirm regeneration
          </button>
          <button type="button" onClick={() => setAction({ kind: 'idle' })}>
            Cancel
          </button>
        </div>
      )}

      {submitting && (
        <p className="status-badge status-running" role="status">
          Generating…
        </p>
      )}

      {action.kind === 'start-error' && (
        <p className="error" role="alert">
          {action.message}
        </p>
      )}

      {action.kind === 'runtime-failure' && (
        <div>
          <p className="status-badge status-failed" role="alert">
            Model/runtime failure
          </p>
          <p>{action.message}</p>
          <p>No canonical design proposal set was produced.</p>
        </div>
      )}

      {action.kind === 'completed' && (
        <div>
          <p>
            Execution <code>{action.result.executionId}</code>:{' '}
            <span className={`status-badge ${action.result.succeeded ? 'status-succeeded' : 'status-failed'}`}>
              {action.result.status}
            </span>
          </p>
          {!action.result.succeeded && (
            <>
              <p>Validation failed - the existing canonical design proposal set was not replaced.</p>
              {action.result.validationIssues.length > 0 && (
                <ul>
                  {action.result.validationIssues.map((issue, index) => (
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

export default DesignProposalGenerationSection
