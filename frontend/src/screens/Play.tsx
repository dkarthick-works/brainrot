import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, topicsQuery } from '../api'
import ErrorState from '../components/ErrorState'
import Loading from '../components/Loading'
import { showSaveError } from '../components/SaveError'
import { earliest, GRADES, nextInterval, shouldPrefetch, unseen, untilLabel } from '../logic'
import type { Card } from '../types'

type Session = {
  queue: Card[] // cards left to grade, top first
  seen: Set<string> // every id queued this session, so refetched batches don't repeat them
  total: number // dueCount at session start, for "position / total"
  dueCount: number // from the latest /cards/due response
  nextDueAt: string | null // soonest future dueAt, from the server and from cards graded here
  reviewed: number[] // qualities graded this session
  exhausted: boolean // the last fetch added nothing new
}

export default function Play({ menuOpen }: { menuOpen: boolean }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const topics = useQuery(topicsQuery)
  const [s, setS] = useState<Session | null>(null)
  const [fetching, setFetching] = useState(false)
  const [error, setError] = useState<unknown>(null)
  // Reviews still being sent. Fetching waits for them, so a batch never refills with cards already graded here.
  const [inflight, setInflight] = useState(0)

  async function fetchBatch() {
    setFetching(true)
    setError(null)
    try {
      const d = await api.due()
      setS(prev => {
        const fresh = unseen(d.cards, prev?.seen ?? new Set())
        const queue = [...(prev?.queue ?? []), ...fresh]
        return {
          queue,
          seen: new Set([...(prev?.seen ?? []), ...fresh.map(c => c.id)]),
          total: prev?.total ?? d.dueCount,
          dueCount: d.dueCount,
          nextDueAt: earliest(d.nextDueAt, prev?.nextDueAt),
          reviewed: prev?.reviewed ?? [],
          exhausted: fresh.length === 0,
        }
      })
    } catch (e) {
      setError(e)
    } finally {
      setFetching(false)
    }
  }

  // First batch on mount; afterwards prefetch when 5 or fewer remain and the server says more are due
  const needMore = !s || (!s.exhausted && shouldPrefetch(s.queue.length, s.dueCount))
  useEffect(() => {
    if (needMore && !fetching && !error && inflight === 0) fetchBatch()
  })

  function review(card: Card, quality: number) {
    setInflight(n => n + 1)
    api.review(card.id, quality).finally(() => setInflight(n => n - 1)).then(
      updated => {
        setS(p => p && { ...p, nextDueAt: earliest(p.nextDueAt, updated.dueAt) })
        qc.invalidateQueries({ queryKey: ['cards'] })
        qc.invalidateQueries({ queryKey: ['topics'] })
      },
      () => {
        // Undo: the card comes back to the end of the stack, ungraded
        setS(p => p && { ...p, queue: [...p.queue, card], reviewed: p.reviewed.slice(0, -1) })
        showSaveError(() => {
          setS(p => p && { ...p, queue: p.queue.filter(c => c.id !== card.id), reviewed: [...p.reviewed, quality] })
          review(card, quality)
        })
      },
    )
  }

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
    if (needMore || fetching || topics.isPending) return <main className="stage"><Loading variant="full" /></main>
    const brandNew = s.reviewed.length === 0 && topics.data?.every(t => t.cardCount === 0)
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
            {s.reviewed.length > 0 && (
              <p className="done-stats">
                {s.reviewed.length} reviewed · {s.reviewed.filter(q => q >= 4).length} Good or Easy
              </p>
            )}
          </div>
        )}
      </main>
    )
  }

  const position = s.reviewed.length + 1
  return (
    <Stack
      queue={s.queue}
      topicName={id => topics.data?.find(t => t.id === id)?.name ?? ''}
      counter={`${position} / ${Math.max(s.total, position + s.queue.length - 1)}`}
      menuOpen={menuOpen}
      onGraded={quality => {
        setS(p => p && { ...p, queue: p.queue.slice(1), reviewed: [...p.reviewed, quality] })
        review(top, quality)
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

/** The top card plus two peeking behind. Stays mounted across cards so the next one slides forward. */
function Stack({ queue, topicName, counter, menuOpen, onGraded }: StackProps) {
  const [flipped, setFlipped] = useState(false)
  const [grade, setGrade] = useState<number | null>(null)
  const [dx, setDx] = useState(0)
  const [exit, setExit] = useState(0)
  const startX = useRef<number | null>(null)
  const startY = useRef(0)
  const verticalDrag = useRef(false)
  const top = queue[0]
  // Swipe unlocks only after the card is flipped and a difficulty is picked. The review is sent on swipe.
  const canSwipe = flipped && grade != null

  function advance(dir: number) {
    if (!canSwipe || exit) return
    setExit(dir)
    setTimeout(() => {
      onGraded(grade!)
      setFlipped(false)
      setGrade(null)
      setDx(0)
      setExit(0)
    }, 260)
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (menuOpen || exit || (e.target as HTMLElement).closest('dialog, input, textarea')) return
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
                    if (!e.isPrimary || e.button !== 0) return
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
                        <p className="q-small">{c.question}</p>
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
