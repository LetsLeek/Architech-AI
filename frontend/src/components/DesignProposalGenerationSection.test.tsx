import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as designProposalsApi from '../api/designProposals'
import { ApiError } from '../api/http'
import DesignProposalGenerationSection from './DesignProposalGenerationSection'

function readiness(overrides: Partial<designProposalsApi.DesignerReadiness> = {}): designProposalsApi.DesignerReadiness {
  return {
    customerProfileExists: true,
    websiteRequirementsExists: true,
    ready: true,
    running: false,
    latestExecutionId: null,
    latestExecutionStatus: null,
    latestExecutionFailureReason: null,
    designProposalSetExists: false,
    designProposalSetArtifactId: null,
    designProposalSetVersionNumber: null,
    ...overrides,
  }
}

function generationResult(
  overrides: Partial<designProposalsApi.DesignProposalGenerationResult> = {},
): designProposalsApi.DesignProposalGenerationResult {
  return {
    executionId: 'execution-1',
    status: 'SUCCEEDED',
    succeeded: true,
    validationIssues: [],
    provider: 'anthropic',
    model: 'claude',
    promptTokens: 100,
    completionTokens: 50,
    costUsd: 0.01,
    ...overrides,
  }
}

describe('DesignProposalGenerationSection', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('disables the generate button and explains why when Designer readiness is not ready', async () => {
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(readiness({ ready: false, websiteRequirementsExists: false }))

    render(<DesignProposalGenerationSection projectId="project-1" />)

    expect(await screen.findByRole('button', { name: '3 Designs generieren' })).toBeDisabled()
    expect(screen.getByText(/Requires a confirmed customer profile and website requirements/)).toBeInTheDocument()
  })

  it('disables the generate button while a Designer execution is already running', async () => {
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(readiness({ running: true }))

    render(<DesignProposalGenerationSection projectId="project-1" />)

    expect(await screen.findByRole('button', { name: '3 Designs generieren' })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('A design proposal generation is already running…')
  })

  it('generates designs and reflects the new canonical design proposal set on success', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness')
      .mockResolvedValueOnce(readiness())
      .mockResolvedValueOnce(readiness({ designProposalSetExists: true, designProposalSetArtifactId: 'artifact-1', designProposalSetVersionNumber: 1 }))
    const startMock = vi.spyOn(designProposalsApi, 'startDesignProposalGeneration').mockResolvedValue(generationResult())

    render(<DesignProposalGenerationSection projectId="project-1" />)

    const generateButton = await screen.findByRole('button', { name: '3 Designs generieren' })
    await user.click(generateButton)

    expect(startMock).toHaveBeenCalledWith('project-1')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Designs ansehen' })).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Designs neu generieren' })).toBeInTheDocument()
    expect(screen.getByText('SUCCEEDED')).toBeInTheDocument()
  })

  it('shows a runtime-failure state and never claims canonical designs were produced', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(readiness())
    vi.spyOn(designProposalsApi, 'startDesignProposalGeneration').mockRejectedValue(
      new ApiError('the model/runtime failed on every permitted attempt', 502),
    )

    render(<DesignProposalGenerationSection projectId="project-1" />)

    await user.click(await screen.findByRole('button', { name: '3 Designs generieren' }))

    expect(await screen.findByText('the model/runtime failed on every permitted attempt')).toBeInTheDocument()
    expect(screen.getByText('No canonical design proposal set was produced.')).toBeInTheDocument()
    // Never silently promoted to the "view/regenerate" state on a failed run.
    expect(screen.queryByRole('button', { name: 'Designs ansehen' })).not.toBeInTheDocument()
  })

  it('shows a generic error state without a canonical output claim on an unexpected failure', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(readiness())
    vi.spyOn(designProposalsApi, 'startDesignProposalGeneration').mockRejectedValue(new Error('Backend is unreachable'))

    render(<DesignProposalGenerationSection projectId="project-1" />)

    await user.click(await screen.findByRole('button', { name: '3 Designs generieren' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
  })

  it('prevents a double-click from starting two concurrent generations', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(readiness())
    let resolveGeneration: (result: designProposalsApi.DesignProposalGenerationResult) => void = () => {}
    const startMock = vi.spyOn(designProposalsApi, 'startDesignProposalGeneration').mockReturnValue(
      new Promise((resolve) => {
        resolveGeneration = resolve
      }),
    )

    render(<DesignProposalGenerationSection projectId="project-1" />)
    const generateButton = await screen.findByRole('button', { name: '3 Designs generieren' })

    await user.dblClick(generateButton)

    expect(startMock).toHaveBeenCalledTimes(1)
    resolveGeneration(generationResult())
  })

  it('replaces the primary action with view/regenerate when a canonical design proposal set already exists', async () => {
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(
      readiness({ designProposalSetExists: true, designProposalSetArtifactId: 'artifact-1', designProposalSetVersionNumber: 3 }),
    )

    render(<DesignProposalGenerationSection projectId="project-1" />)

    expect(await screen.findByRole('button', { name: 'Designs ansehen' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Designs neu generieren' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '3 Designs generieren' })).not.toBeInTheDocument()
  })

  it('requires confirmation before regenerating and never calls the API on cancel', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(
      readiness({ designProposalSetExists: true, designProposalSetArtifactId: 'artifact-1', designProposalSetVersionNumber: 3 }),
    )
    const startMock = vi.spyOn(designProposalsApi, 'startDesignProposalGeneration')

    render(<DesignProposalGenerationSection projectId="project-1" />)

    await user.click(await screen.findByRole('button', { name: 'Designs neu generieren' }))
    expect(screen.getByText(/starts a new, paid AI execution/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(startMock).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Designs neu generieren' })).toBeInTheDocument()
  })

  it('starts a new generation once regeneration is confirmed', async () => {
    const user = userEvent.setup()
    vi.spyOn(designProposalsApi, 'getDesignerReadiness').mockResolvedValue(
      readiness({ designProposalSetExists: true, designProposalSetArtifactId: 'artifact-1', designProposalSetVersionNumber: 3 }),
    )
    const startMock = vi.spyOn(designProposalsApi, 'startDesignProposalGeneration').mockResolvedValue(generationResult())

    render(<DesignProposalGenerationSection projectId="project-1" />)

    await user.click(await screen.findByRole('button', { name: 'Designs neu generieren' }))
    await user.click(screen.getByRole('button', { name: 'Confirm regeneration' }))

    await waitFor(() => expect(startMock).toHaveBeenCalledWith('project-1'))
  })
})
