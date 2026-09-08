export interface Project {
  id: string
  projectType: string
  createdAt: string
  updatedAt: string
}

/** Thrown for a non-2xx API response, carrying the backend's own error message when it has one. */
export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function errorMessageOf(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof (body as { message: unknown }).message === 'string' &&
      (body as { message: string }).message.length > 0
    ) {
      return (body as { message: string }).message
    }
  } catch {
    // response body wasn't JSON (or was empty) - fall through to the generic message below
  }
  return `Request failed with status ${response.status}`
}

export async function createProject(projectType: string): Promise<Project> {
  const response = await fetch('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectType }),
  })
  if (!response.ok) {
    throw new ApiError(await errorMessageOf(response), response.status)
  }
  return response.json() as Promise<Project>
}

export async function getProject(id: string): Promise<Project> {
  const response = await fetch(`/api/projects/${encodeURIComponent(id)}`)
  if (!response.ok) {
    throw new ApiError(await errorMessageOf(response), response.status)
  }
  return response.json() as Promise<Project>
}
