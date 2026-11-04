import { useInfiniteQuery, useQuery, type InfiniteData, type QueryClient, type QueryKey } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { api, ApiError, topicsQuery } from '../api'
import Composer from '../components/Composer'
import ErrorState from '../components/ErrorState'
import Loading from '../components/Loading'
import { useSave } from '../components/SaveError'
import SwipeRow from '../components/SwipeRow'
import { CLOSE, Icon, PENCIL, SEARCH, TRASH, useModal } from '../components/ui'
import { dueLabel } from '../logic'
import type { Card, Page } from '../types'
import EditCard, { type CardPatch } from './EditCard'

const CARDS = ['cards']
const TOPICS = ['topics']
type Pages = InfiniteData<Page, string | null>

/** Applies `fn` to the cards of every cached Cards list (each topic/search combination). Key: ['cards', topicId, q]. */
function editCards(qc: QueryClient, fn: (cards: Card[], key: QueryKey, firstPage: boolean) => Card[]) {
  for (const [key, d] of qc.getQueriesData<Pages>({ queryKey: CARDS })) {
    if (d) qc.setQueryData<Pages>(key, { ...d, pages: d.pages.map((p, i) => ({ ...p, cards: fn(p.cards, key, i === 0) })) })
  }
}

// Backend dedupes questions per topic (Card.questionKey: trimmed, spaces collapsed, lowercase)
const questionKey = (q: string) => q.trim().replace(/\s+/g, ' ').toLowerCase()

export default function Cards() {
  const [params, setParams] = useSearchParams()
  const topicId = params.get('topic') // null = All
  const topics = useQuery(topicsQuery)
  const [query, setQuery] = useState('')
  const [q, setQ] = useState('') // debounced search text sent to the server
  useEffect(() => {
    const t = setTimeout(() => setQ(query.trim()), 250)
    return () => clearTimeout(t)
  }, [query])

  const cards = useInfiniteQuery({
    queryKey: ['cards', topicId, q],
    queryFn: ({ pageParam }) => api.cards({ topicId, q, after: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: p => p.nextCursor,
    // While a new search loads, keep showing the previous results for the same topic (no loader per keystroke)
    placeholderData: (prev, prevQuery) => (prevQuery?.queryKey[1] === topicId ? prev : undefined),
  })

  const [openId, setOpenId] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<Card | null>(null)
  const [editing, setEditing] = useState<Card | null>(null)
  // Two-step add: first the question, then the answer
  const [text, setText] = useState('')
  const [draftQ, setDraftQ] = useState<string | null>(null)
  const [serverDup, setServerDup] = useState<string | null>(null) // a question the server refused as a duplicate
  const input = useRef<HTMLInputElement>(null)
  const deleteDialog = useModal(toDelete != null)
  const editDialog = useModal(editing != null)

  const topicList = topics.data ?? []
  const topicName = (id: string | null) => topicList.find(t => t.id === id)?.name ?? ''
  const defaultTopic = topicList.find(t => t.isDefault)
  const target = topicId ?? defaultTopic?.id ?? null // "All" adds to the default topic (null: server picks/creates it)
  const targetName = topicId ? topicName(topicId) : (defaultTopic?.name ?? 'Default')
  const shown = cards.data?.pages.flatMap(p => p.cards) ?? []

  const add = useSave({
    fn: async (c: Card) => {
      const r = await api.createCard(topicId, c.question, c.answer)
      if (r.skipped.length) throw new ApiError(409, 'duplicate')
      return r
    },
    touches: [CARDS, TOPICS],
    apply: (c, qc) => editCards(qc, (cs, key, first) => (first && (key[1] == null || key[1] === c.topicId) && !key[2] ? [c, ...cs] : cs)),
    onError: (e, c) => {
      if (e.status !== 409) return false
      // Specific error: put the question back in the bar with "Already in Topic" above it
      setDraftQ(null)
      setText(c.question)
      setServerDup(`${c.topicId}|${questionKey(c.question)}`)
      return true
    },
  })
  const update = useSave({
    fn: (v: { id: string; patch: CardPatch }) => api.updateCard(v.id, v.patch),
    touches: [CARDS, TOPICS],
    apply: ({ id, patch }, qc) =>
      editCards(qc, (cs, key) =>
        cs.flatMap(c => (c.id !== id ? [c] : key[1] != null && key[1] !== patch.topicId ? [] : [{ ...c, ...patch }])),
      ),
  })
  const remove = useSave({
    fn: (id: string) => api.deleteCard(id),
    touches: [CARDS, TOPICS],
    apply: (id, qc) => editCards(qc, cs => cs.filter(c => c.id !== id)),
  })

  const trimmed = text.trim()
  const duplicate =
    draftQ == null &&
    trimmed !== '' &&
    (`${target ?? ''}|${questionKey(trimmed)}` === serverDup || shown.some(c => c.topicId === target && questionKey(c.question) === questionKey(trimmed)))
  const canSend = trimmed !== '' && !duplicate

  function send() {
    if (draftQ == null) {
      setDraftQ(trimmed)
      setText('')
      return
    }
    const now = new Date().toISOString()
    add({
      id: 'tmp-' + crypto.randomUUID(), topicId: target ?? '', question: draftQ, answer: trimmed,
      easeFactor: 2.5, interval: 0, repetitions: 0, dueAt: now, lastReviewedAt: null, createdAt: now, updatedAt: now,
    })
    setDraftQ(null)
    setText('')
  }

  function backToQuestion() {
    setText(draftQ ?? '')
    setDraftQ(null)
    input.current?.focus()
  }

  // Infinite scroll: load the next 50 when the sentinel row scrolls into view
  const sentinel = useRef<HTMLLIElement>(null)
  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver(([e]) => e.isIntersecting && cards.fetchNextPage())
    io.observe(el)
    return () => io.disconnect()
  })

  return (
    <>
      <main className="screen">
        <header className="topbar"><h1>Cards</h1></header>

        <div className="chips" role="tablist" aria-label="Filter by topic">
          {[{ id: null, name: 'All' }, ...topicList].map(t => (
            <button
              key={t.id ?? 'all'}
              role="tab"
              aria-selected={topicId === t.id}
              className={`chip ${topicId === t.id ? 'selected' : ''}`}
              onClick={() => setParams(t.id ? { topic: t.id } : {}, { replace: true })}
            >
              {t.name}
            </button>
          ))}
        </div>

        <label className="search">
          <Icon>{SEARCH}</Icon>
          <input type="search" value={query} placeholder="Search cards" aria-label="Search cards" onChange={e => setQuery(e.target.value)} />
        </label>

        {cards.isError && !cards.data ? (
          <ErrorState variant="list" error={cards.error} onRetry={() => cards.refetch()} />
        ) : cards.isPending ? (
          <Loading variant="list" />
        ) : (
          <>
            {shown.length === 0 && <p className="empty">{q ? `No cards match "${q}"` : 'No cards here yet. Add one below.'}</p>}
            <ul className="list" hidden={shown.length === 0}>
              {shown.map(c => {
                const due = dueLabel(c.dueAt)
                const saving = c.id.startsWith('tmp-') // added a moment ago, not saved yet
                return (
                  <SwipeRow
                    key={c.id}
                    className="card-row"
                    open={openId === c.id}
                    onOpenChange={o => setOpenId(o ? c.id : null)}
                    onTap={() => (openId ? setOpenId(null) : !saving && setEditing(c))}
                    actions={saving ? [] : [
                      { label: 'Edit card', icon: PENCIL, onClick: () => setEditing(c) },
                      { label: 'Delete card', icon: TRASH, danger: true, onClick: () => setToDelete(c) },
                    ]}
                  >
                    <span className="card-text">
                      <span className="card-q">{c.question}</span>
                      <span className="card-a">{c.answer}</span>
                      {!topicId && <span className="card-topic">{topicName(c.topicId)}</span>}
                    </span>
                    <span className={`due ${due === 'due now' ? 'now' : ''}`}>{due}</span>
                  </SwipeRow>
                )
              })}
              {cards.hasNextPage && (
                <li ref={sentinel} className="row loading-more" role="status">
                  {cards.isFetchNextPageError ? (
                    <button className="copy-btn" onClick={() => cards.fetchNextPage()}>Try again</button>
                  ) : (
                    'Loading more…'
                  )}
                </li>
              )}
            </ul>
          </>
        )}
      </main>

      <Composer
        inputRef={input}
        value={text}
        onChange={setText}
        onSubmit={send}
        canSend={canSend}
        maxLength={draftQ == null ? 2000 : 5000}
        placeholder={draftQ == null ? `New card in ${targetName}…` : 'Type the answer…'}
        label={draftQ == null ? 'New card question' : 'New card answer'}
        sendLabel={draftQ == null ? 'Next: answer' : 'Add card'}
        enterKeyHint={draftQ == null ? 'next' : 'send'}
        error={duplicate ? `Already in ${targetName}` : null}
        onEscape={draftQ != null ? backToQuestion : undefined}
        context={
          draftQ != null && (
            <p className="composer-context">
              <span className="ellipsis">Answer for <strong>{draftQ}</strong></span>
              <button type="button" aria-label="Back to question" onClick={backToQuestion}>
                <Icon>{CLOSE}</Icon>
              </button>
            </p>
          )
        }
      />

      <dialog ref={deleteDialog} className="sheet" onClose={() => setToDelete(null)}>
        {toDelete && (
          <>
            <h2>Delete this card?</h2>
            <p>"{toDelete.question}" and its review history will be gone. This can't be undone.</p>
            <div className="sheet-actions">
              <button onClick={() => setToDelete(null)}>Cancel</button>
              <button className="danger" onClick={() => { remove(toDelete.id); setToDelete(null) }}>Delete</button>
            </div>
          </>
        )}
      </dialog>

      <dialog ref={editDialog} className="sheet edit" onClose={() => setEditing(null)}>
        {editing && (
          <EditCard
            key={editing.id}
            card={editing}
            topics={topicList}
            onCancel={() => setEditing(null)}
            onSave={patch => {
              update({ id: editing.id, patch })
              setEditing(null)
            }}
          />
        )}
      </dialog>
    </>
  )
}
