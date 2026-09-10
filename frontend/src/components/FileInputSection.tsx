import { useEffect, useRef, useState, type FormEvent } from 'react'
import { errorMessage } from '../api/http'
import { listFileInputs, uploadFileInput, type FileInput } from '../api/projectInputs'

interface FileInputSectionProps {
  projectId: string
  onCountChange?: (count: number) => void
}

function FileInputSection({ projectId, onCountChange }: FileInputSectionProps) {
  const [inputs, setInputs] = useState<FileInput[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    listFileInputs(projectId)
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
    const file = fileInputRef.current?.files?.[0]
    if (!file) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await uploadFileInput(projectId, file)
      setInputs((prev) => [...prev, created])
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="input-section">
      <h2>File evidence</h2>
      <form onSubmit={handleSubmit}>
        <label>
          Evidence file
          <input type="file" ref={fileInputRef} />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Uploading…' : 'Upload file'}
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
            <li key={input.id}>
              {input.filename} ({input.sizeBytes} bytes)
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default FileInputSection
