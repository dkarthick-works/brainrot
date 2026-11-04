import { useEffect, useRef, useState } from 'react'
import { topics, type Card } from './mock'
import Loading, { LOADING } from './Loading'
import ErrorState, { LOAD_ERROR } from './ErrorState'

// Quality values match Sm2.java: Again=1, Hard=3, Good=4, Easy=5
const GRADES = [
  { label: 'Again', quality: 1 },
  { label: 'Hard', quality: 3 },
  { label: 'Good', quality: 4 },
  { label: 'Easy', quality: 5 },
]

// Mirrors Sm2.java interval logic, used only for the "1d / 6d" hints
function nextInterval(c: Card, q: number) {
  if (q < 3 || c.repetitions === 0) return 1
  if (c.repetitions === 1) return 6
  return Math.round(c.interval * c.easeFactor)
}

// "in 40m", "in 3h", "tomorrow", "in 5 days"
function untilLabel(iso: string) {
  const mins = (new Date(iso).getTime() - Date.now()) / 6e4
  if (mins < 60) return `in ${Math.max(1, Math.round(mins))}m`
  if (mins < 60 * 24) return `in ${Math.round(mins / 60)}h`
  const days = Math.round(mins / 1440)
  return days <= 1 ? 'tomorrow' : `in ${days} days`
}

type Props = { menuOpen: boolean; cards: Card[]; onGo: (screen: 'Cards' | 'AgentAccess') => void }

export default function Play({ menuOpen, cards, onGo }: Props) {
  // GET /api/cards/due: cards whose dueAt has passed, soonest first
  const [due] = useState(() => cards.filter(c => new Date(c.dueAt).getTime() <= Date.now()))
  const [queue, setQueue] = useState(due)
  const [reviewed, setReviewed] = useState<number[]>([]) // qualities graded this session
  const [flipped, setFlipped] = useState(false)
  const [grade, setGrade] = useState<number | null>(null)
  const [dx, setDx] = useState(0)
  const [exit, setExit] = useState(0)
  const startX = useRef<number | null>(null)
  const top = queue[0]
  const nextDue = cards
    .map(c => c.dueAt)
    .filter(d => new Date(d).getTime() > Date.now())
    .sort()[0]
  // Swipe unlocks only after the card is flipped and a difficulty is picked.
  const canSwipe = flipped && grade != null

  function advance(dir: number) {
    if (!canSwipe) return
    setExit(dir)
    setTimeout(() => {
      setQueue(q => q.slice(1))
      setReviewed(r => [...r, grade!])
      setFlipped(false)
      setGrade(null)
      setDx(0)
      setExit(0)
    }, 260)
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (menuOpen || !top || exit) return
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); setFlipped(f => !f) }
      if (e.key === 'ArrowRight') advance(1)
      if (e.key === 'ArrowLeft') advance(-1)
      const g = GRADES[Number(e.key) - 1]
      if (flipped && g) setGrade(g.quality)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // Locked cards only budge a little (rubber band) so the drag still feels responsive.
  const x = exit ? exit * window.innerWidth * 1.1 : canSwipe ? dx : dx / 8

  return (
    <>
      <main className="stage">
        {LOAD_ERROR ? (
          <ErrorState variant="full" kind={LOAD_ERROR} />
        ) : LOADING ? (
          <Loading variant="full" />
        ) : top ? (
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
                      ? {
                          transform: `translateX(${x}px) rotate(${x / 18}deg)`,
                          transition: startX.current != null ? 'none' : undefined,
                        }
                      : { transform: `translateY(${depth * 14}px) scale(${1 - depth * 0.05})` }
                  }
                  {...(isTop && {
                    role: 'button',
                    tabIndex: 0,
                    'aria-label': flipped ? 'Answer shown. Tap to show question' : 'Tap to reveal answer',
                    onPointerDown: e => {
                      startX.current = e.clientX
                      e.currentTarget.setPointerCapture(e.pointerId)
                    },
                    onPointerMove: e => startX.current != null && setDx(e.clientX - startX.current),
                    onPointerUp: () => {
                      startX.current = null
                      if (canSwipe && Math.abs(dx) > 100) return advance(Math.sign(dx))
                      if (Math.abs(dx) < 6) setFlipped(f => !f)
                      setDx(0)
                    },
                  })}
                >
                  <div className="card-inner">
                    <section className="face front">
                      <span className="topic">{topics.find(t => t.id === c.topicId)?.name}</span>
                      <p>{c.question}</p>
                      <span className="hint">Tap to reveal</span>
                    </section>
                    <section className="face back">
                      <span className="topic">{topics.find(t => t.id === c.topicId)?.name}</span>
                      <p className="q-small">{c.question}</p>
                      <p>{c.answer}</p>
                      {isTop && (
                        <span className="hint">
                          {grade == null
                            ? 'Pick a difficulty'
                            : `Marked ${GRADES.find(g => g.quality === grade)?.label}. Swipe for next`}
                        </span>
                      )}
                    </section>
                  </div>
                </div>
              )
            })}
          </div>
        ) : null}
        {!LOADING && !LOAD_ERROR && top && <div className="count">{due.length - queue.length + 1} / {due.length}</div>}
        {!LOADING && !LOAD_ERROR && !top && (
          cards.length === 0 ? (
            // Brand-new user: nothing to study yet
            <div className="done">
              <h1>Let's put those brain cells to work.</h1>
              <p>Add your first cards, or connect an agent to make them for you.</p>
              <div className="done-actions">
                <button className="primary" onClick={() => onGo('Cards')}>Add cards</button>
                <button onClick={() => onGo('AgentAccess')}>Connect an agent</button>
              </div>
            </div>
          ) : (
            // Finished today's cards (or nothing was due)
            <div className="done">
              <h1>Brain Upgraded</h1>
              <p>{nextDue ? `Next card due ${untilLabel(nextDue)}.` : 'Nothing else is scheduled.'}</p>
              {reviewed.length > 0 && (
                <p className="done-stats">
                  {reviewed.length} reviewed · {reviewed.filter(q => q >= 4).length} Good or Easy
                </p>
              )}
              <button className="link" onClick={() => { setQueue(due); setReviewed([]) }}>Replay mock deck (prototype only)</button>
            </div>
          )
        )}
      </main>

      {!LOADING && !LOAD_ERROR && top && (
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
      )}
    </>
  )
}
