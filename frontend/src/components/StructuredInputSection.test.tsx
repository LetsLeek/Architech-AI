import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as projectInputsApi from '../api/projectInputs'
import StructuredInputSection from './StructuredInputSection'

function structuredInput(fields: Record<string, string>, id = 'input-1'): projectInputsApi.StructuredInput {
  return { id, projectId: 'project-1', fields, createdAt: '2026-01-01T00:00:00Z' }
}

describe('StructuredInputSection', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('starts with the save button disabled until a predefined field is filled in', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([])
    vi.spyOn(projectInputsApi, 'submitStructuredInput').mockResolvedValue(structuredInput({}))
    const user = userEvent.setup()

    render(<StructuredInputSection projectId="project-1" />)

    const saveButton = await screen.findByRole('button', { name: 'Save business details' })
    expect(saveButton).toBeDisabled()

    await user.type(screen.getByLabelText('Business name'), 'Müller Kfz-Werkstatt')
    expect(saveButton).toBeEnabled()
  })

  it('submits the predefined fields under human-readable labels', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([])
    const submitMock = vi
      .spyOn(projectInputsApi, 'submitStructuredInput')
      .mockResolvedValue(structuredInput({ 'Business name': 'Müller Kfz-Werkstatt' }))
    const user = userEvent.setup()

    render(<StructuredInputSection projectId="project-1" />)

    await user.type(await screen.findByLabelText('Business name'), 'Müller Kfz-Werkstatt')
    await user.type(screen.getByLabelText('Phone'), '+43 1 2345678')
    await user.click(screen.getByRole('button', { name: 'Save business details' }))

    await waitFor(() => expect(submitMock).toHaveBeenCalledTimes(1))
    expect(submitMock).toHaveBeenCalledWith('project-1', {
      'Business name': 'Müller Kfz-Werkstatt',
      Phone: '+43 1 2345678',
    })
    expect(await screen.findByText('Saved.')).toBeInTheDocument()
  })

  it('only includes an opening-hours day once it is checked, formatted as a time range', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([])
    const submitMock = vi.spyOn(projectInputsApi, 'submitStructuredInput').mockResolvedValue(structuredInput({}))
    const user = userEvent.setup()

    render(<StructuredInputSection projectId="project-1" />)

    expect(screen.queryByDisplayValue('09:00')).not.toBeInTheDocument()

    await user.click(await screen.findByRole('checkbox', { name: 'Monday' }))
    expect(screen.getByDisplayValue('09:00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Save business details' }))

    await waitFor(() => expect(submitMock).toHaveBeenCalledTimes(1))
    expect(submitMock).toHaveBeenCalledWith('project-1', { 'Opening hours - Monday': '09:00–18:00' })
  })

  it('only submits a service/offering row once it has a name', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([])
    const submitMock = vi.spyOn(projectInputsApi, 'submitStructuredInput').mockResolvedValue(structuredInput({}))
    const user = userEvent.setup()

    render(<StructuredInputSection projectId="project-1" />)

    await user.type(await screen.findByPlaceholderText('Service or product name'), 'Brake service')
    await user.type(screen.getByPlaceholderText('Price (optional)'), '€49')
    await user.click(screen.getByRole('button', { name: 'Save business details' }))

    await waitFor(() => expect(submitMock).toHaveBeenCalledTimes(1))
    expect(submitMock).toHaveBeenCalledWith('project-1', { 'Service/offering 1': 'Brake service (€49)' })
  })

  it('keeps a custom field escape hatch for anything the predefined fields do not cover', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([])
    const submitMock = vi.spyOn(projectInputsApi, 'submitStructuredInput').mockResolvedValue(structuredInput({}))
    const user = userEvent.setup()

    render(<StructuredInputSection projectId="project-1" />)

    await user.click(await screen.findByRole('button', { name: 'Add a custom detail' }))
    await user.type(screen.getByPlaceholderText('What is this about? (e.g. Parking)'), 'Parking')
    await user.type(screen.getByPlaceholderText('Details'), 'Free customer parking behind the building')
    await user.click(screen.getByRole('button', { name: 'Save business details' }))

    await waitFor(() => expect(submitMock).toHaveBeenCalledTimes(1))
    expect(submitMock).toHaveBeenCalledWith('project-1', { Parking: 'Free customer parking behind the building' })
  })

  it('shows how many submissions have already been saved for this project, collapsed by default', async () => {
    vi.spyOn(projectInputsApi, 'listStructuredInputs').mockResolvedValue([structuredInput({ 'Business name': 'Acme' })])
    vi.spyOn(projectInputsApi, 'submitStructuredInput').mockResolvedValue(structuredInput({}))

    render(<StructuredInputSection projectId="project-1" />)

    const summary = await screen.findByText('1 submission saved so far')
    expect(within(summary.closest('details')!).queryByText(/Business name: Acme/)).not.toBeVisible()
  })
})
