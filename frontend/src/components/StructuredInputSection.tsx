import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../api/http'
import { listStructuredInputs, submitStructuredInput, type StructuredInput } from '../api/projectInputs'

interface StructuredInputSectionProps {
  projectId: string
  onCountChange?: (count: number) => void
}

// Mirrors the canonical customer-profile shape (business/contact/location/openingHours/
// offerings/socialLinks) the Requirements Agent extracts into - not a backend requirement
// (the API accepts any free-form key/value map), just a prompt so nobody has to guess what a
// website typically needs or remember every field from scratch.
interface BusinessBasics {
  businessName: string
  businessDescription: string
  phone: string
  email: string
  website: string
  street: string
  postalCode: string
  city: string
  country: string
}

function emptyBasics(): BusinessBasics {
  return {
    businessName: '',
    businessDescription: '',
    phone: '',
    email: '',
    website: '',
    street: '',
    postalCode: '',
    city: '',
    country: '',
  }
}

const BASICS_FIELDS: { key: keyof BusinessBasics; label: string; placeholder: string; multiline?: boolean }[] = [
  { key: 'businessName', label: 'Business name', placeholder: 'e.g. Müller Kfz-Werkstatt' },
  {
    key: 'businessDescription',
    label: 'Short description',
    placeholder: 'What does the business do, in a sentence or two?',
    multiline: true,
  },
  { key: 'phone', label: 'Phone', placeholder: '+43 1 2345678' },
  { key: 'email', label: 'Email', placeholder: 'info@example.com' },
  { key: 'website', label: 'Existing website (if any)', placeholder: 'https://…' },
  { key: 'street', label: 'Street address', placeholder: 'Hauptstraße 12' },
  { key: 'postalCode', label: 'Postal code', placeholder: '1010' },
  { key: 'city', label: 'City', placeholder: 'Wien' },
  { key: 'country', label: 'Country', placeholder: 'Austria' },
]

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'] as const

interface OpeningHoursDay {
  day: (typeof DAYS)[number]
  open: boolean
  from: string
  to: string
}

function emptyOpeningHours(): OpeningHoursDay[] {
  return DAYS.map((day) => ({ day, open: false, from: '09:00', to: '18:00' }))
}

interface RepeatableRow {
  id: string
  a: string
  b: string
}

function emptyRepeatableRow(): RepeatableRow {
  return { id: crypto.randomUUID(), a: '', b: '' }
}

interface CustomFieldRow {
  id: string
  key: string
  value: string
}

function emptyCustomRow(): CustomFieldRow {
  return { id: crypto.randomUUID(), key: '', value: '' }
}

function StructuredInputSection({ projectId, onCountChange }: StructuredInputSectionProps) {
  const [inputs, setInputs] = useState<StructuredInput[]>([])
  const [basics, setBasics] = useState<BusinessBasics>(emptyBasics())
  const [openingHours, setOpeningHours] = useState<OpeningHoursDay[]>(emptyOpeningHours())
  const [offerings, setOfferings] = useState<RepeatableRow[]>([emptyRepeatableRow()])
  const [socialLinks, setSocialLinks] = useState<RepeatableRow[]>([emptyRepeatableRow()])
  const [customRows, setCustomRows] = useState<CustomFieldRow[]>([emptyCustomRow()])
  const [showCustomRows, setShowCustomRows] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [justSaved, setJustSaved] = useState(false)

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

  function updateOpeningHoursDay(day: string, patch: Partial<Omit<OpeningHoursDay, 'day'>>) {
    setOpeningHours((prev) => prev.map((row) => (row.day === day ? { ...row, ...patch } : row)))
  }

  function updateOffering(id: string, patch: Partial<Pick<RepeatableRow, 'a' | 'b'>>) {
    setOfferings((prev) => prev.map((row) => (row.id === id ? { ...row, ...patch } : row)))
  }

  function updateSocialLink(id: string, patch: Partial<Pick<RepeatableRow, 'a' | 'b'>>) {
    setSocialLinks((prev) => prev.map((row) => (row.id === id ? { ...row, ...patch } : row)))
  }

  function updateCustomRow(id: string, patch: Partial<Pick<CustomFieldRow, 'key' | 'value'>>) {
    setCustomRows((prev) => prev.map((row) => (row.id === id ? { ...row, ...patch } : row)))
  }

  function buildFields(): Record<string, string> {
    const fields: Record<string, string> = {}

    for (const { key, label } of BASICS_FIELDS) {
      const value = basics[key].trim()
      if (value) {
        fields[label] = value
      }
    }

    for (const row of openingHours) {
      if (row.open && row.from && row.to) {
        fields[`Opening hours - ${row.day}`] = `${row.from}–${row.to}`
      }
    }

    offerings
      .filter((row) => row.a.trim())
      .forEach((row, index) => {
        const label = row.b.trim() ? `${row.a.trim()} (${row.b.trim()})` : row.a.trim()
        fields[`Service/offering ${index + 1}`] = label
      })

    socialLinks
      .filter((row) => row.a.trim() && row.b.trim())
      .forEach((row) => {
        fields[`Social media: ${row.a.trim()}`] = row.b.trim()
      })

    for (const row of customRows) {
      const key = row.key.trim()
      if (key) {
        fields[key] = row.value
      }
    }

    return fields
  }

  const hasAnyValue =
    BASICS_FIELDS.some(({ key }) => basics[key].trim()) ||
    openingHours.some((row) => row.open) ||
    offerings.some((row) => row.a.trim()) ||
    socialLinks.some((row) => row.a.trim() && row.b.trim()) ||
    customRows.some((row) => row.key.trim())

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = buildFields()
    if (Object.keys(fields).length === 0) {
      return
    }
    setSubmitting(true)
    setError(null)
    setJustSaved(false)
    try {
      const created = await submitStructuredInput(projectId, fields)
      setInputs((prev) => [...prev, created])
      setJustSaved(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="input-section structured-input-section">
      <h2>Business details</h2>
      <p className="section-hint">
        Fill in whatever you know - everything here is optional, and you can come back and add more later. These are
        the details a typical business website needs, so nothing gets forgotten.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="field-grid">
          {BASICS_FIELDS.map(({ key, label, placeholder, multiline }) => (
            <label className="field-label" key={key}>
              <span>{label}</span>
              {multiline ? (
                <textarea
                  rows={2}
                  value={basics[key]}
                  placeholder={placeholder}
                  onChange={(event) => setBasics((prev) => ({ ...prev, [key]: event.target.value }))}
                />
              ) : (
                <input
                  type="text"
                  value={basics[key]}
                  placeholder={placeholder}
                  onChange={(event) => setBasics((prev) => ({ ...prev, [key]: event.target.value }))}
                />
              )}
            </label>
          ))}
        </div>

        <h3>Opening hours</h3>
        <div className="opening-hours-grid">
          {openingHours.map((row) => (
            <div className="opening-hours-row" key={row.day}>
              <label className="opening-hours-day">
                <input
                  type="checkbox"
                  checked={row.open}
                  onChange={(event) => updateOpeningHoursDay(row.day, { open: event.target.checked })}
                />
                {row.day}
              </label>
              {row.open && (
                <span className="opening-hours-times">
                  <input
                    type="time"
                    value={row.from}
                    onChange={(event) => updateOpeningHoursDay(row.day, { from: event.target.value })}
                  />
                  <span>–</span>
                  <input
                    type="time"
                    value={row.to}
                    onChange={(event) => updateOpeningHoursDay(row.day, { to: event.target.value })}
                  />
                </span>
              )}
            </div>
          ))}
        </div>

        <h3>Services / offerings</h3>
        {offerings.map((row) => (
          <div className="field-row" key={row.id}>
            <input
              type="text"
              placeholder="Service or product name"
              value={row.a}
              onChange={(event) => updateOffering(row.id, { a: event.target.value })}
            />
            <input
              type="text"
              placeholder="Price (optional)"
              value={row.b}
              onChange={(event) => updateOffering(row.id, { b: event.target.value })}
            />
            {offerings.length > 1 && (
              <button type="button" onClick={() => setOfferings((prev) => prev.filter((r) => r.id !== row.id))} aria-label="Remove">
                ×
              </button>
            )}
          </div>
        ))}
        <button type="button" className="add-row-button" onClick={() => setOfferings((prev) => [...prev, emptyRepeatableRow()])}>
          + Add service
        </button>

        <h3>Social media</h3>
        {socialLinks.map((row) => (
          <div className="field-row" key={row.id}>
            <input
              type="text"
              placeholder="Platform (e.g. Instagram)"
              value={row.a}
              onChange={(event) => updateSocialLink(row.id, { a: event.target.value })}
            />
            <input
              type="text"
              placeholder="URL"
              value={row.b}
              onChange={(event) => updateSocialLink(row.id, { b: event.target.value })}
            />
            {socialLinks.length > 1 && (
              <button
                type="button"
                onClick={() => setSocialLinks((prev) => prev.filter((r) => r.id !== row.id))}
                aria-label="Remove"
              >
                ×
              </button>
            )}
          </div>
        ))}
        <button type="button" className="add-row-button" onClick={() => setSocialLinks((prev) => [...prev, emptyRepeatableRow()])}>
          + Add social link
        </button>

        <h3>
          Anything else?{' '}
          <button type="button" className="toggle-link" onClick={() => setShowCustomRows((prev) => !prev)}>
            {showCustomRows ? 'Hide' : 'Add a custom detail'}
          </button>
        </h3>
        {showCustomRows && (
          <>
            <p className="section-hint">
              For anything not covered above - give it a short, clear name and its value, e.g. "Parking" /
              "Free customer parking behind the building".
            </p>
            {customRows.map((row) => (
              <div className="field-row" key={row.id}>
                <input
                  type="text"
                  placeholder="What is this about? (e.g. Parking)"
                  value={row.key}
                  onChange={(event) => updateCustomRow(row.id, { key: event.target.value })}
                />
                <input
                  type="text"
                  placeholder="Details"
                  value={row.value}
                  onChange={(event) => updateCustomRow(row.id, { value: event.target.value })}
                />
                {customRows.length > 1 && (
                  <button type="button" onClick={() => setCustomRows((prev) => prev.filter((r) => r.id !== row.id))} aria-label="Remove">
                    ×
                  </button>
                )}
              </div>
            ))}
            <button type="button" className="add-row-button" onClick={() => setCustomRows((prev) => [...prev, emptyCustomRow()])}>
              + Add another detail
            </button>
          </>
        )}

        <div className="field-row-actions">
          <button type="submit" disabled={submitting || !hasAnyValue}>
            {submitting ? 'Saving…' : 'Save business details'}
          </button>
          {justSaved && <span className="save-confirmation">Saved.</span>}
        </div>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {inputs.length > 0 && (
        <details className="structured-input-history">
          <summary>
            {inputs.length} submission{inputs.length === 1 ? '' : 's'} saved so far
          </summary>
          <ul>
            {inputs.map((input) => (
              <li key={input.id}>
                {Object.entries(input.fields)
                  .map(([key, value]) => `${key}: ${value}`)
                  .join(', ')}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

export default StructuredInputSection
