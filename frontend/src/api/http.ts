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

/**
 * Empty by default - every call site passes a same-origin path (e.g. `/api/projects`), and Vite's
 * own dev-server proxy (see vite.config.ts) keeps local dev working against that unchanged. Only
 * a real deployed environment (AIW-71), where the frontend and backend are served from different
 * origins (a Static Web App and a Container App, on different domains), needs this set - to that
 * environment's own real backend URL, injected at build time, never hardcoded here.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * Matches the backend's own `architech.security.api-key` default (AIW-185) - local dev/tests
 * work with zero setup; every real DEV/STAGING/PROD build injects its own real value at build
 * time via `VITE_API_KEY`, sourced from the same Terraform-generated secret the backend reads
 * from Key Vault. Known limitation, documented not hidden: this ships inside the public,
 * statically-hosted frontend bundle, so it stops casual/automated access to the open internet,
 * not a determined visitor who already has the real page open in a browser - see
 * ApiKeyAuthenticationFilter's own class docs on the backend for the full scope decision.
 */
const API_KEY = import.meta.env.VITE_API_KEY ?? 'architech-dev-api-key'

/** Throws {@link ApiError} for a non-2xx response; otherwise parses the body as JSON. */
export async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('X-API-Key', API_KEY)
  const response = await fetch(`${API_BASE_URL}${input}`, { ...init, headers })
  if (!response.ok) {
    throw new ApiError(await errorMessageOf(response), response.status)
  }
  return response.json() as Promise<T>
}

/** Renders any caught error as a user-facing message, without leaking non-Error values raw. */
export function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong.'
}
