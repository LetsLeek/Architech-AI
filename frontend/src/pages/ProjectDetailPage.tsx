import { useParams } from 'react-router-dom'

function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>()

  return (
    <section>
      <h1>Project {projectId}</h1>
      <p>Requirements input, execution status and results coming soon.</p>
    </section>
  )
}

export default ProjectDetailPage
