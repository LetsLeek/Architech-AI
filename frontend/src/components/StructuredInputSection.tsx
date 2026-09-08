import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../api/http'
import { listStructuredInputs, submitStructuredInput, type StructuredInput } from '../api/projectInputs'

interface FieldRow {
  id: string
  key: string
  value: string
}

function emptyRow(): FieldRow {
  return { id: crypto.randomUUID(), key: '', value: '' }
}

interface StructuredInputSectionProps {
  projectId: string
  onCountChange?: (count: number) => void
}

function StructuredInputSection({ projectId, onCountChange }: StructuredInputSectionProps) {
  const [inputs, setInputs] = useState<StructuredInput[]>([])
  const [rows, setRows] = useState<FieldRow[]>([emptyRow()])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listStructuredInputs(projectId)
      .then((loaded) => {
        if (!cancelled) {
          setInputs(loaded)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(errorMessage(err))
        }
      })
    return () => {
      cancelled = true
    }
  }, [projectId])

  useEffect(() => {
    onCountChange?.(inputs.length)
  }, [inputs.length, onCountChange])

  function updateRow(id: string, patch: Partial<Pick<FieldRow, 'key' | 'value'>>) {
    setRows((prev) => prev.map((row) => (row.id === id ? { ...row, ...patch } : row)))
  }

  function removeRow(id: string) {
    setRows((prev) => prev.filter((row) => row.id !== id))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Field names are structural keys the customer chose - values are their own words, passed
    // through untouched (only whitespace-only field names are dropped, not rewritten).
    const fields = Object.fromEntries(
      rows.map((row) => [row.key.trim(), row.value] as const).filter(([key]) => key.length > 0),
    )
    if (Object.keys(fields).length === 0) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await submitStructuredInput(projectId, fields)
      setInputs((prev) => [...prev, created])
      setRows([emptyRow()])
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="input-section">
      <h2>Structured evidence</h2>
      <form onSubmit={handleSubmit}>
        {rows.map((row) => (
          <div className="field-row" key={row.id}>
            <input
              type="text"
              placeholder="Field name (e.g. openingHours)"
              value={row.key}
              onChange={(event) => updateRow(row.id, { key: event.target.value })}
            />
            <input
              type="text"
              placeholder="Value"
              value={row.value}
              onChange={(event) => updateRow(row.id, { value: event.target.value })}
            />
            {rows.length > 1 && (
              <button type="button" onClick={() => removeRow(row.id)} aria-label="Remove field">
                ×
              </button>
            )}
          </div>
        ))}
        <div className="field-row-actions">
          <button type="button" onClick={() => setRows((prev) => [...prev, emptyRow()])}>
            + Add field
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? 'Submitting…' : 'Add evidence'}
          </button>
        </div>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {inputs.length > 0 && (
        <ul>
          {inputs.map((input) => (
            <li key={input.id}>
              {Object.entries(input.fields)
                .map(([key, value]) => `${key}: ${value}`)
                .join(', ')}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default StructuredInputSection
