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

/** Throws {@link ApiError} for a non-2xx response; otherwise parses the body as JSON. */
export async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new ApiError(await errorMessageOf(response), response.status)
  }
  return response.json() as Promise<T>
}

/** Renders any caught error as a user-facing message, without leaking non-Error values raw. */
export function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong.'
}
