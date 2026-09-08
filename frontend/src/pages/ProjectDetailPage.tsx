import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { getProject, type Project } from '../api/projects'
import FileInputSection from '../components/FileInputSection'
import FreeTextInputSection from '../components/FreeTextInputSection'
import StructuredInputSection from '../components/StructuredInputSection'

function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const [project, setProject] = useState<Project | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!projectId) {
      return
    }
    let cancelled = false
    getProject(projectId)
      .then((loaded) => {
        if (!cancelled) {
          setProject(loaded)
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

  if (error) {
    return (
      <section className="page">
        <p className="error" role="alert">
          {error}
        </p>
      </section>
    )
  }

  if (!project) {
    return (
      <section className="page">
        <p>Loading project…</p>
      </section>
    )
  }

  return (
    <section className="page">
      <h1>{project.projectType} project</h1>
      <dl>
        <dt>ID</dt>
        <dd>{project.id}</dd>
        <dt>Created</dt>
        <dd>{new Date(project.createdAt).toLocaleString()}</dd>
      </dl>

      <FreeTextInputSection projectId={project.id} />
      <StructuredInputSection projectId={project.id} />
      <FileInputSection projectId={project.id} />

      <p>Execution status and results coming soon.</p>
    </section>
  )
}

export default ProjectDetailPage
