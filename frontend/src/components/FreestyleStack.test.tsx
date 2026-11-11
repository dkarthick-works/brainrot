// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Card } from '../types'
import FreestyleStack from './FreestyleStack'

const cards: Card[] = ['a', 'b', 'c'].map(id => Object.freeze({ id, topicId: 't', question: `Question ${id}`, answer: `Answer ${id}`, easeFactor: 2.5, interval: 30, repetitions: 4, dueAt: '2099-01-01', lastReviewedAt: null, createdAt: '', updatedAt: '' }))
const props = () => ({ queue: cards, topicName: () => 'Topic', counter: 1, suspended: false, onAdvance: vi.fn() })
function pointer(element: HTMLElement, type: 'down' | 'move' | 'up' | 'cancel', x = 0, y = 0, id = 1, primary = true, button = 0) {
  const names = { down: 'pointerdown', move: 'pointermove', up: 'pointerup', cancel: 'pointercancel' }
  const event = new MouseEvent(names[type], { bubbles: true, clientX: x, clientY: y, button })
  Object.defineProperties(event, { pointerId: { value: id }, isPrimary: { value: primary } })
  fireEvent(element, event)
}
function swipe(element: HTMLElement, x = 150) { pointer(element, 'down'); pointer(element, 'move', x); pointer(element, 'up', x) }
function end(element: HTMLElement, propertyName = 'transform') {
  const event = new Event('transitionend', { bubbles: true })
  Object.defineProperty(event, 'propertyName', { value: propertyName })
  fireEvent(element, event)
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
  HTMLElement.prototype.setPointerCapture = vi.fn()
  HTMLElement.prototype.hasPointerCapture = vi.fn(() => true)
  HTMLElement.prototype.releasePointerCapture = vi.fn()
})
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals() })

describe('Freestyle gestures', () => {
  it('exposes only the top/visible face, taps both ways, and resets face and scroll on a new card', () => {
    const p = props()
    const view = render(<FreestyleStack {...p} />)
    const top = screen.getByRole('button')
    expect(view.container.querySelector('.back')?.getAttribute('aria-hidden')).toBe('true')
    pointer(top, 'down'); pointer(top, 'up')
    expect(screen.getByRole('button').getAttribute('aria-label')).toContain('Answer shown')
    pointer(top, 'down'); pointer(top, 'up')
    expect(screen.getByRole('button').getAttribute('aria-label')).toBe('Tap to reveal answer')
    fireEvent.keyDown(top, { key: 'Enter' })
    const content = view.container.querySelector('.card-content')!
    content.scrollTop = 100
    view.rerender(<FreestyleStack {...p} queue={cards.slice(1)} counter={2} />)
    expect(screen.getByRole('button').getAttribute('aria-label')).toBe('Tap to reveal answer')
    expect(view.container.querySelector('.card-content')!.scrollTop).toBe(0)
    expect(screen.getByRole('status').textContent).toBe('Card 2')
    expect(screen.getAllByRole('button')).toHaveLength(1)
  })
  it.each([-150, 150])('advances unrevealed cards once in direction %s, filtering transitions', x => {
    const p = props(); const view = render(<FreestyleStack {...p} />)
    const top = screen.getByRole('button')
    swipe(top, x); swipe(top, x)
    fireEvent.keyDown(top, { key: ' ' })
    end(view.container.querySelector('.card-inner')! as HTMLElement)
    end(top, 'opacity')
    expect(p.onAdvance).not.toHaveBeenCalled()
    end(top); act(() => vi.runAllTimers())
    expect(p.onAdvance).toHaveBeenCalledExactlyOnceWith('a')
    expect(top.getAttribute('aria-label')).toBe('Tap to reveal answer')
  })
  it('uses strict distance thresholds and releases captured pointers', () => {
    const p = props(); render(<FreestyleStack {...p} />); const top = screen.getByRole('button')
    swipe(top, 100)
    pointer(top, 'down'); pointer(top, 'up', 6)
    act(() => vi.runAllTimers())
    expect(p.onAdvance).not.toHaveBeenCalled()
    expect(top.getAttribute('aria-label')).toBe('Tap to reveal answer')
    expect(top.setPointerCapture).toHaveBeenCalledWith(1)
    expect(top.releasePointerCapture).toHaveBeenCalledWith(1)
    pointer(top, 'down'); pointer(top, 'up', 5, 5)
    expect(top.getAttribute('aria-label')).toContain('Answer shown')
  })
  it('falls back if no transition is delivered', () => {
    const p = props(); render(<FreestyleStack {...p} />)
    swipe(screen.getByRole('button'))
    act(() => vi.advanceTimersByTime(300))
    expect(p.onAdvance).toHaveBeenCalledExactlyOnceWith('a')
  })
  it('does not flip or advance short drags, out-and-back movement or vertical scrolling', () => {
    const p = props(); render(<FreestyleStack {...p} />); const top = screen.getByRole('button')
    swipe(top, 60)
    pointer(top, 'down'); pointer(top, 'move', 40); pointer(top, 'up')
    pointer(top, 'down'); pointer(top, 'move', 2, 40); pointer(top, 'up', 160, 45)
    act(() => vi.runAllTimers())
    expect(p.onAdvance).not.toHaveBeenCalled()
    expect(top.getAttribute('aria-label')).toBe('Tap to reveal answer')
  })
  it('ignores non-primary/right-button pointers and wrong IDs; cancel/lost capture never dismiss', () => {
    const p = props(); render(<FreestyleStack {...p} />); const top = screen.getByRole('button')
    pointer(top, 'down', 0, 0, 2, false); pointer(top, 'up', 200, 0, 2)
    pointer(top, 'down', 0, 0, 1, true, 2); pointer(top, 'up', 200)
    pointer(top, 'down'); pointer(top, 'down', 0, 0, 2); pointer(top, 'up', 200, 0, 2)
    pointer(top, 'move', 150); pointer(top, 'cancel'); pointer(top, 'up', 150)
    pointer(top, 'down'); pointer(top, 'move', 150)
    const lost = new Event('lostpointercapture', { bubbles: true }); Object.defineProperty(lost, 'pointerId', { value: 1 }); fireEvent(top, lost)
    pointer(top, 'up', 150)
    act(() => vi.runAllTimers()); expect(p.onAdvance).not.toHaveBeenCalled()
  })
  it('drawer opening cancels drag, blocks keyboard/pointers, but lets one committed exit finish', () => {
    const p = props(); const view = render(<FreestyleStack {...p} />); const top = screen.getByRole('button')
    pointer(top, 'down'); pointer(top, 'move', 150)
    view.rerender(<FreestyleStack {...p} suspended />)
    pointer(top, 'up', 150); swipe(top); fireEvent.keyDown(top, { key: 'Enter' })
    act(() => vi.runAllTimers()); expect(p.onAdvance).not.toHaveBeenCalled()
    view.rerender(<FreestyleStack {...p} />); swipe(top)
    view.rerender(<FreestyleStack {...p} suspended />)
    act(() => vi.runAllTimers()); expect(p.onAdvance).toHaveBeenCalledExactlyOnceWith('a')
  })
  it('reduced motion advances immediately, once', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const p = props(); render(<FreestyleStack {...p} />); const top = screen.getByRole('button')
    swipe(top); swipe(top); act(() => vi.runAllTimers())
    expect(p.onAdvance).toHaveBeenCalledExactlyOnceWith('a')
  })
  it.each(['unmount', 'replace'])('clears pending advancement on %s', operation => {
    const p = props(); const view = render(<FreestyleStack {...p} />)
    swipe(screen.getByRole('button'))
    if (operation === 'unmount') view.unmount()
    else view.rerender(<FreestyleStack {...p} queue={cards.slice(1)} />)
    act(() => vi.runAllTimers()); expect(p.onAdvance).not.toHaveBeenCalled()
  })
  it('does not install global keyboard navigation', () => {
    const p = props(); render(<><button>Exit Freestyle</button><FreestyleStack {...p} /></>)
    fireEvent.keyDown(screen.getByText('Exit Freestyle'), { key: 'Enter' })
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(screen.getByRole('button', { name: 'Tap to reveal answer' })).toBeTruthy()
    expect(p.onAdvance).not.toHaveBeenCalled()
  })
})
