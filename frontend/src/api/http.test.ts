import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, errorMessage, requestJson } from './http'

describe('requestJson', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('resolves with the parsed JSON body on a 2xx response', async () => {
    const response = new Response(JSON.stringify({ id: '1' }), { status: 200 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(requestJson('/api/projects')).resolves.toEqual({ id: '1' })
  })

  it('calls fetch with the path unchanged when no API base URL is configured (local dev default)', async () => {
    const response = new Response(JSON.stringify({}), { status: 200 })
    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('fetch', fetchMock)

    await requestJson('/api/projects')

    // AIW-71: requestJson prefixes every path with VITE_API_BASE_URL, empty by default so
    // local dev (Vite's own proxy) sees the exact same request it always has.
    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/projects')
  })

  it('attaches the shared X-API-Key header to every request (AIW-185)', async () => {
    const response = new Response(JSON.stringify({}), { status: 200 })
    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('fetch', fetchMock)

    await requestJson('/api/projects')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('X-API-Key')).toBe('architech-dev-api-key')
  })

  it('preserves a caller-supplied header (e.g. Content-Type) alongside the API key', async () => {
    const response = new Response(JSON.stringify({}), { status: 200 })
    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('fetch', fetchMock)

    await requestJson('/api/projects', { headers: { 'Content-Type': 'application/json' } })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-API-Key')).toBe('architech-dev-api-key')
  })

  it('throws an ApiError carrying the backend message on a non-2xx response', async () => {
    const response = new Response(JSON.stringify({ message: 'No project with id 123' }), { status: 404 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(requestJson('/api/projects/123')).rejects.toMatchObject({
      message: 'No project with id 123',
      status: 404,
    })
  })

  it('falls back to a generic message when the error body has no message field', async () => {
    const response = new Response('', { status: 500 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(requestJson('/api/projects')).rejects.toMatchObject({
      message: 'Request failed with status 500',
      status: 500,
    })
  })
})

describe('errorMessage', () => {
  it('returns an Error subclass message directly', () => {
    expect(errorMessage(new ApiError('No project with id 123', 404))).toBe('No project with id 123')
  })

  it('never leaks a non-Error value raw', () => {
    expect(errorMessage('a raw string, not an Error')).toBe('Something went wrong.')
    expect(errorMessage(undefined)).toBe('Something went wrong.')
  })
})
