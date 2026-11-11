// @vitest-environment jsdom
import { StrictMode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import type { Card, Page } from '../types'
import { useFreestyleSession } from './useFreestyleSession'

vi.mock('../api', () => ({ api: { cards: vi.fn(), review: vi.fn(), updateCard: vi.fn(), createCard: vi.fn(), deleteCard: vi.fn() } }))
const cards = (count: number, prefix = ''): Card[] => Array.from({ length: count }, (_, i) => Object.freeze({
  id: `${prefix}${i}`, topicId: 'topic', question: 'question', answer: 'answer', easeFactor: 2.5,
  interval: 7, repetitions: 2, dueAt: 'future', lastReviewedAt: null, createdAt: 'created', updatedAt: 'updated',
}))
function deferred() {
  let resolve!: (page: Page) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<Page>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const reads = vi.mocked(api.cards)
beforeEach(() => { vi.clearAllMocks(); vi.spyOn(Math, 'random').mockReturnValue(.999) })
afterEach(() => {
  cleanup()
  for (const write of [api.review, api.updateCard, api.createCard, api.deleteCard]) expect(write).not.toHaveBeenCalled()
  vi.restoreAllMocks()
})

describe('useFreestyleSession read lifecycle', () => {
  it('loads all-topic first page, prefetches at five not six, and guards rapid input/requests', async () => {
    const next = deferred()
    reads.mockResolvedValueOnce({ cards: cards(7), nextCursor: 'next' }).mockReturnValueOnce(next.promise)
    const { result, rerender } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.queue).toHaveLength(7))
    expect(reads.mock.calls[0][0]).toEqual({ topicId: null, q: '', after: null })
    act(() => result.current.advance('0'))
    expect(result.current.queue).toHaveLength(6)
    expect(reads).toHaveBeenCalledTimes(1)
    act(() => { result.current.advance('1'); result.current.advance('1') })
    expect(result.current.advancedCount).toBe(2)
    expect(reads).toHaveBeenCalledTimes(2)
    act(() => { result.current.advance('2'); result.current.retry() })
    rerender()
    expect(reads).toHaveBeenCalledTimes(2)
    await act(async () => next.resolve({ cards: cards(2, 'new'), nextCursor: null }))
    expect(result.current.queue.map(c => c.id)).toEqual(['3', '4', '5', '6', 'new0', 'new1'])
  })

  it('initial failure retries the first cursor and distinguishes empty from complete', async () => {
    reads.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ cards: [], nextCursor: null })
    const { result } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.screenState).toBe('error'))
    act(() => { result.current.retry(); result.current.retry() })
    await waitFor(() => expect(result.current.screenState).toBe('empty'))
    expect(reads).toHaveBeenCalledTimes(2)
    expect(reads.mock.calls[1][0].after).toBeNull()
  })

  it('prefetch failure preserves progress/order, stops automatic attempts, retries failed cursor', async () => {
    reads.mockResolvedValueOnce({ cards: cards(6), nextCursor: 'next' }).mockRejectedValueOnce(new Error('server'))
    const retry = deferred()
    reads.mockReturnValueOnce(retry.promise)
    const { result, rerender } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.queue).toHaveLength(6))
    act(() => result.current.advance('0'))
    await waitFor(() => expect(result.current.loadState).toBe('error'))
    expect(result.current.queue.map(c => c.id)).toEqual(['1', '2', '3', '4', '5'])
    expect(result.current.advancedCount).toBe(1)
    expect(result.current.screenState).toBe('playing')
    act(() => { for (let i = 1; i < 6; i++) result.current.advance(`${i}`) })
    rerender()
    expect(result.current.screenState).toBe('error')
    expect(reads).toHaveBeenCalledTimes(2)
    act(() => result.current.retry())
    expect(result.current.screenState).toBe('loading')
    expect(reads.mock.calls[2][0].after).toBe('next')
    await act(async () => retry.resolve({ cards: [], nextCursor: null }))
    expect(result.current.screenState).toBe('complete')
    expect(result.current.advancedCount).toBe(6)
  })

  it('continues empty/deduplicated pages and rejects cyclic cursors recoverably', async () => {
    reads.mockResolvedValueOnce({ cards: cards(1), nextCursor: 'a' })
      .mockResolvedValueOnce({ cards: cards(1), nextCursor: 'b' })
      .mockResolvedValueOnce({ cards: cards(1, 'bad'), nextCursor: 'a' })
      .mockResolvedValueOnce({ cards: [], nextCursor: null })
    const { result } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.loadState).toBe('error'))
    expect(reads).toHaveBeenCalledTimes(3)
    expect(result.current.queue.map(c => c.id)).toEqual(['0'])
    act(() => result.current.retry())
    await waitFor(() => expect(result.current.loadState).toBe('idle'))
    expect(reads.mock.calls[3][0].after).toBe('b')
    act(() => result.current.advance('0'))
    expect(result.current.screenState).toBe('complete')
  })

  it.each(['resolve', 'reject'] as const)('restart ignores old %s and cannot release the new guard', async settlement => {
    const old = deferred(), fresh = deferred()
    reads.mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
    const { result, rerender } = renderHook(useFreestyleSession)
    const signal = reads.mock.calls[0][1]!
    act(() => result.current.restart())
    expect(signal.aborted).toBe(true)
    await act(async () => {
      if (settlement === 'resolve') old.resolve({ cards: cards(1, 'old'), nextCursor: 'old-next' })
      else old.reject(new DOMException('Aborted', 'AbortError'))
    })
    act(() => result.current.retry())
    rerender()
    expect(result.current.error).toBeNull()
    expect(result.current.queue).toEqual([])
    expect(reads).toHaveBeenCalledTimes(2)
    await act(async () => fresh.resolve({ cards: cards(1, 'fresh'), nextCursor: null }))
    expect(result.current.queue[0].id).toBe('fresh0')
  })

  it('StrictMode aborts/replaces its read and ignores obsolete settlements', async () => {
    const old = deferred(), replacement = deferred()
    reads.mockReturnValueOnce(old.promise).mockReturnValueOnce(replacement.promise)
    const { result } = renderHook(useFreestyleSession, { wrapper: StrictMode })
    expect(reads).toHaveBeenCalledTimes(2)
    expect(reads.mock.calls[0][1]!.aborted).toBe(true)
    expect(reads.mock.calls[1][1]!.aborted).toBe(false)
    await act(async () => old.resolve({ cards: cards(1, 'old'), nextCursor: null }))
    expect(result.current.queue).toEqual([])
    expect(result.current.loadState).toBe('loading')
    await act(async () => replacement.resolve({ cards: cards(1), nextCursor: null }))
    expect(result.current.queue[0].id).toBe('0')
  })

  it('unmount aborts reads; stale completion cannot affect a fresh mount', async () => {
    const old = deferred()
    reads.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ cards: cards(1, 'new'), nextCursor: null })
    const first = renderHook(useFreestyleSession)
    first.unmount()
    expect(reads.mock.calls[0][1]!.aborted).toBe(true)
    const fresh = renderHook(useFreestyleSession)
    await waitFor(() => expect(fresh.result.current.queue).toHaveLength(1))
    await act(async () => old.resolve({ cards: cards(1, 'old'), nextCursor: null }))
    expect(fresh.result.current.queue[0].id).toBe('new0')
  })

  it('an exhausted loading buffer is not completion and unmount cancels prefetch', async () => {
    const next = deferred()
    reads.mockResolvedValueOnce({ cards: cards(1), nextCursor: 'next' }).mockReturnValueOnce(next.promise)
    const { result, unmount } = renderHook(useFreestyleSession)
    await waitFor(() => expect(reads).toHaveBeenCalledTimes(2))
    act(() => result.current.advance('0'))
    expect(result.current.screenState).toBe('loading')
    expect(result.current.advancedCount).toBe(1)
    unmount()
    expect(reads.mock.calls[1][1]!.aborted).toBe(true)
    await act(async () => next.reject(new DOMException('Aborted', 'AbortError')))
    expect(reads).toHaveBeenCalledTimes(2)
  })

  it('traverses empty progressing pages before declaring an empty collection', async () => {
    reads.mockResolvedValueOnce({ cards: [], nextCursor: 'next' })
      .mockResolvedValueOnce({ cards: [], nextCursor: null })
    const { result } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.screenState).toBe('empty'))
    expect(reads).toHaveBeenCalledTimes(2)
    expect(result.current.advancedCount).toBe(0)
  })

  it('restart clears completed progress and refetches/reshuffles rather than caching', async () => {
    reads.mockResolvedValue({ cards: cards(2), nextCursor: null })
    const { result } = renderHook(useFreestyleSession)
    await waitFor(() => expect(result.current.queue).toHaveLength(2))
    act(() => { result.current.advance('0'); result.current.advance('1') })
    expect(result.current.screenState).toBe('complete')
    const randomCalls = vi.mocked(Math.random).mock.calls.length
    act(() => result.current.restart())
    expect(result.current.advancedCount).toBe(0)
    expect(result.current.firstPageLoaded).toBe(false)
    await waitFor(() => expect(result.current.queue).toHaveLength(2))
    expect(reads).toHaveBeenCalledTimes(2)
    expect(vi.mocked(Math.random).mock.calls.length).toBeGreaterThan(randomCalls)
  })
})
