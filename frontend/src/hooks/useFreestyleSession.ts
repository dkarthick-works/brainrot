import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { freestyleReducer, freestyleScreenState, initialFreestyleState, prepareFreestylePage } from '../freestyle'
import type { FreestyleEvent } from '../freestyle'

/** Local, read-only, one-pass deck. No query cache, persistence, or review writes. */
export function useFreestyleSession() {
  const [state, setState] = useState(initialFreestyleState)
  // Immediate transitions prevent rapid input and promise settlements from observing stale renders.
  const current = useRef(state)
  const mounted = useRef(false)
  const generation = useRef(0)
  const request = useRef<{ generation: number; controller: AbortController } | null>(null)
  const publish = useCallback((event: FreestyleEvent) => {
    current.current = freestyleReducer(current.current, event)
    if (mounted.current) setState(current.current)
  }, [])
  const cancel = useCallback(() => {
    generation.current++
    const old = request.current
    request.current = null
    old?.controller.abort()
  }, [])

  const load = useCallback((cursor: string | null) => {
    if (!mounted.current || request.current) return
    const owned = { generation: generation.current, controller: new AbortController() }
    request.current = owned
    publish({ type: 'start' })
    const isCurrent = () => mounted.current && request.current === owned && generation.current === owned.generation
    void (async () => {
      try {
        const page = await api.cards({ topicId: null, q: '', after: cursor }, owned.controller.signal)
        if (!isCurrent()) return
        const event = prepareFreestylePage(current.current, cursor, page)
        request.current = null
        publish(event)
      } catch (error) {
        if (!isCurrent()) return
        request.current = null
        publish({ type: 'failure', cursor, error })
      }
      // No unconditional finally: an obsolete request must never release a newer guard.
    })()
  }, [publish])

  useEffect(() => {
    mounted.current = true
    // StrictMode cleanup aborts the first read; setup must replace it even if state says loading.
    if (!current.current.firstPageLoaded) load(null)
    return () => {
      mounted.current = false
      cancel()
    }
  }, [cancel, load])

  useEffect(() => {
    if (state.loadState !== 'idle') return
    if (!state.firstPageLoaded) load(null)
    else if (state.queue.length <= 5 && state.nextCursor !== null) load(state.nextCursor)
  }, [state, load])

  const advance = useCallback((cardId: string) => {
    if (mounted.current) publish({ type: 'advance', cardId })
  }, [publish])
  const retry = useCallback(() => {
    if (current.current.loadState === 'error') load(current.current.failedCursor)
  }, [load])
  const restart = useCallback(() => {
    if (!mounted.current) return
    cancel()
    publish({ type: 'reset' })
    load(null)
  }, [cancel, publish, load])

  return { queue: state.queue, advancedCount: state.advancedCount,
    firstPageLoaded: state.firstPageLoaded, nextCursor: state.nextCursor,
    loadState: state.loadState, error: state.error,
    screenState: freestyleScreenState(state), advance, retry, restart }
}
