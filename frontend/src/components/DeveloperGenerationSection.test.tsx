import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as websiteGenerationApi from '../api/websiteGeneration'
import { ApiError } from '../api/http'
import DeveloperGenerationSection from './DeveloperGenerationSection'

function generationResponse(
  overrides: Partial<websiteGenerationApi.WebsiteGenerationResponse> = {},
): websiteGenerationApi.WebsiteGenerationResponse {
  return {
    allSucceeded: true,
    siblings: [
      {
        proposalLocalRef: 'proposal-a',
        executionId: 'execution-1',
        status: 'SUCCEEDED',
        candidateId: 'candidate-1',
        infrastructureFailureMessage: null,
      },
      {
        proposalLocalRef: 'proposal-b',
        executionId: 'execution-2',
        status: 'SUCCEEDED',
        candidateId: 'candidate-2',
        infrastructureFailureMessage: null,
      },
      {
        proposalLocalRef: 'proposal-c',
        executionId: 'execution-3',
        status: 'SUCCEEDED',
        candidateId: 'candidate-3',
        infrastructureFailureMessage: null,
      },
    ],
    ...overrides,
  }
}

describe('DeveloperGenerationSection', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('generates a website and shows the per-sibling results on success', async () => {
    const user = userEvent.setup()
    const startMock = vi.spyOn(websiteGenerationApi, 'startWebsiteGeneration').mockResolvedValue(generationResponse())
    const onSucceeded = vi.fn()

    render(<DeveloperGenerationSection projectId="project-1" onSucceeded={onSucceeded} />)

    await user.click(screen.getByRole('button', { name: 'Website generieren' }))

    expect(startMock).toHaveBeenCalledWith('project-1')
    await waitFor(() => expect(screen.getByText('All succeeded')).toBeInTheDocument())
    expect(screen.getByText('proposal-a')).toBeInTheDocument()
    expect(screen.getByText('proposal-b')).toBeInTheDocument()
    expect(screen.getByText('proposal-c')).toBeInTheDocument()
    expect(onSucceeded).toHaveBeenCalledTimes(1)
  })

  it('shows a distinct message when a generation is already running (409)', async () => {
    const user = userEvent.setup()
    vi.spyOn(websiteGenerationApi, 'startWebsiteGeneration').mockRejectedValue(
      new ApiError('A website generation is already running for this project', 409),
    )

    render(<DeveloperGenerationSection projectId="project-1" />)

    await user.click(screen.getByRole('button', { name: 'Website generieren' }))

    expect(await screen.findByText('A website generation is already running for this project')).toBeInTheDocument()
  })

  it('shows a generic error state on an unexpected failure', async () => {
    const user = userEvent.setup()
    vi.spyOn(websiteGenerationApi, 'startWebsiteGeneration').mockRejectedValue(new Error('Backend is unreachable'))

    render(<DeveloperGenerationSection projectId="project-1" />)

    await user.click(screen.getByRole('button', { name: 'Website generieren' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
  })

  it('prevents a double-click from starting two concurrent generations', async () => {
    const user = userEvent.setup()
    let resolveGeneration: (result: websiteGenerationApi.WebsiteGenerationResponse) => void = () => {}
    const startMock = vi.spyOn(websiteGenerationApi, 'startWebsiteGeneration').mockReturnValue(
      new Promise((resolve) => {
        resolveGeneration = resolve
      }),
    )

    render(<DeveloperGenerationSection projectId="project-1" />)
    const generateButton = screen.getByRole('button', { name: 'Website generieren' })

    await user.dblClick(generateButton)

    expect(startMock).toHaveBeenCalledTimes(1)
    resolveGeneration(generationResponse())
  })

  it('reports partial failure without claiming all succeeded', async () => {
    const user = userEvent.setup()
    vi.spyOn(websiteGenerationApi, 'startWebsiteGeneration').mockResolvedValue(
      generationResponse({
        allSucceeded: false,
        siblings: [
          {
            proposalLocalRef: 'proposal-a',
            executionId: null,
            status: 'INFRASTRUCTURE_FAILURE',
            candidateId: null,
            infrastructureFailureMessage: 'workspace provisioning failed',
          },
        ],
      }),
    )

    render(<DeveloperGenerationSection projectId="project-1" />)

    await user.click(screen.getByRole('button', { name: 'Website generieren' }))

    expect(await screen.findByText('Not all succeeded')).toBeInTheDocument()
    expect(screen.getByText('workspace provisioning failed')).toBeInTheDocument()
  })
})
