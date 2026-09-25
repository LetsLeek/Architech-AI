import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as documentationApi from '../api/documentation'
import DocumentationView from './DocumentationView'

function documentationLine(overrides: Partial<documentationApi.DocumentationLineResponse> = {}): documentationApi.DocumentationLineResponse {
  return {
    documentationLineId: 'line-1',
    profileRef: 'TECHNICAL_HANDOVER',
    locale: 'en',
    currentRevision: 1,
    currentPackageVersion: {
      packageVersionId: 'version-1',
      revision: 1,
      audience: 'developer',
      policyRef: 'standard-policy',
      semanticArtifactCount: 3,
      deterministicReportCount: 2,
      generationReasons: ['QA gate PASS'],
      createdAt: '2026-09-25T00:00:00Z',
    },
    ...overrides,
  }
}

describe('DocumentationView', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows a loading state before the fetch resolves', () => {
    vi.spyOn(documentationApi, 'getDocumentationLines').mockReturnValue(new Promise(() => {}))

    render(<DocumentationView projectId="project-1" />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows an empty state - not an error or not-found - when no documentation exists yet', async () => {
    vi.spyOn(documentationApi, 'getDocumentationLines').mockResolvedValue([])

    render(<DocumentationView projectId="project-1" />)

    expect(await screen.findByText('No documentation generated yet.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows an explicit error state when documentation cannot be loaded', async () => {
    vi.spyOn(documentationApi, 'getDocumentationLines').mockRejectedValue(new Error('Backend is unreachable'))

    render(<DocumentationView projectId="project-1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
  })

  it('renders each documentation line with its profile, locale, revision, audience, and counts', async () => {
    vi.spyOn(documentationApi, 'getDocumentationLines').mockResolvedValue([
      documentationLine({ documentationLineId: 'line-1', profileRef: 'TECHNICAL_HANDOVER', locale: 'en' }),
      documentationLine({ documentationLineId: 'line-2', profileRef: 'CUSTOMER_HANDOVER', locale: 'en' }),
    ])

    render(<DocumentationView projectId="project-1" />)

    expect(await screen.findByText('TECHNICAL_HANDOVER', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('CUSTOMER_HANDOVER', { exact: false })).toBeInTheDocument()
    expect(screen.getAllByText('Audience: developer').length).toBe(2)
    expect(screen.getAllByText('Semantic artifacts: 3').length).toBe(2)
  })

  it('re-fetches when refreshKey changes', async () => {
    const getMock = vi.spyOn(documentationApi, 'getDocumentationLines').mockResolvedValue([])

    const { rerender } = render(<DocumentationView projectId="project-1" refreshKey={0} />)
    await screen.findByText('No documentation generated yet.')
    expect(getMock).toHaveBeenCalledTimes(1)

    rerender(<DocumentationView projectId="project-1" refreshKey={1} />)
    await vi.waitFor(() => expect(getMock).toHaveBeenCalledTimes(2))
  })
})
