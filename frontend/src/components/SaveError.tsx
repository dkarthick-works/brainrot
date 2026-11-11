import { useMutation, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import { useEffect, useSyncExternalStore } from 'react'
import type { ApiError } from '../api'
import { CLOSE, Icon } from './ui'

// One app-wide "Couldn't save" banner. Holds the retry for the last failed save.
type SaveNotice = { retry: () => void; kind: 'generic' | 'review' }
let notice: SaveNotice | null = null
const listeners = new Set<() => void>()
function setRetry(r: (() => void) | null, kind: SaveNotice['kind'] = 'generic') {
  notice = r ? { retry: r, kind } : null
  listeners.forEach(l => l())
}
function useSaveNotice() {
  return useSyncExternalStore(
    l => (listeners.add(l), () => void listeners.delete(l)),
    () => notice,
  )
}
export const useReviewSaveBannerVisible = () => useSaveNotice()?.kind === 'review'
export function clearReviewSaveError() {
  if (notice?.kind === 'review') setRetry(null)
}

export function SaveErrorBanner() {
  const current = useSaveNotice()
  const r = current?.retry
  // Clears itself after 8s
  useEffect(() => {
    if (!r) return
    const t = setTimeout(() => setRetry(null), 8000)
    return () => clearTimeout(t)
  }, [r])
  if (!r) return null
  return (
    <div className="save-error" role="alert">
      <span>{current?.kind === 'review' ? "A scheduled review couldn't be saved." : "Couldn't save. Your change was undone."}</span>
      <button onClick={() => { setRetry(null); r() }}>Try again</button>
      <button className="dismiss" aria-label="Dismiss" onClick={() => setRetry(null)}>
        <Icon>{CLOSE}</Icon>
      </button>
    </div>
  )
}

type SaveOptions<V, R> = {
  fn: (v: V) => Promise<R>
  /** Caches this save changes: snapshotted, optimistically updated by `apply`, restored on failure, refetched after. */
  touches: QueryKey[]
  apply?: (v: V, qc: QueryClient) => void
  onSuccess?: (result: R, v: V) => void
  /** Return true when the error was shown in place (a specific error), so no banner. The cache is restored either way. */
  onError?: (e: ApiError, v: V) => boolean | void
}

/** An optimistic write: the screen updates now, the request runs in the background, failure undoes it. */
export function useSave<V, R = unknown>(o: SaveOptions<V, R>) {
  const qc = useQueryClient()
  const { mutate } = useMutation({
    mutationFn: o.fn,
    onMutate: async (v: V) => {
      await Promise.all(o.touches.map(k => qc.cancelQueries({ queryKey: k })))
      const snapshot = o.touches.flatMap(k => qc.getQueriesData({ queryKey: k }))
      o.apply?.(v, qc)
      return snapshot
    },
    onError: (e, v, snapshot) => {
      snapshot?.forEach(([k, d]) => qc.setQueryData(k, d))
      if (o.onError?.(e as ApiError, v)) return
      setRetry(() => mutate(v))
    },
    onSuccess: (r, v) => o.onSuccess?.(r, v),
    onSettled: () => o.touches.forEach(k => qc.invalidateQueries({ queryKey: k })),
  })
  return mutate
}

/** Shows the banner for a failure handled outside useSave (Play reviews). */
export const showSaveError = (r: () => void, kind: SaveNotice['kind'] = 'generic') => setRetry(r, kind)
