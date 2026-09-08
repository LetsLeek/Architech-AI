import { useEffect, useState } from 'react'
import { getWebsiteRequirements, type ArtifactVersionInfo, type WebsiteRequirements } from '../api/artifacts'
import { ApiError, errorMessage } from '../api/http'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; artifact: ArtifactVersionInfo<WebsiteRequirements> }

interface WebsiteRequirementsViewProps {
  projectId: string
  // Bumped by the parent whenever a Requirements Analysis run just succeeded, so this
  // re-fetches instead of silently keeping stale (or absent) requirements on screen.
  refreshKey?: number
}

function WebsiteRequirementsView({ projectId, refreshKey }: WebsiteRequirementsViewProps) {
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    getWebsiteRequirements(projectId)
      .then((artifact) => {
        if (!cancelled) {
          setState({ kind: 'loaded', artifact })
        }
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        if (err instanceof ApiError && err.status === 404) {
          setState({ kind: 'not-found' })
        } else {
          setState({ kind: 'error', message: errorMessage(err) })
        }
      })
    return () => {
      cancelled = true
    }
  }, [projectId, refreshKey])

  return (
    <section className="input-section">
      <h2>Website Requirements</h2>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'not-found' && (
        <p>No canonical Website Requirements yet - run a successful Requirements Analysis first.</p>
      )}
      {state.kind === 'error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}
      {state.kind === 'loaded' && <WebsiteRequirementsContent artifact={state.artifact} />}
    </section>
  )
}

function WebsiteRequirementsContent({ artifact }: { artifact: ArtifactVersionInfo<WebsiteRequirements> }) {
  const requirements = artifact.content

  return (
    <div>
      <p className="artifact-meta">
        Version {artifact.versionNumber} · {new Date(artifact.createdAt).toLocaleString()}
      </p>

      {requirements.goals.length > 0 && (
        <div className="profile-block">
          <h3>Goals</h3>
          <ul>
            {requirements.goals.map((goal) => (
              <li key={goal.localRef}>
                <span className="strength-tag">{goal.strength}</span> {goal.description}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.targetAudiences.length > 0 && (
        <div className="profile-block">
          <h3>Target Audiences</h3>
          <ul>
            {requirements.targetAudiences.map((audience) => (
              <li key={audience.localRef}>{audience.description}</li>
            ))}
          </ul>
        </div>
      )}

      {requirements.contentRequirements.length > 0 && (
        <div className="profile-block">
          <h3>Content Requirements</h3>
          <ul>
            {requirements.contentRequirements.map((requirement) => (
              <li key={requirement.localRef}>
                <span className="strength-tag">{requirement.strength}</span>{' '}
                <em>{requirement.type === 'custom' && requirement.customType ? requirement.customType : requirement.type}</em>:{' '}
                {requirement.description}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.functionalRequirements.length > 0 && (
        <div className="profile-block">
          <h3>Functional Requirements</h3>
          <ul>
            {requirements.functionalRequirements.map((requirement) => (
              <li key={requirement.localRef}>
                <span className="strength-tag">{requirement.strength}</span>{' '}
                <em>{requirement.type === 'custom' && requirement.customType ? requirement.customType : requirement.type}</em>:{' '}
                {requirement.description}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.languages.length > 0 && (
        <div className="profile-block">
          <h3>Languages</h3>
          <ul>
            {requirements.languages.map((language) => (
              <li key={language.code}>
                <span className="strength-tag">{language.strength}</span> {language.code}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.constraints.length > 0 && (
        <div className="profile-block">
          <h3>Constraints</h3>
          <ul>
            {requirements.constraints.map((constraint) => (
              <li key={constraint.localRef}>
                <span className="strength-tag">{constraint.strength}</span> <em>{constraint.category}</em>: {constraint.description}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.unknowns.length > 0 && (
        <div className="profile-block">
          <h3>Unknowns</h3>
          <ul>
            {requirements.unknowns.map((unknown, index) => (
              // backend content, wholesale-replaced per fetch - index is fine as a key here
              <li key={index}>
                <strong>{unknown.kind}</strong>: {unknown.description}
                {unknown.affects && unknown.affects.length > 0 && ` (affects: ${unknown.affects.join(', ')})`}
              </li>
            ))}
          </ul>
        </div>
      )}

      {requirements.conflicts.length > 0 && (
        <div className="profile-block">
          <h3>Conflicts</h3>
          <ul>
            {requirements.conflicts.map((conflict, index) => (
              // backend content, wholesale-replaced per fetch - index is fine as a key here
              <li key={index}>
                {conflict.description}
                {conflict.affects && conflict.affects.length > 0 && ` (affects: ${conflict.affects.join(', ')})`}
                <ul>
                  {conflict.statements.map((statement, statementIndex) => (
                    <li key={statementIndex}>{statement.description}</li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

export default WebsiteRequirementsView
