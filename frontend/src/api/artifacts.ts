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
