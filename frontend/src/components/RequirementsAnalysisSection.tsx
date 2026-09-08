import { useState } from 'react'
import { errorMessage } from '../api/http'
import { startRequirementsAnalysis, type RequirementsAnalysisResult } from '../api/requirementsAnalysis'

function RequirementsAnalysisSection({ projectId, hasInput }: { projectId: string; hasInput: boolean }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<RequirementsAnalysisResult | null>(null)

  async function handleStart() {
    setSubmitting(true)
    setError(null)
    try {
      const started = await startRequirementsAnalysis(projectId)
      setResult(started)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="input-section">
      <h2>Requirements Analysis</h2>
      <button type="button" onClick={handleStart} disabled={submitting || !hasInput}>
        {submitting ? 'Starting…' : 'Start Requirements Analysis'}
      </button>
      {!hasInput && <p>Add at least one piece of evidence above before starting.</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {result && (
        <div>
          <p>
            Execution <code>{result.executionId}</code>:{' '}
            <strong className={result.succeeded ? undefined : 'error'}>{result.status}</strong>
          </p>
          {result.validationIssues.length > 0 && (
            <ul>
              {/* wholesale-replaced per run, never reordered/edited in place - index is fine as a key here */}
              {result.validationIssues.map((issue, index) => (
                <li key={index}>{issue}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

export default RequirementsAnalysisSection
