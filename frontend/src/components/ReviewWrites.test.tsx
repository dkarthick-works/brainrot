import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import type { Card } from '../types'
import { ReviewWritesNotice, ReviewWritesProvider, useReviewWrites } from './ReviewWrites'
import { SaveErrorBanner } from './SaveError'

vi.mock('../api', () => ({ api: { review: vi.fn() } }))
const card = { id: 'a', question: 'Question A', dueAt: '2030-01-01' } as Card
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
let writes: ReturnType<typeof useReviewWrites>
function Probe() { writes = useReviewWrites(); return null }
function setup() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(qc, 'invalidateQueries')
  const view = render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/freestyle']}>
    <ReviewWritesProvider><Probe /><ReviewWritesNotice /><SaveErrorBanner /></ReviewWritesProvider>
  </MemoryRouter></QueryClientProvider>)
  return { ...view, invalidate }
}
beforeEach(() => vi.clearAllMocks())
afterEach(() => { cleanup(); vi.useRealTimers() })

describe('persistent scheduled-review coordinator', () => {
  it('owns immutable submissions and invalidates cards/topics on success', async () => {
    const request = deferred<Card>()
    vi.mocked(api.review).mockReturnValue(request.promise)
    const { invalidate } = setup()
    let id: string | null = null
    await act(async () => { id = writes.submit('session', card, 4) })
    expect(writes.pending).toBe(1)
    expect(writes.submissions[0]).toMatchObject({ id, sessionId: 'session', quality: 4, attemptId: 1, status: 'pending' })
    expect(writes.submissions[0].card).not.toBe(card)
    await act(async () => { request.resolve(card) })
    expect(writes.pending).toBe(0)
    expect(writes.submissions[0].status).toBe('succeeded')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['cards'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['topics'] })
  })

  it('keeps off-route failures actionable after the transient banner expires; retries stored quality', async () => {
    vi.useFakeTimers()
    vi.mocked(api.review).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(card)
    setup()
    await act(async () => { writes.submit('old-home', card, 3) })
    expect(screen.queryByRole('button', { name: 'Back to home' })).toBeNull()
    expect(screen.getByRole('alert').textContent).toContain("A scheduled review couldn't be saved.")
    expect(screen.queryByRole('status')).toBeNull() // no duplicate notice
    act(() => { vi.advanceTimersByTime(8000) })
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByRole('button', { name: 'Back to home' })).toBeTruthy()
    expect(writes.failures).toHaveLength(1)
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry scheduled review: Question A' })) })
    expect(api.review).toHaveBeenNthCalledWith(2, 'a', 3)
    expect(writes.submissions[0]).toMatchObject({ sessionId: 'old-home', attemptId: 2, status: 'succeeded' })
    expect(writes.failures).toHaveLength(0)
  })

  it('guards repeated retries and supersedes stale retry closures after a replacement succeeds', async () => {
    const replacement = deferred<Card>()
    vi.mocked(api.review).mockRejectedValueOnce(new Error('failed')).mockReturnValue(replacement.promise)
    setup()
    let failed!: string
    await act(async () => { failed = writes.submit('old', card, 1)! })
    const oldRetry = writes.retry
    await act(async () => {
      writes.submit('new', card, 5)
      oldRetry(failed)
      expect(writes.submit('new', card, 4)).toBeNull()
    })
    expect(api.review).toHaveBeenCalledTimes(2)
    await act(async () => { replacement.resolve(card) })
    expect(writes.submissions[0].status).toBe('superseded')
    await act(async () => { oldRetry(failed) })
    expect(api.review).toHaveBeenCalledTimes(2)
    expect(writes.failures).toHaveLength(0)
  })

  it('retries only explicitly and permits just one pending attempt per card', async () => {
    const retry = deferred<Card>()
    vi.mocked(api.review).mockRejectedValueOnce(new Error('failure')).mockReturnValue(retry.promise)
    setup()
    let id!: string
    await act(async () => { id = writes.submit('home', card, 4)! })
    expect(api.review).toHaveBeenCalledTimes(1)
    await act(async () => { writes.retry(id); writes.retry(id) })
    expect(api.review).toHaveBeenCalledTimes(2)
    expect(writes.pending).toBe(1)
    await act(async () => { retry.reject(new Error('still offline')) })
    expect(writes.failures[0].attemptId).toBe(2)
  })

  it('does not invalidate or expose settlements/retries after authenticated-shell unmount', async () => {
    const request = deferred<Card>()
    vi.mocked(api.review).mockReturnValue(request.promise)
    const { unmount, invalidate } = setup()
    await act(async () => { writes.submit('home', card, 4) })
    unmount()
    await act(async () => { request.resolve(card) })
    expect(invalidate).not.toHaveBeenCalled()
  })
})
