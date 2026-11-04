import { useRef, useState } from 'react'
import type { Topic } from './mock'
import SwipeRow from './SwipeRow'
import Loading, { LOADING } from './Loading'
import ErrorState, { LOAD_ERROR } from './ErrorState'
import { CLOSE, Icon, PENCIL, SEARCH_ICON, SEND, TRASH, useModal } from './ui'

type Props = {
  topics: Topic[]
  setTopics: (fn: (t: Topic[]) => Topic[]) => void
  onOpen: (topicId: string) => void
}

export default function Topics({ topics, setTopics, onOpen }: Props) {
  const [name, setName] = useState('')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<Topic | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<Topic | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const dialog = useModal(toDelete != null)

  // Search only earns its space once the list gets long
  const showSearch = !LOADING && !LOAD_ERROR && topics.length > 10 // count unknown until the list arrives
  const shown = topics.filter(t => t.name.toLowerCase().includes(query.trim().toLowerCase()))
  const trimmed = name.trim()
  // Backend enforces unique names per user, case-insensitive (Topic.nameKey). Renaming may change only the case.
  const duplicate = topics.some(t => t.id !== editing?.id && t.name.toLowerCase() === trimmed.toLowerCase())
  const canSend = trimmed !== '' && !duplicate && trimmed !== editing?.name

  function reset() {
    setEditing(null)
    setName('')
  }

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSend) return
    if (editing) setTopics(ts => ts.map(t => (t.id === editing.id ? { ...t, name: trimmed } : t)))
    else setTopics(ts => [{ id: crypto.randomUUID(), name: trimmed, isDefault: false, cardCount: 0, dueCount: 0 }, ...ts])
    reset()
  }

  function startRename(t: Topic) {
    setEditing(t)
    setName(t.name)
    input.current?.focus()
  }

  function confirmDelete() {
    if (!toDelete) return
    setTopics(ts => ts.filter(t => t.id !== toDelete.id))
    if (editing?.id === toDelete.id) reset()
    setToDelete(null)
  }

  return (
    <>
      <main className="screen">
        <header className="topbar">
          <h1>Topics</h1>
        </header>

        {showSearch && (
          <label className="search">
            <Icon>{SEARCH_ICON}</Icon>
            <input type="search" value={query} placeholder="Search topics" aria-label="Search topics" onChange={e => setQuery(e.target.value)} />
          </label>
        )}

        {LOAD_ERROR ? (
          <ErrorState variant="list" kind={LOAD_ERROR} />
        ) : LOADING ? (
          <Loading variant="list" />
        ) : (
          <>
            {shown.length === 0 && <p className="empty">No topics match "{query.trim()}"</p>}
            <ul className="list" hidden={shown.length === 0}>
              {shown.map(t => (
                <SwipeRow
                  key={t.id}
                  className={editing?.id === t.id ? 'editing' : ''}
                  open={openId === t.id}
                  onOpenChange={o => setOpenId(o ? t.id : null)}
                  // A tap closes whichever row is open; otherwise it opens the topic's cards
                  onTap={() => (openId ? setOpenId(null) : onOpen(t.id))}
                  actions={[
                    { label: `Rename ${t.name}`, icon: PENCIL, onClick: () => startRename(t) },
                    // The default topic can't be deleted (backend rule), so it only gets Rename
                    ...(t.isDefault ? [] : [{ label: `Delete ${t.name}`, icon: TRASH, danger: true, onClick: () => setToDelete(t) }]),
                  ]}
                >
                  <span>{t.name}</span>
                  <span className="row-count">
                    {t.cardCount} {t.cardCount === 1 ? 'card' : 'cards'}
                  </span>
                </SwipeRow>
              ))}
            </ul>
          </>
        )}
      </main>

      <form className="composer" onSubmit={submit}>
        {duplicate && <p className="composer-error" role="alert">"{trimmed}" already exists</p>}
        {editing && !duplicate && (
          <p className="composer-context">
            Renaming <strong>{editing.name}</strong>
            <button type="button" aria-label="Cancel rename" onClick={reset}>
              <Icon>{CLOSE}</Icon>
            </button>
          </p>
        )}
        <div className="composer-bar">
          <input
            ref={input}
            value={name}
            maxLength={100}
            placeholder="Add a topic…"
            aria-label={editing ? `New name for ${editing.name}` : 'New topic name'}
            aria-invalid={duplicate}
            enterKeyHint="send"
            onChange={e => setName(e.target.value)}
            onKeyDown={e => e.key === 'Escape' && editing && reset()}
          />
          <button type="submit" className="send" aria-label={editing ? 'Save new name' : 'Add topic'} disabled={!canSend}>
            <Icon>{SEND}</Icon>
          </button>
        </div>
      </form>

      {/* Native <dialog>: gives the backdrop, focus trap and Escape-to-close for free */}
      <dialog ref={dialog} className="sheet" onClose={() => setToDelete(null)}>
        {toDelete && toDelete.cardCount > 0 ? (
          <>
            <h2>Can't delete {toDelete.name}</h2>
            <p>
              It still has {toDelete.cardCount} {toDelete.cardCount === 1 ? 'card' : 'cards'}. Move or delete them first.
            </p>
            <div className="sheet-actions">
              <button onClick={() => setToDelete(null)}>OK</button>
            </div>
          </>
        ) : (
          toDelete && (
            <>
              <h2>Delete {toDelete.name}?</h2>
              <p>This can't be undone.</p>
              <div className="sheet-actions">
                <button onClick={() => setToDelete(null)}>Cancel</button>
                <button className="danger" onClick={confirmDelete}>Delete</button>
              </div>
            </>
          )
        )}
      </dialog>
    </>
  )
}
