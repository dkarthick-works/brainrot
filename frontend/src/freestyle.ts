import type { Card, Page } from './types'

export type FreestyleState = {
  queue: Card[]
  seenIds: ReadonlySet<string>
  acceptedCursors: ReadonlySet<string | null>
  advancedCount: number
  firstPageLoaded: boolean
  nextCursor: string | null
  loadState: 'idle' | 'loading' | 'error'
  error: unknown | null
  failedCursor: string | null
}
export type FreestyleScreenState = 'loading' | 'error' | 'playing' | 'empty' | 'complete'
export type FreestyleEvent =
  | { type: 'start' }
  | { type: 'page'; cursor: string | null; cards: Card[]; nextCursor: string | null }
  | { type: 'failure'; cursor: string | null; error: unknown }
  | { type: 'advance'; cardId: string }
  | { type: 'reset' }

export function initialFreestyleState(): FreestyleState {
  return { queue: [], seenIds: new Set(), acceptedCursors: new Set(), advancedCount: 0,
    firstPageLoaded: false, nextCursor: null, loadState: 'idle', error: null, failedCursor: null }
}

/** Copy only the array; scheduling fields and card identity are never changed. */
export function shuffle<T>(items: readonly T[], random: () => number = Math.random): T[] {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

/** Prepare once outside React reducers/updaters, which may be replayed. */
export function prepareFreestylePage(state: FreestyleState, cursor: string | null, page: Page,
  random: () => number = Math.random): FreestyleEvent {
  if (state.acceptedCursors.has(cursor) || (page.nextCursor !== null &&
      (page.nextCursor === cursor || state.acceptedCursors.has(page.nextCursor)))) {
    throw new Error('Card pagination did not advance. Try again.')
  }
  const seen = new Set(state.seenIds)
  const cards = page.cards.filter(card => {
    if (seen.has(card.id)) return false
    seen.add(card.id)
    return true
  })
  return { type: 'page', cursor, cards: shuffle(cards, random), nextCursor: page.nextCursor }
}

export function freestyleReducer(state: FreestyleState, event: FreestyleEvent): FreestyleState {
  switch (event.type) {
    case 'reset': return initialFreestyleState()
    case 'start': return { ...state, loadState: 'loading', error: null }
    case 'failure': return { ...state, loadState: 'error', error: event.error, failedCursor: event.cursor }
    case 'advance':
      return state.queue[0]?.id === event.cardId
        ? { ...state, queue: state.queue.slice(1), advancedCount: state.advancedCount + 1 } : state
    case 'page': {
      if (state.acceptedCursors.has(event.cursor)) return state
      const seenIds = new Set(state.seenIds)
      const cards = event.cards.filter(card => {
        if (seenIds.has(card.id)) return false
        seenIds.add(card.id)
        return true
      })
      return { ...state, queue: [...state.queue, ...cards], seenIds,
        acceptedCursors: new Set([...state.acceptedCursors, event.cursor]),
        firstPageLoaded: true, nextCursor: event.nextCursor,
        loadState: 'idle', error: null, failedCursor: null }
    }
  }
}

export function freestyleScreenState(state: FreestyleState): FreestyleScreenState {
  if (state.queue.length) return 'playing'
  if (state.loadState === 'error') return 'error'
  if (!state.firstPageLoaded || state.nextCursor !== null || state.loadState === 'loading') return 'loading'
  return state.advancedCount > 0 ? 'complete' : 'empty'
}
