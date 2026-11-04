import { useRef, useState } from 'react'
import type { Card, Topic } from './mock'
import SwipeRow from './SwipeRow'
import Loading, { LOADING } from './Loading'
import ErrorState, { LOAD_ERROR } from './ErrorState'
import EditCard from './EditCard'
import { CLOSE, Icon, PENCIL, SEARCH_ICON, SEND, TRASH, useModal } from './ui'

type Props = {
  cards: Card[]
  setCards: (fn: (c: Card[]) => Card[]) => void
  topics: Topic[]
  topicId: string | null // null = All
  setTopicId: (id: string | null) => void
}

// "due now", "in 3d", "in 2mo"
function dueLabel(dueAt: string) {
  const days = Math.ceil((new Date(dueAt).getTime() - Date.now()) / 864e5)
  if (days <= 0) return 'due now'
  return days < 30 ? `in ${days}d` : `in ${Math.round(days / 30)}mo`
}

// Backend dedupes questions per topic (Card.questionKey); approximated here as trimmed + lowercase
const sameQuestion = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

export default function Cards({ cards, setCards, topics, topicId, setTopicId }: Props) {
  const [query, setQuery] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<Card | null>(null)
  const [editing, setEditing] = useState<Card | null>(null)
  // Two-step add: first the question, then the answer
  const [text, setText] = useState('')
  const [draftQ, setDraftQ] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const deleteDialog = useModal(toDelete != null)
  const editDialog = useModal(editing != null)

  const topicName = (id: string) => topics.find(t => t.id === id)?.name ?? ''
  const defaultTopic = topics.find(t => t.isDefault)!
  const targetTopic = topicId ?? defaultTopic.id // "All" adds to the default topic
  const q = query.trim().toLowerCase()
  const shown = cards.filter(
    c => (!topicId || c.topicId === topicId) && (!q || c.question.toLowerCase().includes(q) || c.answer.toLowerCase().includes(q)),
  )

  const trimmed = text.trim()
  const duplicate = draftQ == null && cards.some(c => c.topicId === targetTopic && sameQuestion(c.question, trimmed))
  const canSend = trimmed !== '' && !duplicate

  function send(e: React.FormEvent) {
    e.preventDefault()
    if (!canSend) return
    if (draftQ == null) {
      setDraftQ(trimmed)
      setText('')
      return
    }
    const now = new Date().toISOString()
    setCards(cs => [
      {
        id: crypto.randomUUID(), topicId: targetTopic, question: draftQ, answer: trimmed,
        easeFactor: 2.5, interval: 0, repetitions: 0, dueAt: now, lastReviewedAt: null, createdAt: now, updatedAt: now,
      },
      ...cs,
    ])
    setDraftQ(null)
    setText('')
  }

  function cancelDraft() {
    setText(draftQ ?? '')
    setDraftQ(null)
    input.current?.focus()
  }

  return (
    <>
      <main className="screen">
        <header className="topbar">
          <h1>Cards</h1>
        </header>

        <div className="chips" role="tablist" aria-label="Filter by topic">
          {[{ id: null, name: 'All' }, ...topics].map(t => (
            <button
              key={t.id ?? 'all'}
              role="tab"
              aria-selected={topicId === t.id}
              className={`chip ${topicId === t.id ? 'selected' : ''}`}
              onClick={() => setTopicId(t.id)}
            >
              {t.name}
            </button>
          ))}
        </div>

        <label className="search">
          <Icon>{SEARCH_ICON}</Icon>
          <input type="search" value={query} placeholder="Search cards" aria-label="Search cards" onChange={e => setQuery(e.target.value)} />
        </label>

        {LOAD_ERROR ? (
          <ErrorState variant="list" kind={LOAD_ERROR} />
        ) : LOADING ? (
          <Loading variant="list" />
        ) : (
          <>
            {shown.length === 0 && (
              <p className="empty">{q ? `No cards match "${query.trim()}"` : 'No cards here yet. Add one below.'}</p>
            )}
            <ul className="list" hidden={shown.length === 0}>
              {shown.map(c => (
                <SwipeRow
                  key={c.id}
                  className="card-row"
                  open={openId === c.id}
                  onOpenChange={o => setOpenId(o ? c.id : null)}
                  onTap={() => (openId ? setOpenId(null) : setEditing(c))}
                  actions={[
                    { label: 'Edit card', icon: PENCIL, onClick: () => setEditing(c) },
                    { label: 'Delete card', icon: TRASH, danger: true, onClick: () => setToDelete(c) },
                  ]}
                >
                  <span className="card-text">
                    <span className="card-q">{c.question}</span>
                    <span className="card-a">{c.answer}</span>
                    {!topicId && <span className="card-topic">{topicName(c.topicId)}</span>}
                  </span>
                  <span className={`due ${dueLabel(c.dueAt) === 'due now' ? 'now' : ''}`}>{dueLabel(c.dueAt)}</span>
                </SwipeRow>
              ))}
            </ul>
          </>
        )}
      </main>

      <form className="composer" onSubmit={send}>
        {duplicate && <p className="composer-error" role="alert">Already in {topicName(targetTopic)}</p>}
        {draftQ != null && (
          <p className="composer-context">
            <span className="ellipsis">Answer for <strong>{draftQ}</strong></span>
            <button type="button" aria-label="Back to question" onClick={cancelDraft}>
              <Icon>{CLOSE}</Icon>
            </button>
          </p>
        )}
        <div className="composer-bar">
          <input
            ref={input}
            value={text}
            placeholder={draftQ == null ? `New card in ${topicName(targetTopic)}…` : 'Type the answer…'}
            aria-label={draftQ == null ? 'New card question' : 'New card answer'}
            aria-invalid={duplicate}
            enterKeyHint={draftQ == null ? 'next' : 'send'}
            onChange={e => setText(e.target.value)}
            onKeyDown={e => e.key === 'Escape' && draftQ != null && cancelDraft()}
          />
          <button type="submit" className="send" aria-label={draftQ == null ? 'Next: answer' : 'Add card'} disabled={!canSend}>
            <Icon>{SEND}</Icon>
          </button>
        </div>
      </form>

      <dialog ref={deleteDialog} className="sheet" onClose={() => setToDelete(null)}>
        {toDelete && (
          <>
            <h2>Delete this card?</h2>
            <p>"{toDelete.question}" and its review history will be gone. This can't be undone.</p>
            <div className="sheet-actions">
              <button onClick={() => setToDelete(null)}>Cancel</button>
              <button
                className="danger"
                onClick={() => {
                  setCards(cs => cs.filter(c => c.id !== toDelete.id))
                  setToDelete(null)
                }}
              >
                Delete
              </button>
            </div>
          </>
        )}
      </dialog>

      <dialog ref={editDialog} className="sheet edit" onClose={() => setEditing(null)}>
        {editing && (
          <EditCard
            key={editing.id}
            card={editing}
            topics={topics}
            onCancel={() => setEditing(null)}
            onSave={patch => {
              setCards(cs => cs.map(c => (c.id === editing.id ? { ...c, ...patch, updatedAt: new Date().toISOString() } : c)))
              setEditing(null)
            }}
          />
        )}
      </dialog>
    </>
  )
}
