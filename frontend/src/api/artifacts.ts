import { requestJson } from './http'

export interface Price {
  type: string
  amount?: number
  maxAmount?: number
  currency?: string
  raw: string
}

export interface Location {
  localRef: string
  name?: string
  street?: string
  postalCode?: string
  city?: string
  countryCode?: string
  raw?: string
}

export interface Offering {
  localRef: string
  name?: string
  description?: string
  category?: string
  price?: Price
}

export interface Interval {
  from: string
  to: string
}

export interface ScheduleEntry {
  days: string[]
  intervals: Interval[]
}

export interface OpeningHoursSet {
  localRef: string
  locationRefs?: string[]
  schedule?: ScheduleEntry[]
  closedDays?: string[]
  raw?: string
}

export interface SocialLink {
  platform: string
  url: string
  sourceRefs: string[]
}

export interface ProvidedClaim {
  claim: string
  sourceRefs: string[]
}

export interface UnknownItem {
  kind: 'missing' | 'ambiguous'
  field: string
  description: string
  sourceRefs?: string[]
}

export interface ConflictStatement {
  value: string
  sourceRefs: string[]
}

export interface ConflictItem {
  field: string
  description: string
  statements: ConflictStatement[]
}

export interface ProvenanceItem {
  targetRef?: string
  field?: string
  sourceRefs: string[]
}

export interface CustomerProfile {
  business?: { name?: string; description?: string; languages?: string[] }
  contact?: { email?: string; phone?: string; website?: string }
  locations: Location[]
  offerings: Offering[]
  openingHours: OpeningHoursSet[]
  socialLinks: SocialLink[]
  providedClaims: ProvidedClaim[]
  unknowns: UnknownItem[]
  conflicts: ConflictItem[]
  provenance: ProvenanceItem[]
}

export type Strength = 'must' | 'should' | 'could'

export interface Goal {
  localRef: string
  description: string
  strength: Strength
  sourceRefs: string[]
}

export interface Audience {
  localRef: string
  description: string
  sourceRefs: string[]
}

export interface ContentRequirement {
  localRef: string
  type: string
  customType?: string
  description: string
  strength: Strength
  sourceRefs: string[]
}

export interface FunctionalRequirement {
  localRef: string
  type: string
  customType?: string
  description: string
  strength: Strength
  sourceRefs: string[]
}

export interface Language {
  code: string
  strength: Strength
  sourceRefs: string[]
}

export interface Constraint {
  localRef: string
  category: string
  description: string
  strength: Strength
  sourceRefs: string[]
}

// Shaped differently from the Customer Profile's unknown/conflict (no "field" requirement,
// an "affects" list of localRefs instead) - kept as separate types rather than reusing
// UnknownItem/ConflictItem, which would misrepresent this artifact's actual schema.
export interface RequirementUnknown {
  kind: 'missing' | 'ambiguous'
  description: string
  field?: string
  affects?: string[]
  sourceRefs?: string[]
}

export interface RequirementConflictStatement {
  description: string
  sourceRefs: string[]
}

export interface RequirementConflict {
  description: string
  affects?: string[]
  statements: RequirementConflictStatement[]
}

export interface WebsiteRequirements {
  goals: Goal[]
  targetAudiences: Audience[]
  contentRequirements: ContentRequirement[]
  functionalRequirements: FunctionalRequirement[]
  languages: Language[]
  constraints: Constraint[]
  unknowns: RequirementUnknown[]
  conflicts: RequirementConflict[]
}

export interface ArtifactVersionInfo<T> {
  artifactId: string
  type: string
  versionNumber: number
  content: T
  createdAt: string
}

export function getCustomerProfile(projectId: string): Promise<ArtifactVersionInfo<CustomerProfile>> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/artifacts/customer-profile`)
}

export function getWebsiteRequirements(projectId: string): Promise<ArtifactVersionInfo<WebsiteRequirements>> {
  return requestJson(`/api/projects/${encodeURIComponent(projectId)}/artifacts/website-requirements`)
}
