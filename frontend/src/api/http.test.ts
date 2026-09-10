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
