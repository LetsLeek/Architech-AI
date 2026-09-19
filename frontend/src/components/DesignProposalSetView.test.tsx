import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as artifactsApi from '../api/artifacts'
import { ApiError } from '../api/http'
import DesignProposalSetView from './DesignProposalSetView'

function proposal(overrides: Partial<artifactsApi.DesignProposal> = {}): artifactsApi.DesignProposal {
  return {
    localRef: 'proposal-a',
    name: 'Coastal Calm',
    concept: 'A relaxed, airy concept built around natural light and open space.',
    websitePlan: {
      pages: [
        {
          localRef: 'page-home',
          name: 'Home',
          route: '/',
          purpose: 'Primary landing page',
          sections: [{ localRef: 'section-hero', kind: 'hero', purpose: 'Introduce the business', layoutIntent: 'full-bleed', elements: [
            { localRef: 'element-heading', kind: 'heading', role: 'primary-heading', contentIntent: 'Business name and tagline' },
          ] }],
        },
      ],
    },
    designSpecification: {
      colors: [{ role: 'primary', value: '#2f4f2f' }],
      typography: [{ role: 'heading', fontFamily: 'Georgia', fontWeight: 700, fontSizeRem: 2.5, lineHeight: 1.2 }],
      spacing: [{ role: 'section-gap', valueRem: 4 }],
      layout: { contentWidth: 'standard', density: 'balanced', pageGutterRem: 1.5, sectionGapRem: 4, gridIntent: '12-column' },
      uiPatterns: [],
      imagery: { direction: 'Natural, sunlit photography', treatment: 'Warm, minimally edited' },
      responsive: {
        navigationBehavior: 'Collapses to a hamburger menu below 768px',
        contentStacking: 'Single column below tablet width',
        typeScaling: 'Headings scale down 20% on mobile',
        spacingAdjustment: 'Section gaps shrink by half on mobile',
        mediaBehavior: 'Images crop to maintain aspect ratio',
      },
    },
    ...overrides,
  }
}

function designProposalSet(): artifactsApi.ArtifactVersionInfo<artifactsApi.DesignProposalSet> {
  return {
    artifactId: 'artifact-1',
    type: 'design-proposal-set',
    versionNumber: 1,
    createdAt: '2026-09-20T00:00:00Z',
    content: {
      proposals: [
        proposal({ localRef: 'proposal-a', name: 'Coastal Calm' }),
        proposal({ localRef: 'proposal-b', name: 'Bold Marketplace' }),
        proposal({ localRef: 'proposal-c', name: 'Quiet Studio' }),
      ],
    },
  }
}

describe('DesignProposalSetView', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows a loading state before the fetch resolves', () => {
    vi.spyOn(artifactsApi, 'getDesignProposalSet').mockReturnValue(new Promise(() => {}))

    render(<DesignProposalSetView projectId="project-1" />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows a not-generated state when no canonical design proposal set exists yet', async () => {
    vi.spyOn(artifactsApi, 'getDesignProposalSet').mockRejectedValue(new ApiError('not found', 404))

    render(<DesignProposalSetView projectId="project-1" />)

    expect(await screen.findByText(/No canonical Design Proposal Set yet/)).toBeInTheDocument()
  })

  it('shows an explicit error state when the artifact cannot be loaded', async () => {
    vi.spyOn(artifactsApi, 'getDesignProposalSet').mockRejectedValue(new Error('Backend is unreachable'))

    render(<DesignProposalSetView projectId="project-1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
  })

  it('renders exactly three proposals with comparable structural and visual information', async () => {
    vi.spyOn(artifactsApi, 'getDesignProposalSet').mockResolvedValue(designProposalSet())

    render(<DesignProposalSetView projectId="project-1" />)

    expect(await screen.findByText('Coastal Calm')).toBeInTheDocument()
    expect(screen.getByText('Bold Marketplace')).toBeInTheDocument()
    expect(screen.getByText('Quiet Studio')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(3)

    // Structural/visual info is present without needing raw JSON.
    expect(screen.getAllByText(/primary: #2f4f2f/).length).toBe(3)
    expect(screen.getAllByText(/Natural, sunlit photography/).length).toBe(3)
    expect(screen.getAllByText(/Collapses to a hamburger menu below 768px/).length).toBe(3)
  })

  it('explicitly states no proposal is ranked, and never labels one as best or a winner', async () => {
    vi.spyOn(artifactsApi, 'getDesignProposalSet').mockResolvedValue(designProposalSet())

    render(<DesignProposalSetView projectId="project-1" />)
    await screen.findByText('Coastal Calm')

    expect(screen.getByText(/None is recommended or ranked above the others/)).toBeInTheDocument()
    const body = document.body.textContent ?? ''
    expect(body).not.toMatch(/best choice/i)
    expect(body).not.toMatch(/winner/i)
  })
})
