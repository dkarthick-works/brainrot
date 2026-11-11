import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import type { Card } from '../types'
import { ReviewWritesNotice, ReviewWritesProvider, useReviewWrites } from '../components/ReviewWrites'
import Play from './Play'
import { SaveErrorBanner } from '../components/SaveError'

vi.mock('../api', () => {
  const api = { due: vi.fn(), review: vi.fn(), topics: vi.fn() }
  return { ApiError: class ApiError extends Error {}, api, topicsQuery: { queryKey: ['topics'], queryFn: () => api.topics() } }
})
const card = (id: string): Card => ({ id, topicId: 't', question: `Question ${id}`, answer: `Answer ${id}`,
  easeFactor: 2.5, interval: 1, repetitions: 1, dueAt: '2020-01-01', lastReviewedAt: null, createdAt: '', updatedAt: '' })
const a = card('a'), b = card('b')
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
let writes: ReturnType<typeof useReviewWrites>
function Nav() {
  const navigate = useNavigate()
  writes = useReviewWrites()
  return <><button onClick={() => navigate('/freestyle')}>Leave Home</button><button onClick={() => navigate('/')}>Return Home</button></>
}
function setup(strict = false) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  const tree = <QueryClientProvider client={qc}><MemoryRouter><ReviewWritesProvider>
    <Nav /><ReviewWritesNotice /><SaveErrorBanner /><Routes><Route path="/" element={<Play menuOpen={false} />} />
      <Route path="/freestyle" element={<p>Freestyle route</p>} /></Routes>
  </ReviewWritesProvider></MemoryRouter></QueryClientProvider>
  return { ...render(strict ? <StrictMode>{tree}</StrictMode> : tree), qc }
}
const key = (key: string) => fireEvent.keyDown(document.body, { key })
async function grade(qualityKey = '3') {
  key('Enter'); key(qualityKey); key('ArrowRight')
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 280)) })
}
async function loaded() { await screen.findByRole('button', { name: 'Tap to reveal answer' }) }
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.topics).mockResolvedValue([{ id: 't', name: 'Topic', isDefault: true, cardCount: 2, dueCount: 1 }])
  vi.mocked(api.due).mockResolvedValue({ cards: [], dueCount: 0, nextDueAt: null })
})
afterEach(cleanup)

describe('Home scheduled-review navigation safety', () => {
  it('shows only the answer text on the back of a study card', async () => {
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    const { container } = setup()
    await loaded()
    key('Enter')
    expect(container.querySelector('.front .card-content')?.textContent).toBe(a.question)
    expect(container.querySelector('.back .card-content')?.textContent).toBe(a.answer)
  })

  it('waits for final POST and authoritative refill before offering Freestyle', async () => {
    const post = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockReturnValue(post.promise)
    setup(); await loaded(); await grade()
    expect(api.review).toHaveBeenCalledWith('a', 4)
    expect(screen.queryByText('Brain Upgraded')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Freestyle' })).toBeNull()
    expect(api.due).toHaveBeenCalledTimes(1)
    await act(async () => { post.resolve({ ...a, dueAt: '2030-01-01' }) })
    await screen.findByText('Brain Upgraded')
    expect(screen.getByText('1 reviewed · 1 Good or Easy')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Freestyle' }))
    expect(screen.getByText('Freestyle route')).toBeTruthy()
  })

  it('continues writes off-route and waits on returning Home before fetching due cards', async () => {
    const post = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockReturnValue(post.promise)
    const { qc } = setup()
    const invalidate = vi.spyOn(qc, 'invalidateQueries')
    await loaded(); await grade()
    fireEvent.click(screen.getByText('Leave Home'))
    expect(screen.getByText('Freestyle route')).toBeTruthy()
    fireEvent.click(screen.getByText('Return Home'))
    expect(api.due).toHaveBeenCalledTimes(1)
    await act(async () => { post.resolve(a) })
    await screen.findByText('Brain Upgraded')
    expect(api.due).toHaveBeenCalledTimes(2)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['cards'] })
    expect(screen.queryByText('1 reviewed · 1 Good or Easy')).toBeNull()
  })

  it('restores the exact failed card once and rolls back its own quality out of order', async () => {
    const first = deferred<Card>(), second = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a, b], dueCount: 2, nextDueAt: null })
    vi.mocked(api.review).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise).mockResolvedValue(a)
    setup(); await loaded(); await grade('3'); await grade('1')
    await act(async () => { second.resolve(b); first.reject(new Error('failed')) })
    await loaded()
    expect(screen.getByText('2 / 2')).toBeTruthy()
    expect(screen.queryByText('Question b')).toBeNull()
    expect(writes.failures).toHaveLength(1)
    await grade('1')
    await screen.findByText('Brain Upgraded')
    expect(screen.getByText('2 reviewed · 0 Good or Easy')).toBeTruthy()
    expect(writes.submissions[0].status).toBe('superseded')
  })

  it('old-session failure never appends its snapshot to the fresh authoritative Home queue', async () => {
    const post = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
      .mockResolvedValueOnce({ cards: [b], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockReturnValue(post.promise)
    setup(); await loaded(); await grade()
    fireEvent.click(screen.getByText('Leave Home')); fireEvent.click(screen.getByText('Return Home'))
    await act(async () => { post.reject(new Error('offline')) })
    await loaded()
    expect(screen.queryByText('Question a')).toBeNull()
    expect(screen.getByText('1 / 1')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.getByRole('button', { name: 'Retry scheduled review: Question a' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Freestyle' })).toBeNull()
  })

  it('an immediate explicit retry removes the restored card and counts it only once', async () => {
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(a)
    setup(); await loaded(); await grade('2')
    await loaded()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry scheduled review: Question a' })) })
    await screen.findByText('Brain Upgraded')
    expect(screen.getByText('1 reviewed · 0 Good or Easy')).toBeTruthy()
    expect(api.review).toHaveBeenNthCalledWith(2, 'a', 3)
    expect(screen.queryByText('Question a')).toBeNull()
  })

  it('a foreign-session retry reloads authoritative due data without importing its summary', async () => {
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
      .mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
      .mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(a)
    setup(); await loaded(); await grade()
    fireEvent.click(screen.getByText('Leave Home')); fireEvent.click(screen.getByText('Return Home'))
    await loaded()
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    // Even though an old snapshot is available, only this new GET may decide the queue.
    vi.mocked(api.due).mockReset().mockResolvedValue({ cards: [b], dueCount: 1, nextDueAt: null })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry scheduled review: Question a' })) })
    await waitFor(() => expect(screen.getAllByText('Question b')).toHaveLength(1))
    expect(screen.queryByText('Question a')).toBeNull()
    expect(screen.getByText('1 / 1')).toBeTruthy()
  })

  it('two failed submissions settling in reverse order restore each card exactly once', async () => {
    const first = deferred<Card>(), second = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a, b], dueCount: 2, nextDueAt: null })
    vi.mocked(api.review).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    setup(); await loaded(); await grade('3'); await grade('1')
    await act(async () => { second.reject(new Error('second failed')) })
    await loaded()
    await act(async () => { first.reject(new Error('first failed')) })
    expect(writes.failures).toHaveLength(2)
    expect(screen.getAllByText('Question a')).toHaveLength(1)
    expect(screen.getAllByText('Question b')).toHaveLength(1)
    expect(screen.getByText('1 / 2')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Freestyle' })).toBeNull()
  })

  it('requires reveal and grade, and cancels a committed animation when leaving Home', async () => {
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    setup(); await loaded()
    key('ArrowRight'); key('3'); key('ArrowLeft')
    expect(api.review).not.toHaveBeenCalled()
    key('Enter'); key('ArrowRight')
    expect(api.review).not.toHaveBeenCalled()
    key('3'); key('ArrowRight'); key('ArrowRight')
    fireEvent.click(screen.getByText('Leave Home'))
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 280)) })
    expect(api.review).not.toHaveBeenCalled()
  })

  it('pointer swipes stay gated until revealed and graded, then submit exactly once', async () => {
    const post = deferred<Card>()
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    vi.mocked(api.review).mockReturnValue(post.promise)
    setup(); await loaded()
    const top = screen.getByRole('button', { name: 'Tap to reveal answer' })
    const swipe = () => {
      fireEvent.pointerDown(top, { clientX: 200, clientY: 100, pointerId: 1, button: 0, isPrimary: true })
      fireEvent.pointerUp(top, { clientX: 20, clientY: 100, pointerId: 1, button: 0, isPrimary: true })
    }
    swipe()
    expect(api.review).not.toHaveBeenCalled()
    key('Enter'); swipe()
    expect(api.review).not.toHaveBeenCalled()
    key('4'); swipe(); swipe()
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 280)) })
    expect(api.review).toHaveBeenCalledTimes(1)
    expect(api.review).toHaveBeenCalledWith('a', 5)
    await act(async () => { post.resolve(a) })
    await screen.findByText('Brain Upgraded')
  })

  it('does not let the global flip handler hijack navigation buttons', async () => {
    vi.mocked(api.due).mockResolvedValueOnce({ cards: [a], dueCount: 1, nextDueAt: null })
    setup(); await loaded()
    fireEvent.keyDown(screen.getByText('Leave Home'), { key: 'Enter' })
    expect(screen.getByRole('button', { name: 'Tap to reveal answer' })).toBeTruthy()
  })

  it('preserves no-card actions without showing completion or Freestyle', async () => {
    vi.mocked(api.topics).mockResolvedValue([])
    setup()
    await screen.findByText("Let's put those brain cells to work.")
    expect(screen.getByRole('button', { name: 'Add cards' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Connect an agent' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Freestyle' })).toBeNull()
  })

  it('does not infer completion from failed topic lookup with no session evidence', async () => {
    vi.mocked(api.topics).mockRejectedValue(new Error('offline'))
    setup()
    await screen.findByText('Brain.exe stopped working')
    expect(screen.queryByText('Brain Upgraded')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Freestyle' })).toBeNull()
  })

  it('survives StrictMode read cleanup without accepting obsolete due responses', async () => {
    const stale = deferred<{ cards: Card[]; dueCount: number; nextDueAt: null }>()
    vi.mocked(api.due).mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce({ cards: [b], dueCount: 1, nextDueAt: null })
    setup(true); await loaded()
    await act(async () => { stale.resolve({ cards: [a], dueCount: 1, nextDueAt: null }) })
    await waitFor(() => expect(screen.getByText('1 / 1')).toBeTruthy())
    expect(screen.queryByText('Question a')).toBeNull()
  })
})
