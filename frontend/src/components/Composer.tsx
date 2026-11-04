import type { ReactNode, RefObject } from 'react'
import { Icon, SEND } from './ui'

type Props = {
  value: string
  onChange: (v: string) => void
  onSubmit: () => void
  canSend: boolean
  placeholder: string
  label: string
  sendLabel: string
  error?: string | null
  /** Line above the bar ("Renaming X ✕", "Answer for X ✕") */
  context?: ReactNode
  onEscape?: () => void
  maxLength?: number
  enterKeyHint?: 'send' | 'next'
  inputRef?: RefObject<HTMLInputElement | null>
}

/** Chat-style bar pinned to the bottom of list screens, for adding and renaming. */
export default function Composer(p: Props) {
  return (
    <form
      className="composer"
      onSubmit={e => {
        e.preventDefault()
        if (p.canSend) p.onSubmit()
      }}
    >
      {p.error ? <p className="composer-error" role="alert">{p.error}</p> : p.context}
      <div className="composer-bar">
        <input
          ref={p.inputRef}
          value={p.value}
          maxLength={p.maxLength}
          placeholder={p.placeholder}
          aria-label={p.label}
          aria-invalid={!!p.error}
          enterKeyHint={p.enterKeyHint ?? 'send'}
          onChange={e => p.onChange(e.target.value)}
          onKeyDown={e => e.key === 'Escape' && p.onEscape?.()}
        />
        <button type="submit" className="send" aria-label={p.sendLabel} disabled={!p.canSend}>
          <Icon>{SEND}</Icon>
        </button>
      </div>
    </form>
  )
}
