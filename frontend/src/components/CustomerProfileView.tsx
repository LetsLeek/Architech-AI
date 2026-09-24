import { useEffect, useState } from 'react'
import { getCustomerProfile, type ArtifactVersionInfo, type CustomerProfile } from '../api/artifacts'
import { ApiError, errorMessage } from '../api/http'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; artifact: ArtifactVersionInfo<CustomerProfile> }

interface CustomerProfileViewProps {
  projectId: string
  // Bumped by the parent whenever a Requirements Analysis run just succeeded, so this
  // re-fetches instead of silently keeping a stale (or absent) profile on screen.
  refreshKey?: number
}

function CustomerProfileView({ projectId, refreshKey }: CustomerProfileViewProps) {
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    // Deliberately doesn't reset to 'loading' here (that would keep re-triggering a lint
    // warning about setState-in-effect, and it isn't great UX anyway) - a refetch triggered
    // by refreshKey keeps showing the previous profile until the new one is ready, rather
    // than flashing back to a loading state.
    getCustomerProfile(projectId)
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
      <h2>Customer Profile</h2>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'not-found' && <p>No canonical Customer Profile yet - run a successful Requirements Analysis first.</p>}
      {state.kind === 'error' && (
        <p className="error" role="alert">
          {state.message}
        </p>
      )}
      {state.kind === 'loaded' && <CustomerProfileContent artifact={state.artifact} />}
    </section>
  )
}

function CustomerProfileContent({ artifact }: { artifact: ArtifactVersionInfo<CustomerProfile> }) {
  const profile = artifact.content

  return (
    <div>
      <p className="artifact-meta">
        Version {artifact.versionNumber} · {new Date(artifact.createdAt).toLocaleString()}
      </p>

      {profile.business && (profile.business.name || profile.business.description || profile.business.languages?.length) && (
        <div className="profile-block">
          <h3>Business</h3>
          {profile.business.name && <p>{profile.business.name}</p>}
          {profile.business.description && <p>{profile.business.description}</p>}
          {profile.business.languages && profile.business.languages.length > 0 && (
            <p>Languages: {profile.business.languages.join(', ')}</p>
          )}
        </div>
      )}

      {profile.contact && (profile.contact.email || profile.contact.phone || profile.contact.website) && (
        <div className="profile-block">
          <h3>Contact</h3>
          <dl>
            {profile.contact.email && (
              <>
                <dt>Email</dt>
                <dd>{profile.contact.email}</dd>
              </>
            )}
            {profile.contact.phone && (
              <>
                <dt>Phone</dt>
                <dd>{profile.contact.phone}</dd>
              </>
            )}
            {profile.contact.website && (
              <>
                <dt>Website</dt>
                <dd>{profile.contact.website}</dd>
              </>
            )}
          </dl>
        </div>
      )}

      {profile.locations.length > 0 && (
        <div className="profile-block">
          <h3>Locations</h3>
          <ul>
            {profile.locations.map((location) => (
              <li key={location.localRef}>
                {[location.name, location.street, location.postalCode, location.city, location.countryCode]
                  .filter(Boolean)
                  .join(', ') || location.raw}
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.offerings.length > 0 && (
        <div className="profile-block">
          <h3>Offerings</h3>
          <ul>
            {profile.offerings.map((offering) => (
              <li key={offering.localRef}>
                {offering.name ?? offering.category ?? offering.localRef}
                {offering.description && ` - ${offering.description}`}
                {offering.price && ` (${offering.price.raw})`}
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.openingHours.length > 0 && (
        <div className="profile-block">
          <h3>Opening Hours</h3>
          <ul>
            {profile.openingHours.map((set) => (
              <li key={set.localRef}>
                {set.raw ?? scheduleSummary(set)}
                {set.closedDays && set.closedDays.length > 0 && ` (closed: ${set.closedDays.join(', ')})`}
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.socialLinks.length > 0 && (
        <div className="profile-block">
          <h3>Social Links</h3>
          <ul>
            {profile.socialLinks.map((link) => (
              <li key={link.url}>
                {link.platform}: {link.url}
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.providedClaims.length > 0 && (
        <div className="profile-block">
          <h3>Provided Claims</h3>
          <ul>
            {profile.providedClaims.map((claim) => (
              <li key={claim.claim}>{claim.claim}</li>
            ))}
          </ul>
        </div>
      )}

      {profile.unknowns.length > 0 && (
        <div className="profile-block">
          <h3>Unknowns</h3>
          <ul>
            {profile.unknowns.map((unknown, index) => (
              // backend content, wholesale-replaced per fetch - index is fine as a key here
              <li key={index}>
                <strong>{unknown.kind}</strong> ({unknown.field}): {unknown.description}
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.conflicts.length > 0 && (
        <div className="profile-block">
          <h3>Conflicts</h3>
          <ul>
            {profile.conflicts.map((conflict, index) => (
              // backend content, wholesale-replaced per fetch - index is fine as a key here
              <li key={index}>
                <strong>{conflict.field}</strong>: {conflict.description}
                <ul>
                  {conflict.statements.map((statement, statementIndex) => (
                    <li key={statementIndex}>{statement.value}</li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </div>
      )}

      {profile.provenance.length > 0 && (
        <div className="profile-block">
          <h3>Provenance</h3>
          <p className="provenance-note">Exactly as recorded by the agent - source refs are not resolved back to text here.</p>
          <ul>
            {profile.provenance.map((entry, index) => (
              // backend content, wholesale-replaced per fetch - index is fine as a key here
              <li key={index}>
                {entry.targetRef && <code>{entry.targetRef}</code>}
                {entry.field && ` ${entry.field}`} ← {entry.sourceRefs.join(', ')}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function scheduleSummary(set: CustomerProfile['openingHours'][number]): string {
  if (!set.schedule || set.schedule.length === 0) {
    return '(no schedule recorded)'
  }
  return set.schedule
    .map((entry) => `${entry.days.join('/')} ${entry.intervals.map((interval) => `${interval.from}-${interval.to}`).join(', ')}`)
    .join('; ')
}

export default CustomerProfileView
