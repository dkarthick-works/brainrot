import { expect, test } from 'vitest'
import { dueLabel, earliest, nextInterval, shouldPrefetch, unseen, untilLabel } from './logic'
import type { Card } from './types'

const now = Date.parse('2026-11-05T00:00:00Z')
const at = (mins: number) => new Date(now + mins * 6e4).toISOString()

test.each([
  // Same cases as Sm2Test: repetitions, interval, ease, Hard, Good, Easy
  [0, 0, 2.5, 1, 2, 4],
  [0, 1, 1.3, 1, 2, 4],
  [1, 1, 2.5, 2, 3, 4],
  [1, 2, 2.5, 3, 5, 7],
  [2, 10, 2.5, 12, 25, 33],
  [5, 6, 1.3, 7, 8, 10],
])('nextInterval mirrors Sm2 (%i repetitions, %i days, %f ease)',
  (repetitions, interval, easeFactor, hard, good, easy) => {
    const c = { repetitions, interval, easeFactor }
    for (const q of [0, 1, 2]) expect(nextInterval(c, q)).toBe(1)
    expect(nextInterval(c, 3)).toBe(hard)
    expect(nextInterval(c, 4)).toBe(good)
    expect(nextInterval(c, 5)).toBe(easy)
    expect(hard).toBeLessThan(good)
    expect(good).toBeLessThan(easy)
  })

test('untilLabel', () => {
  expect(untilLabel(at(0.2), now)).toBe('in 1m')
  expect(untilLabel(at(40), now)).toBe('in 40m')
  expect(untilLabel(at(180), now)).toBe('in 3h')
  expect(untilLabel(at(1440), now)).toBe('tomorrow')
  expect(untilLabel(at(5 * 1440), now)).toBe('in 5 days')
})

test('dueLabel', () => {
  expect(dueLabel(at(-10), now)).toBe('due now')
  expect(dueLabel(at(3 * 1440), now)).toBe('in 3d')
  expect(dueLabel(at(60 * 1440), now)).toBe('in 2mo')
})

test('queue prefetch and dedupe', () => {
  const card = (id: string) => ({ id }) as Card
  expect(unseen([card('a'), card('b'), card('c')], new Set(['a', 'c'])).map(c => c.id)).toEqual(['b'])
  expect(shouldPrefetch(5, 30)).toBe(true)
  expect(shouldPrefetch(6, 30)).toBe(false)
  expect(shouldPrefetch(3, 3)).toBe(false) // we already hold everything that's due
  expect(earliest(null, at(300), at(60))).toBe(at(60))
  expect(earliest(null, undefined)).toBeNull()
})
