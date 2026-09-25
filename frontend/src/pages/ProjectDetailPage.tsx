import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { errorMessage } from '../api/http'
import { getProject, type Project } from '../api/projects'
import CustomerProfileView from '../components/CustomerProfileView'
import DesignProposalGenerationSection from '../components/DesignProposalGenerationSection'
import DesignProposalSetView from '../components/DesignProposalSetView'
import DeveloperGenerationSection from '../components/DeveloperGenerationSection'
import DocumentationView from '../components/DocumentationView'
import FileInputSection from '../components/FileInputSection'
import FreeTextInputSection from '../components/FreeTextInputSection'
import QaResultView from '../components/QaResultView'
import RequirementsAnalysisSection from '../components/RequirementsAnalysisSection'
import StructuredInputSection from '../components/StructuredInputSection'
import WebsiteRequirementsView from '../components/WebsiteRequirementsView'

function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const [project, setProject] = useState<Project | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Tracked here (rather than inside RequirementsAnalysisSection) because "is there any
  // input yet" spans all three input types, each owning its own list independently.
  const [freeTextCount, setFreeTextCount] = useState(0)
  const [structuredCount, setStructuredCount] = useState(0)
  const [fileCount, setFileCount] = useState(0)
  const [profileRefreshKey, setProfileRefreshKey] = useState(0)
  const [designsRefreshKey, setDesignsRefreshKey] = useState(0)
  // Bumped once a Developer generation run succeeds. QA and (on a QA pass) Documentation both run
  // synchronously inside that same backend call (AIW-213), so a fetch right after success already
  // sees fresh results for both - no polling needed.
  const [websiteGenerationRefreshKey, setWebsiteGenerationRefreshKey] = useState(0)

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

  const handleFreeTextCountChange = useCallback((count: number) => setFreeTextCount(count), [])
  const handleStructuredCountChange = useCallback((count: number) => setStructuredCount(count), [])
  const handleFileCountChange = useCallback((count: number) => setFileCount(count), [])

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

      <FreeTextInputSection projectId={project.id} onCountChange={handleFreeTextCountChange} />
      <StructuredInputSection projectId={project.id} onCountChange={handleStructuredCountChange} />
      <FileInputSection projectId={project.id} onCountChange={handleFileCountChange} />

      <RequirementsAnalysisSection
        projectId={project.id}
        hasInput={freeTextCount + structuredCount + fileCount > 0}
        onSucceeded={() => setProfileRefreshKey((key) => key + 1)}
      />

      <CustomerProfileView projectId={project.id} refreshKey={profileRefreshKey} />
      <WebsiteRequirementsView projectId={project.id} refreshKey={profileRefreshKey} />

      <DesignProposalGenerationSection
        projectId={project.id}
        onSucceeded={() => setDesignsRefreshKey((key) => key + 1)}
        onViewDesigns={() => document.getElementById('design-proposal-set-view')?.scrollIntoView({ behavior: 'smooth' })}
      />
      <DesignProposalSetView projectId={project.id} refreshKey={designsRefreshKey} />

      <DeveloperGenerationSection
        projectId={project.id}
        onSucceeded={() => setWebsiteGenerationRefreshKey((key) => key + 1)}
      />
      <QaResultView projectId={project.id} refreshKey={websiteGenerationRefreshKey} />
      <DocumentationView projectId={project.id} refreshKey={websiteGenerationRefreshKey} />
    </section>
  )
}

export default ProjectDetailPage
