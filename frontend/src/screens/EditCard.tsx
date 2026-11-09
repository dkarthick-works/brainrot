import { useState } from 'react'
import { CHEVRON_DOWN, Icon } from '../components/ui'
import type { Card, Topic } from '../types'

export type CardPatch = Pick<Card, 'question' | 'answer' | 'topicId'>
type Props = { card: Card; topics: Topic[]; onSave: (p: CardPatch) => void; onCancel: () => void }

/** Edit a card as a flashcard: question + topic on the front, answer on the back. Tapping the card (not a field) flips it. */
export default function EditCard({ card, topics, onSave, onCancel }: Props) {
  const [draft, setDraft] = useState<CardPatch>({ question: card.question, answer: card.answer, topicId: card.topicId })
  const [flipped, setFlipped] = useState(false)
  const set = (k: keyof CardPatch) => (e: React.ChangeEvent<HTMLTextAreaElement | HTMLSelectElement>) =>
    setDraft(d => ({ ...d, [k]: e.target.value }))
  const valid = draft.question.trim() !== '' && draft.answer.trim() !== ''
  // Cancel / Save sit inside the card, on whichever face is showing
  const actions = (visible: boolean) => (
    <div className="sheet-actions card-actions">
      <button type="button" onClick={onCancel} tabIndex={visible ? 0 : -1}>Cancel</button>
      <button type="submit" className="primary" disabled={!valid} tabIndex={visible ? 0 : -1}>Save</button>
    </div>
  )

  return (
    <form
      onSubmit={e => {
        e.preventDefault()
        if (valid) onSave({ ...draft, question: draft.question.trim(), answer: draft.answer.trim() })
      }}
    >
      <div className="edit-stack">
        <div
          className={`card edit-card ${flipped ? 'flipped' : ''}`}
          onClick={e => !(e.target as HTMLElement).closest('textarea, select, button') && setFlipped(f => !f)}
        >
          <div className="card-inner">
            <section className="face front" aria-hidden={flipped}>
              <textarea className="face-field" value={draft.question} onChange={set('question')} aria-label="Question" placeholder="Question" rows={4} maxLength={2000} tabIndex={flipped ? -1 : 0} />
              <span className="topic-pick">
                <select value={draft.topicId} onChange={set('topicId')} aria-label="Topic" tabIndex={flipped ? -1 : 0}>
                  {topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
                <Icon>{CHEVRON_DOWN}</Icon>
              </span>
              <button type="button" className="hint flip" onClick={() => setFlipped(true)} tabIndex={flipped ? -1 : 0}>Tap card to edit answer</button>
              {actions(!flipped)}
            </section>
            <section className="face back" aria-hidden={!flipped}>
              <span className="topic">Answer</span>
              <textarea className="face-field" value={draft.answer} onChange={set('answer')} aria-label="Answer" placeholder="Answer" rows={4} maxLength={5000} tabIndex={flipped ? 0 : -1} />
              <button type="button" className="hint flip" onClick={() => setFlipped(false)} tabIndex={flipped ? 0 : -1}>Tap card to edit question</button>
              {actions(flipped)}
            </section>
          </div>
        </div>
      </div>
    </form>
  )
}
