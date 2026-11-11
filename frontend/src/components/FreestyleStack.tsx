import { useEffect, useRef, useState } from 'react'
import type { PointerEvent } from 'react'
import type { Card } from '../types'

type Props = {
  queue: readonly Card[]
  topicName: (id: string) => string | undefined
  counter: number
  suspended: boolean
  onAdvance: (cardId: string) => void
}

/** Keying the interaction owner discards timers, face and scroll on every new top card. */
export default function FreestyleStack(props: Props) {
  return props.queue[0] ? <StackCard key={props.queue[0].id} {...props} /> : null
}

function StackCard({ queue, topicName, counter, suspended, onAdvance }: Props) {
  const [flipped, setFlipped] = useState(false)
  const [dx, setDx] = useState(0)
  const [exit, setExit] = useState(0)
  const pointer = useRef<{ id: number; x: number; y: number; moved: boolean; axis: 'x' | 'y' | null; element: HTMLElement } | null>(null)
  const committed = useRef(false)
  const delivered = useRef(false)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const advance = useRef(onAdvance)
  advance.current = onAdvance
  const id = queue[0].id

  function release() {
    const p = pointer.current
    pointer.current = null
    if (p?.element.hasPointerCapture?.(p.id)) p.element.releasePointerCapture(p.id)
  }
  function cancel() { release(); setDx(0) }
  useEffect(() => {
    if (suspended) cancel()
  }, [suspended])
  useEffect(() => () => { clearTimeout(timer.current); release() }, [])

  function finish() {
    if (!committed.current || delivered.current) return
    delivered.current = true
    clearTimeout(timer.current)
    advance.current(id)
  }
  function dismiss(direction: number) {
    if (suspended || committed.current) return
    committed.current = true
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      finish()
      return
    }
    setExit(direction)
    timer.current = setTimeout(finish, 300) // bounded fallback if transitionend is lost
  }
  function flip() {
    if (!suspended && !committed.current) setFlipped(f => !f)
  }
  function motion(e: PointerEvent<HTMLElement>) {
    const p = pointer.current
    if (!p || p.id !== e.pointerId) return null
    const x = e.clientX - p.x
    const y = e.clientY - p.y
    if (Math.abs(x) >= 6 || Math.abs(y) >= 6) p.moved = true
    if (!p.axis && p.moved) p.axis = Math.abs(y) > Math.abs(x) ? 'y' : 'x'
    return { p, x, y }
  }
  const x = exit ? exit * window.innerWidth * 1.1 : dx

  return <>
    <div className="stack freestyle-stack">
      {queue.slice(0, 3).map((card, depth) => {
        const top = depth === 0
        const topic = topicName(card.topicId)
        return <div key={card.id}
          className={`card ${top && flipped ? 'flipped' : ''}`}
          aria-hidden={!top || undefined}
          style={{ zIndex: 3 - depth, transform: top ? `translateX(${x}px) rotate(${x / 18}deg)` : `translateY(${depth * 14}px) scale(${1 - depth * .05})`, transition: top && pointer.current ? 'none' : undefined }}
          {...(top ? {
            role: 'button', tabIndex: suspended ? -1 : 0,
            'aria-disabled': suspended,
            'aria-label': flipped ? 'Answer shown. Tap to show question' : 'Tap to reveal answer',
            onKeyDown: (e: React.KeyboardEvent<HTMLDivElement>) => {
              if (e.target !== e.currentTarget || e.repeat) return
              if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flip() }
            },
            onPointerDown: (e: PointerEvent<HTMLDivElement>) => {
              if (suspended || committed.current || pointer.current || !e.isPrimary || e.button !== 0) return
              pointer.current = { id: e.pointerId, x: e.clientX, y: e.clientY, moved: false, axis: null, element: e.currentTarget }
              e.currentTarget.setPointerCapture?.(e.pointerId)
            },
            onPointerMove: (e: PointerEvent<HTMLDivElement>) => {
              const m = motion(e)
              if (m) setDx(m.p.axis === 'x' ? m.x : 0)
            },
            onPointerUp: (e: PointerEvent<HTMLDivElement>) => {
              const m = motion(e)
              if (!m) return
              cancel()
              if (suspended || committed.current || m.p.axis === 'y') return
              if (Math.abs(m.x) > 100) dismiss(Math.sign(m.x))
              else if (!m.p.moved) flip()
            },
            onPointerCancel: (e: PointerEvent<HTMLDivElement>) => { if (pointer.current?.id === e.pointerId) cancel() },
            onLostPointerCapture: (e: PointerEvent<HTMLDivElement>) => { if (pointer.current?.id === e.pointerId) cancel() },
            onTransitionEnd: (e: React.TransitionEvent<HTMLDivElement>) => {
              if (e.target === e.currentTarget && e.propertyName === 'transform') finish()
            },
          } : {})}>
          <div className="card-inner">
            <section className="face front" aria-hidden={!top || flipped}>
              {topic && <span className="topic">{topic}</span>}
              <div className="card-content"><div className="card-content-inner"><p>{card.question}</p></div></div>
              <span className="hint">Tap to reveal · Swipe to continue</span>
            </section>
            <section className="face back" aria-hidden={!top || !flipped}>
              {topic && <span className="topic">{topic}</span>}
              <div className="card-content"><div className="card-content-inner"><p className="q-small">{card.question}</p><p>{card.answer}</p></div></div>
              <span className="hint">Tap for question · Swipe to continue</span>
            </section>
          </div>
        </div>
      })}
    </div>
    <div className="count" role="status" aria-live="polite">Card {counter}</div>
  </>
}
