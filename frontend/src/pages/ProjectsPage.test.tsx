import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as projectsApi from '../api/projects'
import ProjectsPage from './ProjectsPage'

const navigateMock = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigateMock }
})

function renderPage() {
  return render(
    <MemoryRouter>
      <ProjectsPage />
    </MemoryRouter>,
  )
}

describe('ProjectsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    navigateMock.mockReset()
  })

  it('navigates to the new project on a successful create', async () => {
    const user = userEvent.setup()
    vi.spyOn(projectsApi, 'createProject').mockResolvedValue({
      id: 'project-1',
      projectType: 'website',
      createdAt: '2026-09-10T00:00:00Z',
      updatedAt: '2026-09-10T00:00:00Z',
    })
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Create Website Project' }))

    expect(screen.getByRole('button', { name: 'Creating…' })).toBeDisabled()
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/projects/project-1'))
  })

  it('shows an error and re-enables the button when creation fails', async () => {
    const user = userEvent.setup()
    vi.spyOn(projectsApi, 'createProject').mockRejectedValue(new Error('Backend is unreachable'))
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Create Website Project' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend is unreachable')
    expect(screen.getByRole('button', { name: 'Create Website Project' })).not.toBeDisabled()
    expect(navigateMock).not.toHaveBeenCalled()
  })
})
