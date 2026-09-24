import { useEffect, useState } from 'react'
import {
  getDesignProposalSet,
  type ArtifactVersionInfo,
  type DesignProposal,
  type DesignProposalSet,
} from '../api/artifacts'
import { ApiError, errorMessage } from '../api/http'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; artifact: ArtifactVersionInfo<DesignProposalSet> }

interface DesignProposalSetViewProps {
  projectId: string
  // Bumped by the parent whenever a Designer generation run just succeeded, so this re-fetches
  // instead of silently keeping a stale (or absent) proposal set on screen.
  refreshKey?: number
}

function DesignProposalSetView({ projectId, refreshKey }: DesignProposalSetViewProps) {
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    getDesignProposalSet(projectId)
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
    <section id="design-proposal-set-view" className="input-section proposal-set-section">
      <h2>Design Proposal Results</h2>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'not-found' && <p>No canonical Design Proposal Set yet - generate 3 designs first.</p>}
      {state.kind === 'error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}
      {state.kind === 'loaded' && <DesignProposalSetContent artifact={state.artifact} />}
    </section>
  )
}

function DesignProposalSetContent({ artifact }: { artifact: ArtifactVersionInfo<DesignProposalSet> }) {
  return (
    <div>
      <p className="artifact-meta">
        Version {artifact.versionNumber} · {new Date(artifact.createdAt).toLocaleString()}
      </p>
      {/* These are three independent, equally-weighted concepts, not rendered as previews of a
          real running website - array order here reflects nothing but Designer output order and
          must never read as a ranking or a recommendation. */}
      <p>Three independent design concepts. None is recommended or ranked above the others.</p>
      <div className="proposal-grid">
        {artifact.content.proposals.map((proposal) => (
          <DesignProposalCard key={proposal.localRef} proposal={proposal} />
        ))}
      </div>
    </div>
  )
}

function DesignProposalCard({ proposal }: { proposal: DesignProposal }) {
  const { colors, typography, layout, imagery, responsive } = proposal.designSpecification

  return (
    <article className="proposal-card">
      <h3>{proposal.name}</h3>
      <p>{proposal.concept}</p>

      <div className="profile-block">
        <h4>Pages</h4>
        <ul>
          {proposal.websitePlan.pages.map((page) => (
            <li key={page.localRef}>
              <strong>{page.name}</strong> <code>{page.route}</code>
              {page.sections.length > 0 && (
                <ul>
                  {page.sections.map((section) => (
                    <li key={section.localRef}>{section.kind === 'custom' ? section.customKind : section.kind}</li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      </div>

      <div className="profile-block">
        <h4>Colors</h4>
        <ul>
          {colors.map((color, index) => (
            // backend content with no stable id beyond role, which the schema doesn't enforce
            // unique - index is fine as a key here.
            <li key={index}>
              <span className="color-swatch" style={{ backgroundColor: color.value }} /> {color.role}: {color.value}
            </li>
          ))}
        </ul>
      </div>

      <div className="profile-block">
        <h4>Typography</h4>
        <ul>
          {typography.map((token, index) => (
            <li key={index}>
              {token.role}: {token.fontFamily}, {token.fontWeight}, {token.fontSizeRem}rem / {token.lineHeight}
            </li>
          ))}
        </ul>
      </div>

      <div className="profile-block">
        <h4>Layout</h4>
        <p>
          <span className="strength-tag">{layout.contentWidth}</span> <span className="strength-tag">{layout.density}</span>{' '}
          {layout.gridIntent}
        </p>
      </div>

      <div className="profile-block">
        <h4>Imagery</h4>
        <p>
          {imagery.direction} - {imagery.treatment}
        </p>
      </div>

      <div className="profile-block">
        <h4>Responsive Behavior</h4>
        <ul>
          <li>Navigation: {responsive.navigationBehavior}</li>
          <li>Content stacking: {responsive.contentStacking}</li>
          <li>Type scaling: {responsive.typeScaling}</li>
          <li>Spacing: {responsive.spacingAdjustment}</li>
          <li>Media: {responsive.mediaBehavior}</li>
        </ul>
      </div>
    </article>
  )
}

export default DesignProposalSetView
