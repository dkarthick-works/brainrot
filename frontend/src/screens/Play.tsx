import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, topicsQuery } from '../api'
import ErrorState from '../components/ErrorState'
import Loading from '../components/Loading'
import { newReviewSessionId, useReviewWrites } from '../components/ReviewWrites'
import { earliest, GRADES, nextInterval, shouldPrefetch, unseen, untilLabel } from '../logic'
import type { Card } from '../types'

type Session = {
  queue: Card[] // cards left to grade, top first
  seen: Set<string> // every id queued this session, so refetched batches don't repeat them
  total: number // dueCount at session start, for "position / total"
  dueCount: number // from the latest /cards/due response
  nextDueAt: string | null // soonest future dueAt, from the server and from cards graded here
  exhausted: boolean // the last fetch added nothing new
}

export default function Play({ menuOpen }: { menuOpen: boolean }) {
  const navigate = useNavigate()
  const writes = useReviewWrites()
  const [sessionId] = useState(newReviewSessionId)
  const handled = useRef(new Set<string>())
  const foreignSuccesses = useRef(new Set(writes.submissions.filter(r => r.status === 'succeeded').map(r => `${r.id}:${r.attemptId}`)))
  const mounted = useRef(false)
  const fetchToken = useRef(0)
  const fetchBusy = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; fetchToken.current++; fetchBusy.current = false }
  }, [])
  const topics = useQuery(topicsQuery)
  const [s, setS] = useState<Session | null>(null)
  const [fetching, setFetching] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const sessionWrites = writes.submissions.filter(r => r.sessionId === sessionId)
  const reviewed = sessionWrites.filter(r => r.status === 'pending' || r.status === 'succeeded').map(r => r.quality)

  // Foreign successes trigger an authoritative reload, never apply their old card snapshot
  // or summary to this session (e.g. retrying an earlier Home failure from the notice).
  useEffect(() => {
    const fresh = writes.submissions.filter(r => r.sessionId !== sessionId && r.status === 'succeeded' && !foreignSuccesses.current.has(`${r.id}:${r.attemptId}`))
    for (const r of fresh) foreignSuccesses.current.add(`${r.id}:${r.attemptId}`)
    if (fresh.length && s) {
      fetchToken.current++
      fetchBusy.current = false
      setFetching(false)
      setError(null)
      setS(null)
    }
  })

  // Each attempt/status is reconciled once, and only into its originating Home session.
  useEffect(() => {
    for (const r of sessionWrites) {
      const key = `${r.id}:${r.attemptId}:${r.status}`
      if (handled.current.has(key)) continue
      handled.current.add(key)
      setS(p => {
        if (!p) return p
        if (r.status === 'failed') return { ...p, queue: p.queue.some(c => c.id === r.card.id) ? p.queue : [...p.queue, r.card] }
        if (r.status === 'pending') return { ...p, queue: p.queue.filter(c => c.id !== r.card.id) }
        if (r.status === 'succeeded') return { ...p, queue: p.queue.filter(c => c.id !== r.card.id), nextDueAt: earliest(p.nextDueAt, r.result?.dueAt ?? null) }
        return p
      })
    }
  })

  async function fetchBatch() {
    if (fetchBusy.current || writes.pending > 0) return
    fetchBusy.current = true
    const token = ++fetchToken.current
    setFetching(true)
    setError(null)
    try {
      const d = await api.due()
      if (!mounted.current || token !== fetchToken.current) return
      setS(prev => {
        const fresh = unseen(d.cards, prev?.seen ?? new Set())
        const queue = [...(prev?.queue ?? []), ...fresh]
        return {
          queue,
          seen: new Set([...(prev?.seen ?? []), ...fresh.map(c => c.id)]),
          total: prev?.total ?? d.dueCount,
          dueCount: d.dueCount,
          nextDueAt: earliest(d.nextDueAt, prev?.nextDueAt),
          exhausted: fresh.length === 0,
        }
      })
    } catch (e) {
      if (mounted.current && token === fetchToken.current) setError(e)
    } finally {
      if (mounted.current && token === fetchToken.current) {
        fetchBusy.current = false
        setFetching(false)
      }
    }
  }

  // First batch on mount; afterwards prefetch when 5 or fewer remain and the server says more are due
  const needMore = !s || (!s.exhausted && shouldPrefetch(s.queue.length, s.dueCount))
  useEffect(() => {
    if (needMore && !fetchBusy.current && !error && writes.pending === 0) fetchBatch()
  })

  if (!s) {
    return (
      <main className="stage">
        {error ? <ErrorState variant="full" error={error} onRetry={fetchBatch} /> : <Loading variant="full" />}
      </main>
    )
  }

  const top = s.queue[0]
  if (!top) {
    if (error) return <main className="stage"><ErrorState variant="full" error={error} onRetry={fetchBatch} /></main>
    if (needMore || fetching || writes.pending > 0 || topics.isPending) return <main className="stage"><Loading variant="full" /></main>
    if (writes.failures.length) return <main className="stage"><p>Resolve the unsaved scheduled reviews to finish.</p></main>
    if (!reviewed.length && !topics.data) return <main className="stage"><ErrorState variant="full" error={topics.error} onRetry={() => { void topics.refetch() }} /></main>
    const brandNew = reviewed.length === 0 && topics.data?.every(t => t.cardCount === 0)
    return (
      <main className="stage">
        {brandNew ? (
          <div className="done">
            <h1>Let's put those brain cells to work.</h1>
            <p>Add your first cards, or connect an agent to make them for you.</p>
            <div className="done-actions">
              <button className="primary" onClick={() => navigate('/cards')}>Add cards</button>
              <button onClick={() => navigate('/settings/agents')}>Connect an agent</button>
            </div>
          </div>
        ) : (
          <div className="done">
            <h1>Brain Upgraded</h1>
            <p>{s.nextDueAt ? `Next card due ${untilLabel(s.nextDueAt)}.` : 'Nothing else is scheduled.'}</p>
            {reviewed.length > 0 && (
              <p className="done-stats">
                {reviewed.length} reviewed · {reviewed.filter(q => q >= 4).length} Good or Easy
              </p>
            )}
            <div className="done-actions"><button className="primary" onClick={() => navigate('/freestyle')}>Freestyle</button></div>
          </div>
        )}
      </main>
    )
  }

  const position = reviewed.length + 1
  return (
    <Stack
      key={top.id}
      queue={s.queue}
      topicName={id => topics.data?.find(t => t.id === id)?.name ?? ''}
      counter={`${position} / ${Math.max(s.total, position + s.queue.length - 1)}`}
      menuOpen={menuOpen}
      onGraded={quality => {
        const id = writes.submit(sessionId, top, quality)
        if (id) setS(p => p && { ...p, queue: p.queue.filter(c => c.id !== top.id) })
      }}
    />
  )
}

type StackProps = {
  queue: Card[]
  topicName: (id: string) => string
  counter: string
  menuOpen: boolean
  onGraded: (quality: number) => void
}

/** The top card plus two peeking behind. A new top resets interaction and cancels old timers. */
function Stack({ queue, topicName, counter, menuOpen, onGraded }: StackProps) {
  const [flipped, setFlipped] = useState(false)
  const [grade, setGrade] = useState<number | null>(null)
  const [dx, setDx] = useState(0)
  const [exit, setExit] = useState(0)
  const startX = useRef<number | null>(null)
  const startY = useRef(0)
  const verticalDrag = useRef(false)
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => { if (exitTimer.current) clearTimeout(exitTimer.current) }, [])
  useEffect(() => {
    if (menuOpen) { startX.current = null; setDx(0) }
  }, [menuOpen])
  const top = queue[0]
  // Swipe unlocks only after the card is flipped and a difficulty is picked. The review is sent on swipe.
  const canSwipe = flipped && grade != null

  function advance(dir: number) {
    if (!canSwipe || exitTimer.current || menuOpen) return
    setExit(dir)
    exitTimer.current = setTimeout(() => {
      exitTimer.current = null
      onGraded(grade!)
      setFlipped(false)
      setGrade(null)
      setDx(0)
      setExit(0)
    }, 260)
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (menuOpen || exit || (e.target as HTMLElement).closest('dialog, input, textarea, button, a')) return
      if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault()
        setFlipped(f => !f)
      }
      if (e.key === 'ArrowRight') advance(1)
      if (e.key === 'ArrowLeft') advance(-1)
      const g = GRADES[Number(e.key) - 1]
      if (flipped && g) setGrade(g.quality)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // Locked cards only budge a little (rubber band) so the drag still feels responsive
  const x = exit ? exit * window.innerWidth * 1.1 : canSwipe ? dx : dx / 8

  return (
    <>
      <main className="stage">
        <div className="stack">
          {queue.slice(0, 3).reverse().map((c, i, arr) => {
            const depth = arr.length - 1 - i
            const isTop = depth === 0
            return (
              <div
                key={c.id}
                className={`card ${isTop && flipped ? 'flipped' : ''}`}
                style={
                  isTop
                    ? { transform: `translateX(${x}px) rotate(${x / 18}deg)`, transition: startX.current != null ? 'none' : undefined }
                    : { transform: `translateY(${depth * 14}px) scale(${1 - depth * 0.05})` }
                }
                {...(isTop && {
                  role: 'button',
                  tabIndex: 0,
                  'aria-label': flipped ? 'Answer shown. Tap to show question' : 'Tap to reveal answer',
                  onPointerDown: e => {
                    if (menuOpen || exitTimer.current || !e.isPrimary || e.button !== 0) return
                    startX.current = e.clientX
                    startY.current = e.clientY
                    verticalDrag.current = false
                    e.currentTarget.setPointerCapture(e.pointerId)
                  },
                  onPointerMove: e => {
                    if (startX.current == null) return
                    const deltaX = e.clientX - startX.current
                    const deltaY = e.clientY - startY.current
                    if (Math.abs(deltaY) > 6 && Math.abs(deltaY) > Math.abs(deltaX)) {
                      verticalDrag.current = true
                    }
                    setDx(verticalDrag.current ? 0 : deltaX)
                  },
                  onPointerUp: e => {
                    if (startX.current == null) return
                    const deltaX = e.clientX - startX.current
                    const deltaY = e.clientY - startY.current
                    startX.current = null
                    setDx(0)
                    if (verticalDrag.current || Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) >= 6) return
                    if (canSwipe && Math.abs(deltaX) > 100) return advance(Math.sign(deltaX))
                    if (Math.abs(deltaX) < 6 && Math.abs(deltaY) < 6) setFlipped(f => !f)
                  },
                  onPointerCancel: () => {
                    startX.current = null
                    setDx(0)
                  },
                })}
              >
                <div className="card-inner">
                  <section className="face front">
                    <span className="topic">{topicName(c.topicId)}</span>
                    <div className="card-content">
                      <div className="card-content-inner"><p>{c.question}</p></div>
                    </div>
                    <span className="hint">Tap to reveal</span>
                  </section>
                  <section className="face back">
                    <span className="topic">{topicName(c.topicId)}</span>
                    <div className="card-content">
                      <div className="card-content-inner">
                        <p>{c.answer}</p>
                      </div>
                    </div>
                    {isTop && (
                      <span className="hint">
                        {grade == null ? 'Pick a difficulty' : `Marked ${GRADES.find(g => g.quality === grade)?.label}. Swipe for next`}
                      </span>
                    )}
                  </section>
                </div>
              </div>
            )
          })}
        </div>
        <div className="count">{counter}</div>
      </main>

      <footer className={`grades ${flipped ? 'show' : ''}`}>
        {GRADES.map(g => (
          <button
            key={g.quality}
            className={`grade q${g.quality} ${grade === g.quality ? 'selected' : ''}`}
            aria-pressed={grade === g.quality}
            tabIndex={flipped ? 0 : -1}
            onClick={() => setGrade(g.quality)}
          >
            {g.label}
            <small>{nextInterval(top, g.quality)}d</small>
          </button>
        ))}
      </footer>
    </>
  )
}
