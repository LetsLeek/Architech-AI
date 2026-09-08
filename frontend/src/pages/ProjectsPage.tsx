import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { createProject } from '../api/projects'

function ProjectsPage() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleCreate() {
    setSubmitting(true)
    setError(null)
    try {
      const project = await createProject('website')
      navigate(`/projects/${project.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <section className="page">
      <h1>Website Projects</h1>
      <p>Start a new Website Project to begin gathering requirements.</p>
      <button type="button" onClick={handleCreate} disabled={submitting}>
        {submitting ? 'Creating…' : 'Create Website Project'}
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}

export default ProjectsPage
