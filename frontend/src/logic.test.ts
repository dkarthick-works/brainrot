import { expect, test } from 'vitest'
import { dueLabel, earliest, nextInterval, shouldPrefetch, unseen, untilLabel } from './logic'
import type { Card } from './types'

const now = Date.parse('2026-11-05T00:00:00Z')
const at = (mins: number) => new Date(now + mins * 6e4).toISOString()

test('nextInterval mirrors Sm2', () => {
  const c = { repetitions: 0, interval: 0, easeFactor: 2.5 }
  expect(nextInterval(c, 4)).toBe(1)
  expect(nextInterval({ ...c, repetitions: 1, interval: 1 }, 4)).toBe(6)
  expect(nextInterval({ ...c, repetitions: 2, interval: 6 }, 5)).toBe(15)
  expect(nextInterval({ ...c, repetitions: 5, interval: 40 }, 1)).toBe(1)
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
