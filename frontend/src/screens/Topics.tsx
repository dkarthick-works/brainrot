import { useQuery, type QueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, topicsQuery } from '../api'
import Composer from '../components/Composer'
import ErrorState from '../components/ErrorState'
import Loading from '../components/Loading'
import { useSave } from '../components/SaveError'
import SwipeRow from '../components/SwipeRow'
import { CLOSE, Icon, PENCIL, SEARCH, TRASH, useModal } from '../components/ui'
import { plural } from '../logic'
import type { Topic } from '../types'

const TOPICS = ['topics']
const tempId = () => 'tmp-' + crypto.randomUUID()
const editTopics = (qc: QueryClient, fn: (ts: Topic[]) => Topic[]) => qc.setQueryData<Topic[]>(TOPICS, ts => ts && fn(ts))

export default function Topics() {
  const navigate = useNavigate()
  const topics = useQuery(topicsQuery)
  const [name, setName] = useState('')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<Topic | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<Topic | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const dialog = useModal(toDelete != null)

  const create = useSave({
    fn: (t: Topic) => api.createTopic(t.name),
    touches: [TOPICS],
    apply: (t, qc) => editTopics(qc, ts => [t, ...ts]),
  })
  const rename = useSave({
    fn: (v: { id: string; name: string }) => api.renameTopic(v.id, v.name),
    touches: [TOPICS],
    apply: (v, qc) => editTopics(qc, ts => ts.map(t => (t.id === v.id ? { ...t, name: v.name } : t))),
  })
  const remove = useSave({
    fn: (id: string) => api.deleteTopic(id),
    touches: [TOPICS],
    apply: (id, qc) => editTopics(qc, ts => ts.filter(t => t.id !== id)),
  })

  const list = topics.data ?? []
  // Search only earns its space once the list gets long
  const showSearch = list.length > 10
  const q = query.trim().toLowerCase()
  const shown = showSearch ? list.filter(t => t.name.toLowerCase().includes(q)) : list
  const trimmed = name.trim()
  // Names are unique per user, ignoring case (Topic.nameKey). A rename may change only the case.
  const duplicate = list.some(t => t.id !== editing?.id && t.name.toLowerCase() === trimmed.toLowerCase())
  const canSend = trimmed !== '' && !duplicate && trimmed !== editing?.name

  function reset() {
    setEditing(null)
    setName('')
  }

  function submit() {
    if (editing) rename({ id: editing.id, name: trimmed })
    else create({ id: tempId(), name: trimmed, isDefault: false, cardCount: 0, dueCount: 0 })
    reset()
  }

  function startRename(t: Topic) {
    setEditing(t)
    setName(t.name)
    input.current?.focus()
  }

  return (
    <>
      <main className="screen">
        <header className="topbar"><h1>Topics</h1></header>

        {showSearch && (
          <label className="search">
            <Icon>{SEARCH}</Icon>
            <input type="search" value={query} placeholder="Search topics" aria-label="Search topics" onChange={e => setQuery(e.target.value)} />
          </label>
        )}

        {topics.isError ? (
          <ErrorState variant="list" error={topics.error} onRetry={() => topics.refetch()} />
        ) : topics.isPending ? (
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
                  onTap={() => (openId ? setOpenId(null) : !t.id.startsWith('tmp-') && navigate(`/cards?topic=${t.id}`))}
                  actions={[
                    { label: `Rename ${t.name}`, icon: PENCIL, onClick: () => startRename(t) },
                    // The default topic can't be deleted (backend rule), so it only gets Rename
                    ...(t.isDefault ? [] : [{ label: `Delete ${t.name}`, icon: TRASH, danger: true, onClick: () => setToDelete(t) }]),
                  ]}
                >
                  <span>{t.name}</span>
                  <span className="row-count">{plural(t.cardCount, 'card')}</span>
                </SwipeRow>
              ))}
            </ul>
          </>
        )}
      </main>

      <Composer
        inputRef={input}
        value={name}
        onChange={setName}
        onSubmit={submit}
        canSend={canSend}
        maxLength={100}
        placeholder="Add a topic…"
        label={editing ? `New name for ${editing.name}` : 'New topic name'}
        sendLabel={editing ? 'Save new name' : 'Add topic'}
        error={duplicate ? `"${trimmed}" already exists` : null}
        onEscape={editing ? reset : undefined}
        context={
          editing && (
            <p className="composer-context">
              Renaming <strong>{editing.name}</strong>
              <button type="button" aria-label="Cancel rename" onClick={reset}>
                <Icon>{CLOSE}</Icon>
              </button>
            </p>
          )
        }
      />

      <dialog ref={dialog} className="sheet" onClose={() => setToDelete(null)}>
        {toDelete && toDelete.cardCount > 0 ? (
          <>
            <h2>Can't delete {toDelete.name}</h2>
            <p>It still has {plural(toDelete.cardCount, 'card')}. Move or delete them first.</p>
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
                <button
                  className="danger"
                  onClick={() => {
                    remove(toDelete.id)
                    if (editing?.id === toDelete.id) reset()
                    setToDelete(null)
                  }}
                >
                  Delete
                </button>
              </div>
            </>
          )
        )}
      </dialog>
    </>
  )
}
