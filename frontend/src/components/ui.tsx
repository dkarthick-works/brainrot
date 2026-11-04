import { useEffect, useRef, type ReactNode } from 'react'

// Lucide-style 24x24 stroke paths, rendered inside <Icon>
export const SEARCH = <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>
export const PENCIL = <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></>
export const TRASH = <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6" />
export const CLOSE = <path d="M18 6 6 18M6 6l12 12" />
export const SEND = <path d="M12 19V5M5 12l7-7 7 7" />
export const BACK = <path d="M19 12H5M12 19l-7-7 7-7" />
export const CHEVRON_RIGHT = <path d="m9 6 6 6-6 6" />
export const CHEVRON_DOWN = <path d="m6 9 6 6 6-6" />

export const Icon = ({ children, className = '' }: { children: ReactNode; className?: string }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" aria-hidden="true">{children}</svg>
)

/** Opens/closes a native <dialog> as a modal whenever `open` changes (backdrop, focus trap, Escape for free) */
export function useModal(open: boolean) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    if (open) ref.current?.showModal()
    else ref.current?.close()
  }, [open])
  return ref
}
