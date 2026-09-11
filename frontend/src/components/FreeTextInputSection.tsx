import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../api/http'
import { listFreeTextInputs, submitFreeTextInput, type FreeTextInput } from '../api/projectInputs'

interface FreeTextInputSectionProps {
  projectId: string
  onCountChange?: (count: number) => void
}

function FreeTextInputSection({ projectId, onCountChange }: FreeTextInputSectionProps) {
  const [inputs, setInputs] = useState<FreeTextInput[]>([])
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listFreeTextInputs(projectId)
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!content.trim()) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await submitFreeTextInput(projectId, content)
      setInputs((prev) => [...prev, created])
      setContent('')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="input-section">
      <h2>Free-text evidence</h2>
      <form onSubmit={handleSubmit}>
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Describe the business, in your own words…"
          rows={4}
        />
        <button type="submit" disabled={submitting || !content.trim()}>
          {submitting ? 'Submitting…' : 'Add evidence'}
        </button>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {inputs.length > 0 && (
        <ul>
          {inputs.map((input) => (
            <li key={input.id}>{input.content}</li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default FreeTextInputSection
