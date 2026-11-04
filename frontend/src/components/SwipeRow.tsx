import { useRef, useState, type ReactNode } from 'react'
import { Icon } from './ui'

export type SwipeAction = { label: string; icon: ReactNode; danger?: boolean; onClick: () => void }

const ACTION_W = 64 // width of each revealed action button

type Props = {
  actions: SwipeAction[]
  open: boolean
  onOpenChange: (open: boolean) => void
  onTap?: () => void
  className?: string
  children: ReactNode
}

/** List row that slides left to reveal icon actions. The parent owns which row is open (one at a time). */
export default function SwipeRow({ actions, open, onOpenChange, onTap, className = '', children }: Props) {
  const [dx, setDx] = useState<number | null>(null)
  const startX = useRef<number | null>(null)
  const w = actions.length * ACTION_W
  const base = open ? -w : 0
  const x = dx == null ? base : Math.min(0, Math.max(-w - 24, base + dx))

  const end = () => {
    startX.current = null
    setDx(null)
  }

  return (
    <li className="swipe">
      <div className="swipe-actions">
        {actions.map(a => (
          <button
            key={a.label}
            className={`swipe-action ${a.danger ? 'danger' : ''}`}
            aria-label={a.label}
            tabIndex={open ? 0 : -1}
            onClick={() => {
              onOpenChange(false)
              a.onClick()
            }}
          >
            <Icon>{a.icon}</Icon>
          </button>
        ))}
      </div>
      <button
        className={`row ${className}`}
        style={{ transform: `translateX(${x}px)`, transition: dx == null ? undefined : 'none' }}
        onPointerDown={e => {
          startX.current = e.clientX
          e.currentTarget.setPointerCapture(e.pointerId)
        }}
        onPointerMove={e => startX.current != null && setDx(e.clientX - startX.current)}
        onPointerUp={() => {
          const d = dx ?? 0
          end()
          if (Math.abs(d) < 6) return open ? onOpenChange(false) : onTap?.()
          onOpenChange(x < -w / 2)
        }}
        onPointerCancel={end}
        onClick={e => e.detail === 0 && onTap?.()} // keyboard Enter/Space (pointer taps are handled above)
        // Keyboard: arrow left reveals the actions, arrow right / Escape hides them
        onKeyDown={e => {
          if (e.key === 'ArrowLeft') onOpenChange(true)
          if (e.key === 'ArrowRight' || e.key === 'Escape') onOpenChange(false)
        }}
      >
        {children}
      </button>
    </li>
  )
}
