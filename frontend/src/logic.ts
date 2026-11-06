import type { Card } from './types'

// Quality values match Sm2.java: Again=1, Hard=3, Good=4, Easy=5
export const GRADES = [
  { label: 'Again', quality: 1 },
  { label: 'Hard', quality: 3 },
  { label: 'Good', quality: 4 },
  { label: 'Easy', quality: 5 },
]

/** Days until the card returns if graded `q`. Mirrors Sm2.java; used only for the button hints. */
export function nextInterval(c: Pick<Card, 'repetitions' | 'interval' | 'easeFactor'>, q: number) {
  if (q < 3) return 1
  if (c.repetitions === 0) return q === 3 ? 1 : q === 4 ? 2 : 4
  const hard = Math.max(c.interval + 1, Math.round(c.interval * 1.2))
  const good = Math.max(hard + 1, Math.round(c.interval * c.easeFactor))
  const easy = Math.max(good + 1, Math.round(c.interval * c.easeFactor * 1.3))
  return q === 3 ? hard : q === 4 ? good : easy
}

/** "in 40m", "in 3h", "tomorrow", "in 5 days" (Brain Upgraded) */
export function untilLabel(iso: string, now = Date.now()) {
  const mins = (new Date(iso).getTime() - now) / 6e4
  if (mins < 60) return `in ${Math.max(1, Math.round(mins))}m`
  if (mins < 60 * 24) return `in ${Math.round(mins / 60)}h`
  const days = Math.round(mins / 1440)
  return days <= 1 ? 'tomorrow' : `in ${days} days`
}

/** "due now", "in 3d", "in 2mo" (Cards rows) */
export function dueLabel(iso: string, now = Date.now()) {
  const days = Math.ceil((new Date(iso).getTime() - now) / 864e5)
  if (days <= 0) return 'due now'
  return days < 30 ? `in ${days}d` : `in ${Math.round(days / 30)}mo`
}

/** "Used 2h ago" / "Never used" (API keys) */
export function usedLabel(iso: string | null, now = Date.now()) {
  if (!iso) return 'Never used'
  const mins = Math.round((now - new Date(iso).getTime()) / 6e4)
  if (mins < 60) return `Used ${mins}m ago`
  if (mins < 60 * 24) return `Used ${Math.round(mins / 60)}h ago`
  return `Used ${Math.round(mins / 1440)}d ago`
}

export const plural = (n: number, word: string) => `${n} ${n === 1 ? word : word + 's'}`

/** Cards from a fresh /cards/due batch that this session hasn't queued yet (unreviewed ones come back too). */
export function unseen(batch: Card[], seen: ReadonlySet<string>) {
  return batch.filter(c => !seen.has(c.id))
}

/** Prefetch when 5 or fewer remain and the server says more are due than we hold. */
export function shouldPrefetch(queued: number, dueCount: number) {
  return queued <= 5 && dueCount > queued
}

/** Earliest of the given ISO times (nulls ignored), for "Next card due in …" */
export function earliest(...isos: (string | null | undefined)[]) {
  const ts = isos.filter((s): s is string => !!s).sort((a, b) => Date.parse(a) - Date.parse(b))
  return ts[0] ?? null
}
