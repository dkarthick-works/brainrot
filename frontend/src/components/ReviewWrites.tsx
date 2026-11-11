import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { api } from '../api'
import type { Card } from '../types'
import { clearReviewSaveError, showSaveError, useReviewSaveBannerVisible } from './SaveError'

export type ReviewSubmission = {
  id: string
  sessionId: string
  card: Card
  quality: number
  attemptId: number
  status: 'pending' | 'succeeded' | 'failed' | 'superseded'
  result?: Card
  error?: unknown
}

type ReviewWrites = {
  submissions: ReviewSubmission[]
  pending: number
  failures: ReviewSubmission[]
  submit: (sessionId: string, card: Card, quality: number) => string | null
  retry: (id: string) => void
}
const Context = createContext<ReviewWrites | null>(null)
let nextId = 0
export const newReviewSessionId = () => `home-${++nextId}`

/** Memory-only, authenticated-shell lifetime. POSTs deliberately outlive route changes.
 * Explicit retries can duplicate an ambiguously accepted POST: the API has no idempotency key.
 */
export function ReviewWritesProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const records = useRef<ReviewSubmission[]>([])
  const [submissions, setSubmissions] = useState<ReviewSubmission[]>([])
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => { alive.current = false; clearReviewSaveError() }
  }, [])
  useEffect(() => {
    if (!submissions.some(r => r.status === 'failed')) clearReviewSaveError()
  }, [submissions])

  function publish(next: ReviewSubmission[]) {
    records.current = next
    if (alive.current) setSubmissions(next)
  }

  function send(record: ReviewSubmission) {
    // Capture the immutable attempt, not any route-local callback.
    void Promise.resolve().then(() => api.review(record.card.id, record.quality)).then(result => {
      if (!alive.current) return
      publish(records.current.map(r => r.id === record.id
        ? { ...r, status: 'succeeded', result, error: undefined }
        : r.card.id === record.card.id && r.status === 'failed'
          ? { ...r, status: 'superseded' } : r))
      void qc.invalidateQueries({ queryKey: ['cards'] })
      void qc.invalidateQueries({ queryKey: ['topics'] })
    }, error => {
      if (!alive.current) return
      publish(records.current.map(r => r.id === record.id ? { ...r, status: 'failed', error } : r))
      showSaveError(() => retry(record.id), 'review')
    })
  }

  function retry(id: string) {
    if (!alive.current) return
    const record = records.current.find(r => r.id === id)
    if (!record || record.status !== 'failed' || records.current.some(r => r.card.id === record.card.id && r.status === 'pending')) return
    const attempt: ReviewSubmission = { ...record, status: 'pending', attemptId: record.attemptId + 1, error: undefined }
    publish(records.current.map(r => r.id === id ? attempt : r))
    send(attempt)
  }

  function submit(sessionId: string, card: Card, quality: number) {
    if (!alive.current || records.current.some(r => r.card.id === card.id && r.status === 'pending')) return null
    const record: ReviewSubmission = {
      id: `review-${++nextId}`, sessionId, card: { ...card }, quality, attemptId: 1, status: 'pending',
    }
    publish([...records.current, record])
    send(record)
    return record.id
  }

  return <Context.Provider value={{ submissions, pending: submissions.filter(r => r.status === 'pending').length,
    failures: submissions.filter(r => r.status === 'failed'), submit, retry }}>{children}</Context.Provider>
}

export function useReviewWrites() {
  const context = useContext(Context)
  if (!context) throw new Error('Play requires ReviewWritesProvider in the authenticated Shell')
  return context
}

/** Mount once above Routes. Unlike the generic 8-second banner, recovery does not expire. */
export function ReviewWritesNotice() {
  const { failures, submissions, retry } = useReviewWrites()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const transientVisible = useReviewSaveBannerVisible()
  if (!failures.length || transientVisible) return null
  return <aside className="review-writes-notice" role="status">
    <p>{failures.length === 1 ? "A scheduled review couldn't be saved." : `${failures.length} scheduled reviews couldn't be saved.`}</p>
    {pathname !== '/' && <button onClick={() => navigate('/')}>Back to home</button>}
    {failures.map(r => <button key={r.id}
      disabled={submissions.some(other => other.card.id === r.card.id && other.status === 'pending')}
      onClick={() => retry(r.id)} aria-label={`Retry scheduled review: ${r.card.question}`}>Try again</button>)}
  </aside>
}
