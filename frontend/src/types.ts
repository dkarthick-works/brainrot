// Mirrors the backend JSON (Card.java, CardService records, ApiKey.java). The backend is the source of truth.
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
export type Topic = { id: string; name: string; isDefault: boolean; cardCount: number; dueCount: number }
export type Due = { cards: Card[]; dueCount: number; nextDueAt: string | null }
export type Page = { cards: Card[]; nextCursor: string | null }
export type CreateResult = { created: Card[]; skipped: { id: string | null; question: string; reason: string }[] }
export type ApiKey = { id: string; name: string; prefix: string; createdAt: string; lastUsedAt: string | null }
export type CreatedKey = { id: string; name: string; prefix: string; key: string }
