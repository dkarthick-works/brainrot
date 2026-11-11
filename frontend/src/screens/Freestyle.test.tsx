// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { api, ApiError } from '../api'
import type { Card } from '../types'
import Freestyle from './Freestyle'

const session = vi.hoisted(() => ({ queue: [] as Card[], advancedCount: 0, firstPageLoaded: false, nextCursor: null as string | null, loadState: 'loading' as 'idle' | 'loading' | 'error', error: null as unknown, advance: vi.fn(), retry: vi.fn(), restart: vi.fn() }))
vi.mock('../hooks/useFreestyleSession', () => ({ useFreestyleSession: () => session }))
const card: Card = Object.freeze({ id: 'a', topicId: 't', question: 'A future question', answer: 'An answer', easeFactor: 2.5, interval: 300, repetitions: 12, dueAt: '2099-01-01', lastReviewedAt: null, createdAt: '', updatedAt: '' })
function mount(menuOpen = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/freestyle']}><Routes>
    <Route path="/freestyle" element={<Freestyle menuOpen={menuOpen} />} />
    <Route path="/" element={<p>Home route</p>} /><Route path="/cards" element={<p>Cards route</p>} />
  </Routes></MemoryRouter></QueryClientProvider>)
}
beforeEach(() => {
  vi.useFakeTimers()
  Object.assign(session, { queue: [], advancedCount: 0, firstPageLoaded: false, nextCursor: null, loadState: 'loading', error: null })
  session.advance.mockReset(); session.retry.mockReset(); session.restart.mockReset()
  vi.spyOn(api, 'topics').mockResolvedValue([])
  for (const method of ['review', 'updateCard', 'createCard', 'deleteCard'] as const) vi.spyOn(api, method)
})
afterEach(() => {
  for (const method of ['review', 'updateCard', 'createCard', 'deleteCard'] as const) expect(api[method]).not.toHaveBeenCalled()
  cleanup(); vi.restoreAllMocks(); vi.useRealTimers()
})

it.each(['loading', 'error', 'empty', 'playing', 'complete', 'buffer-loading', 'buffer-error'])('keeps explicit home Exit in %s state without grades or Next', state => {
  if (state !== 'loading') Object.assign(session, { firstPageLoaded: true, loadState: 'idle' })
  if (state.includes('error')) Object.assign(session, { loadState: 'error', error: new ApiError(0, 'offline') })
  if (state === 'playing') session.queue = [card]
  if (state === 'complete') session.advancedCount = 1
  if (state.startsWith('buffer')) Object.assign(session, { advancedCount: 50, nextCursor: 'next' })
  if (state === 'buffer-loading') session.loadState = 'loading'
  mount()
  expect(screen.getByRole('heading', { name: 'Freestyle' })).toBeTruthy()
  expect(screen.queryByRole('button', { name: /^(Next|Again|Hard|Good|Easy)$/ })).toBeNull()
  if (state.startsWith('buffer')) expect(screen.queryByText('Freestyle complete')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Exit Freestyle' }))
  expect(screen.getByText('Home route')).toBeTruthy()
})
it('distinguishes empty from completed and opens Cards', () => {
  Object.assign(session, { firstPageLoaded: true, loadState: 'idle' }); mount()
  expect(screen.getByText('No cards yet')).toBeTruthy()
  expect(screen.queryByText('Play again')).toBeNull()
  fireEvent.click(screen.getByText('Add cards')); expect(screen.getByText('Cards route')).toBeTruthy()
})
it('restarts completed passes in place and Back to home explicitly routes home', () => {
  Object.assign(session, { firstPageLoaded: true, loadState: 'idle', advancedCount: 20 }); mount()
  fireEvent.click(screen.getByText('Play again')); expect(session.restart).toHaveBeenCalledOnce()
  fireEvent.click(screen.getByText('Back to home')); expect(screen.getByText('Home route')).toBeTruthy()
})
it('shows a nonblocking read error and retries without changing the card/count', () => {
  Object.assign(session, { queue: [card], advancedCount: 49, firstPageLoaded: true, loadState: 'error', error: new ApiError(0, 'offline'), nextCursor: 'page2' })
  const before = JSON.stringify(card); mount()
  expect(screen.getByRole('status').textContent).toBe('Card 50')
  expect(screen.getByRole('alert').textContent).toContain("Couldn't reach brainrot")
  fireEvent.click(screen.getByText('Try again'))
  fireEvent.keyDown(screen.getByText('Try again'), { key: 'Enter' })
  expect(session.retry).toHaveBeenCalledOnce()
  expect(screen.getByRole('button', { name: 'Tap to reveal answer' })).toBeTruthy()
  expect(session.restart).not.toHaveBeenCalled(); expect(JSON.stringify(card)).toBe(before)
})
it('uses blocking server failure when the buffer is empty', () => {
  Object.assign(session, { firstPageLoaded: true, loadState: 'error', error: new ApiError(500, 'server'), advancedCount: 50, nextCursor: 'page2' }); mount()
  expect(screen.getByText('Brain.exe stopped working')).toBeTruthy()
  expect(screen.getByText('Something broke on our end. Try again in a moment.')).toBeTruthy()
  fireEvent.click(screen.getByText('Try again')); expect(session.retry).toHaveBeenCalledOnce()
})
it('does not block cards on missing/failed topics and never shows raw topic IDs', async () => {
  vi.mocked(api.topics).mockRejectedValue(new Error('topics failed'))
  Object.assign(session, { queue: [card], firstPageLoaded: true, loadState: 'idle' }); const view = mount()
  await act(async () => { await Promise.resolve() })
  expect(screen.getByRole('button', { name: 'Tap to reveal answer' })).toBeTruthy()
  expect(view.container.querySelector('.topic')).toBeNull()
  expect(screen.queryByText('Brain.exe stopped working')).toBeNull()
})
it('flips and advances a future-due card without writes or scheduling mutations', () => {
  Object.assign(session, { queue: [card], firstPageLoaded: true, loadState: 'idle' })
  const before = JSON.stringify(card)
  const media = vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true } as MediaQueryList)
  mount()
  const top = screen.getByRole('button', { name: 'Tap to reveal answer' })
  fireEvent.keyDown(top, { key: 'Enter' })
  expect(top.getAttribute('aria-label')).toContain('Answer shown')
  for (const [type, x] of [['pointerdown', 0], ['pointerup', 150]] as const) {
    const event = new MouseEvent(type, { bubbles: true, clientX: x, button: 0 })
    Object.defineProperties(event, { pointerId: { value: 1 }, isPrimary: { value: true } })
    fireEvent(top, event)
  }
  expect(session.advance).toHaveBeenCalledExactlyOnceWith('a')
  expect(JSON.stringify(card)).toBe(before)
  media.mockRestore()
})
it('makes the screen inert behind the drawer and suspends the stack', () => {
  Object.assign(session, { queue: [card], firstPageLoaded: true, loadState: 'idle' }); const view = mount(true)
  expect(view.container.querySelector('.freestyle')?.hasAttribute('inert')).toBe(true)
  const top = screen.getByRole('button', { name: 'Tap to reveal answer' })
  fireEvent.keyDown(top, { key: ' ' }); expect(top.getAttribute('aria-label')).toBe('Tap to reveal answer')
})
