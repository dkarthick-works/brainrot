import { useState } from 'react'
import type { Card, Topic } from './mock'

type Patch = Pick<Card, 'question' | 'answer' | 'topicId'>
type Props = { card: Card; topics: Topic[]; onSave: (p: Patch) => void; onCancel: () => void }

// Edit a card as a flashcard: question + topic on the front, answer on the back.
// Tapping anywhere on the card that isn't a field flips it, like the Play Screen.
export default function EditCard({ card, topics, onSave, onCancel }: Props) {
  const [draft, setDraft] = useState<Patch>({ question: card.question, answer: card.answer, topicId: card.topicId })
  const [flipped, setFlipped] = useState(false)
  const set = (k: keyof Patch) => (e: React.ChangeEvent<HTMLTextAreaElement | HTMLSelectElement>) =>
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
              <textarea
                className="face-field"
                value={draft.question}
                onChange={set('question')}
                aria-label="Question"
                placeholder="Question"
                rows={4}
                tabIndex={flipped ? -1 : 0}
              />
              <span className="topic-pick">
                <select value={draft.topicId} onChange={set('topicId')} aria-label="Topic" tabIndex={flipped ? -1 : 0}>
                  {topics.map(t => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </select>
                <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
              </span>
              <button type="button" className="hint flip" onClick={() => setFlipped(true)} tabIndex={flipped ? -1 : 0}>
                Tap card to edit answer
              </button>
              {actions(!flipped)}
            </section>
            <section className="face back" aria-hidden={!flipped}>
              <span className="topic">Answer</span>
              <p className="q-small">{draft.question}</p>
              <textarea
                className="face-field"
                value={draft.answer}
                onChange={set('answer')}
                aria-label="Answer"
                placeholder="Answer"
                rows={4}
                tabIndex={flipped ? 0 : -1}
              />
              <button type="button" className="hint flip" onClick={() => setFlipped(false)} tabIndex={flipped ? 0 : -1}>
                Tap card to edit question
              </button>
              {actions(flipped)}
            </section>
          </div>
        </div>
      </div>
    </form>
  )
}
