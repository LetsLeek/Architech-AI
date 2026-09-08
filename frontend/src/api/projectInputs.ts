import { requestJson } from './http'

export interface FreeTextInput {
  id: string
  projectId: string
  content: string
  createdAt: string
}

export interface StructuredInput {
  id: string
  projectId: string
  fields: Record<string, string>
  createdAt: string
}

export interface FileInput {
  id: string
  projectId: string
  filename: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

function inputsUrl(projectId: string, segment: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/${segment}`
}

export function listFreeTextInputs(projectId: string): Promise<FreeTextInput[]> {
  return requestJson(inputsUrl(projectId, 'inputs'))
}

// content is submitted exactly as typed - never trimmed, paraphrased, or otherwise rewritten,
// so validation.SourceContext later cites exactly what the customer actually said (AIW-54 AC).
export function submitFreeTextInput(projectId: string, content: string): Promise<FreeTextInput> {
  return requestJson(inputsUrl(projectId, 'inputs'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  })
}

export function listStructuredInputs(projectId: string): Promise<StructuredInput[]> {
  return requestJson(inputsUrl(projectId, 'structured-inputs'))
}

export function submitStructuredInput(projectId: string, fields: Record<string, string>): Promise<StructuredInput> {
  return requestJson(inputsUrl(projectId, 'structured-inputs'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields }),
  })
}

export function listFileInputs(projectId: string): Promise<FileInput[]> {
  return requestJson(inputsUrl(projectId, 'files'))
}

export function uploadFileInput(projectId: string, file: File): Promise<FileInput> {
  const formData = new FormData()
  formData.append('file', file)
  // No Content-Type header here - the browser sets the multipart boundary itself.
  return requestJson(inputsUrl(projectId, 'files'), {
    method: 'POST',
    body: formData,
  })
}
