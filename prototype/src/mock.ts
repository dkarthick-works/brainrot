// Shapes mirror backend Card.java / Topic.java JSON. Mock only, no API calls.
// GET /api/topics returns TopicView
export type Topic = { id: string; name: string; isDefault: boolean; cardCount: number; dueCount: number }
export type Card = {
  id: string
  topicId: string
  question: string
  answer: string
  easeFactor: number
  interval: number
  repetitions: number
  dueAt: string
  lastReviewedAt: string | null
  createdAt: string
  updatedAt: string
}

const t = '2026-11-01T09:00:00Z'

export const topics: Topic[] = [
  { id: 't1', name: 'Japanese', isDefault: true, cardCount: 42, dueCount: 2 },
  { id: 't2', name: 'Biology', isDefault: false, cardCount: 18, dueCount: 2 },
  { id: 't3', name: 'JavaScript', isDefault: false, cardCount: 27, dueCount: 2 },
  // Extra topics so the list passes 10 and the search field shows. Trim to 10 or fewer to see it hidden.
  { id: 't4', name: 'Spanish', isDefault: false, cardCount: 64, dueCount: 0 },
  { id: 't5', name: 'World History', isDefault: false, cardCount: 31, dueCount: 0 },
  { id: 't6', name: 'Chemistry', isDefault: false, cardCount: 22, dueCount: 0 },
  { id: 't7', name: 'SQL', isDefault: false, cardCount: 15, dueCount: 0 },
  { id: 't8', name: 'Music Theory', isDefault: false, cardCount: 9, dueCount: 0 },
  { id: 't9', name: 'Geography', isDefault: false, cardCount: 40, dueCount: 0 },
  { id: 't10', name: 'Anatomy', isDefault: false, cardCount: 53, dueCount: 0 },
  { id: 't11', name: 'Economics', isDefault: false, cardCount: 12, dueCount: 0 },
  { id: 't12', name: 'Rust', isDefault: false, cardCount: 1, dueCount: 0 },
  { id: 't13', name: 'Old Notes', isDefault: false, cardCount: 0, dueCount: 0 }, // empty, so Delete can be confirmed
]

const card = (id: string, topicId: string, question: string, answer: string, repetitions = 0, interval = 0): Card => ({
  id, topicId, question, answer, easeFactor: 2.5, interval, repetitions,
  dueAt: '2026-11-04T08:00:00Z', lastReviewedAt: repetitions ? t : null, createdAt: t, updatedAt: t,
})

// GET /api/cards/due returns { cards: Card[], dueCount }
export const dueCards: Card[] = [
  card('c1', 't1', 'How do you say "thank you" in Japanese?', 'ありがとう (arigatō)', 2, 6),
  card('c2', 't2', 'What is the powerhouse of the cell?', 'The mitochondrion. It produces ATP through cellular respiration.'),
  card('c3', 't3', 'What does `Array.prototype.flatMap` do?', 'Maps each element, then flattens the result one level deep.', 1, 1),
  card('c4', 't2', 'What molecule carries genetic information?', 'DNA (deoxyribonucleic acid).', 3, 15),
  card('c5', 't1', 'What does 猫 mean?', 'Cat (neko).'),
  card('c6', 't3', 'Difference between `==` and `===`?', '`===` compares without type coercion; `==` coerces types first.', 2, 6),
]

const inDays = (d: number) => new Date(Date.now() + d * 864e5).toISOString()
const later = (id: string, topicId: string, question: string, answer: string, days: number, repetitions = 3): Card => ({
  ...card(id, topicId, question, answer, repetitions, days),
  dueAt: inDays(days),
})

// GET /api/cards returns { cards: Card[], nextCursor } (50 per page). Due cards first, then ones scheduled later.
export const allCards: Card[] = [
  ...dueCards,
  later('c7', 't1', 'What does 水 mean?', 'Water (mizu).', 3),
  later('c8', 't1', 'How do you say "good morning"?', 'おはよう (ohayō)', 12),
  later('c9', 't2', 'What are the four DNA bases?', 'Adenine, thymine, guanine, cytosine.', 5),
  later('c10', 't2', 'Where does photosynthesis happen?', 'In the chloroplasts of plant cells.', 47),
  later('c11', 't3', 'What does `Promise.all` do when one promise rejects?', 'It rejects immediately with that reason.', 2),
  later('c12', 't3', 'What is a closure?', 'A function that remembers the variables from the scope where it was created.', 21),
  later('c13', 't4', 'How do you say "library" in Spanish?', 'La biblioteca.', 8),
  later('c14', 't7', 'What does `LEFT JOIN` return?', 'All rows from the left table, plus matching rows from the right (NULL where none match).', 90),
]

// Clerk user (shown on Settings). The real app reads this from Clerk's useUser().
export const user = { name: 'Sam Rivera', email: 'sam@example.com' }

// GET /api/api-keys returns ApiKey[] (keyHash and userId are never sent)
export type ApiKey = { id: string; name: string; prefix: string; createdAt: string; lastUsedAt: string | null }
export const apiKeys: ApiKey[] = [
  { id: 'k1', name: 'claude-code', prefix: 'fc_a1B2c', createdAt: '2026-10-12T10:00:00Z', lastUsedAt: new Date(Date.now() - 2 * 36e5).toISOString() },
  { id: 'k2', name: 'cron backup', prefix: 'fc_Zx9Qw', createdAt: '2026-09-03T08:30:00Z', lastUsedAt: null },
]
