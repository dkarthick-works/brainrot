import { describe, expect, it } from 'vitest'
import { freestyleReducer as reduce, freestyleScreenState as screen, initialFreestyleState as initial, prepareFreestylePage as prepare, shuffle } from './freestyle'
import type { Card } from './types'

const card = (id: string): Card => Object.freeze({ id, topicId: 'topic', question: id, answer: 'answer', easeFactor: 2.5,
  interval: 10, repetitions: 3, dueAt: 'future', lastReviewedAt: null, createdAt: 'created', updatedAt: 'updated' })

describe('Freestyle pure transitions', () => {
  it('uses deterministic Fisher–Yates without mutating arrays or card fields', () => {
    const cards = Object.freeze([card('a'), card('b'), card('c')])
    expect(shuffle([], () => 0)).toEqual([])
    expect(shuffle([cards[0]], () => 0)).toEqual([cards[0]])
    expect(shuffle(cards, () => 0)).toEqual([cards[1], cards[2], cards[0]])
    expect(cards.map(c => c.id)).toEqual(['a', 'b', 'c'])
    expect(shuffle(cards).map(c => c.id).sort()).toEqual(['a', 'b', 'c'])
  })

  it('deduplicates within/across pages, preserves cursor and accepted order, applies once', () => {
    const start = initial()
    const event = prepare(start, null, { cards: [card('a'), card('a'), card('b')], nextCursor: 'server' }, () => 0)
    const first = reduce(start, event)
    expect(first.queue.map(c => c.id)).toEqual(['b', 'a'])
    expect(first.nextCursor).toBe('server')
    expect(reduce(first, event)).toBe(first)
    const next = reduce(first, prepare(first, 'server', { cards: [card('a'), card('c'), card('c')], nextCursor: null }))
    expect(next.queue.map(c => c.id)).toEqual(['b', 'a', 'c'])
    expect([...next.seenIds]).toEqual(['b', 'a', 'c'])
    expect(start.seenIds.size).toBe(0)
    expect(first.queue.map(c => c.id)).toEqual(['b', 'a'])
  })

  it('consumes only current top once, retains IDs but discards consumed objects', () => {
    const first = reduce(initial(), prepare(initial(), null, { cards: [card('a'), card('b')], nextCursor: null }, () => .99))
    expect(reduce(first, { type: 'advance', cardId: 'b' })).toBe(first)
    const next = reduce(first, { type: 'advance', cardId: 'a' })
    expect(reduce(next, { type: 'advance', cardId: 'a' })).toBe(next)
    expect(next.advancedCount).toBe(1)
    expect(next.queue.map(c => c.id)).toEqual(['b'])
    expect(next.seenIds.has('a')).toBe(true)
  })

  it('distinguishes empty traversal, pending pages, buffer loading/error and completion', () => {
    expect(screen(initial())).toBe('loading')
    let state = reduce(initial(), prepare(initial(), null, { cards: [], nextCursor: 'next' }))
    expect(screen(state)).toBe('loading')
    state = reduce(state, prepare(state, 'next', { cards: [card('a')], nextCursor: 'final' }))
    expect(screen(state)).toBe('playing')
    state = reduce(state, { type: 'advance', cardId: 'a' })
    state = reduce(state, { type: 'start' })
    expect(screen(state)).toBe('loading')
    state = reduce(state, { type: 'failure', cursor: 'final', error: new Error() })
    expect(screen(state)).toBe('error')
    state = reduce(state, prepare(state, 'final', { cards: [], nextCursor: null }))
    expect(screen(state)).toBe('complete')
    expect(screen(reduce(initial(), prepare(initial(), null, { cards: [], nextCursor: null })))).toBe('empty')
    expect(reduce(state, { type: 'reset' })).toEqual(initial())
  })

  it('rejects non-progressing and cyclic cursors before accepting cards', () => {
    const first = reduce(initial(), prepare(initial(), null, { cards: [], nextCursor: 'a' }))
    expect(() => prepare(first, 'a', { cards: [card('x')], nextCursor: 'a' })).toThrow(/pagination/)
    const second = reduce(first, prepare(first, 'a', { cards: [], nextCursor: 'b' }))
    expect(() => prepare(second, 'b', { cards: [], nextCursor: 'a' })).toThrow(/pagination/)
    expect(() => prepare(second, 'a', { cards: [], nextCursor: null })).toThrow(/pagination/)
  })

  it('errors retain queue identity, counters, cursor bookkeeping and seen IDs', () => {
    const state = reduce(initial(), prepare(initial(), null, { cards: [card('a')], nextCursor: 'next' }))
    const failed = reduce(state, { type: 'failure', cursor: 'next', error: 'offline' })
    expect(failed.queue).toBe(state.queue)
    expect(failed.seenIds).toBe(state.seenIds)
    expect(failed.acceptedCursors).toBe(state.acceptedCursors)
    expect(failed.nextCursor).toBe('next')
    expect(screen(failed)).toBe('playing')
  })
})
