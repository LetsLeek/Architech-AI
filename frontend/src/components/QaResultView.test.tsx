import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as qaResultsApi from '../api/qaResults'
import { ApiError } from '../api/http'
import QaResultView from './QaResultView'

function qaResult(overrides: Partial<qaResultsApi.QaResultResponse> = {}): qaResultsApi.QaResultResponse {
  return {
    qaResultId: 'qa-result-1',
    qaExecutionId: 'qa-execution-1',
    testedCandidateId: 'candidate-1',
    qaProfileRef: 'standard-qa-profile',
    evaluationState: 'COMPLETED',
    gateOutcome: 'PASS',
    holdReasons: [],
    findingCount: 2,
    authorityIssueCount: 0,
    evaluationIssueCount: 1,
    createdAt: '2026-09-25T00:00:00Z',
    ...overrides,
  }
}

describe('QaResultView', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows a loading state before the fetch resolves', () => {
    vi.spyOn(qaResultsApi, 'getLatestQaResult').mockReturnValue(new Promise(() => {}))

    render(<QaResultView projectId="project-1" />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows a not-found state when no QA result exists yet', async () => {
    vi.spyOn(qaResultsApi, 'getLatestQaResult').mockRejectedValue(new ApiError('not found', 404))

    render(<QaResultView projectId="project-1" />)

    expect(await screen.findByText('No QA result yet - generate a website first.')).toBeInTheDocument()
  })

  it('shows an explicit error state when the QA result cannot be loaded', async () => {
    vi.spyOn(qaResultsApi, 'getLatestQaResult').mockRejectedValue(new Error('Backend is unreachable'))

    render(<QaResultView projectId="project-1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
  })

  it('renders the gate outcome, evaluation state, counts, and hold reasons', async () => {
    vi.spyOn(qaResultsApi, 'getLatestQaResult').mockResolvedValue(
      qaResult({ gateOutcome: 'HOLD', holdReasons: ['missing accessibility labels'] }),
    )

    render(<QaResultView projectId="project-1" />)

    expect(await screen.findByText('HOLD')).toBeInTheDocument()
    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('standard-qa-profile')).toBeInTheDocument()
    expect(screen.getByText('missing accessibility labels')).toBeInTheDocument()
  })

  it('re-fetches when refreshKey changes', async () => {
    const getMock = vi.spyOn(qaResultsApi, 'getLatestQaResult').mockResolvedValue(qaResult())

    const { rerender } = render(<QaResultView projectId="project-1" refreshKey={0} />)
    await screen.findByText('PASS')
    expect(getMock).toHaveBeenCalledTimes(1)

    rerender(<QaResultView projectId="project-1" refreshKey={1} />)
    await vi.waitFor(() => expect(getMock).toHaveBeenCalledTimes(2))
  })
})
