import { getToken } from '@clerk/react'
import type { ApiKey, Card, CreatedKey, CreateResult, Due, Page, Topic } from './types'

const BASE = import.meta.env.VITE_API_URL as string // "/api" in production (same origin), absolute in dev
// The MCP endpoint lives at /mcp on the same host as the API
export const MCP_URL = new URL('/mcp', new URL(BASE, location.href)).href

/** status 0 = the request never reached the server (offline, DNS, CORS) */
export class ApiError extends Error {
  status: number
  constructor(status: number, detail: string) {
    super(detail)
    this.status = status
  }
}

async function request<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const token = await getToken()
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      signal,
      headers: { Authorization: `Bearer ${token}`, ...(body !== undefined && { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    if (signal?.aborted || (error instanceof Error && error.name === 'AbortError')) throw error
    throw new ApiError(0, 'network error')
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => null) // ProblemDetail
    throw new ApiError(res.status, problem?.detail ?? res.statusText)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

const qs = (params: Record<string, string | number | null | undefined>) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v != null && v !== '') p.set(k, String(v))
  return p.size ? `?${p}` : ''
}

export const api = {
  due: (limit = 20) => request<Due>(`/cards/due${qs({ limit })}`),
  review: (id: string, quality: number) => request<Card>(`/cards/${id}/review`, 'POST', { quality }),
  cards: (p: { topicId: string | null; q: string; after?: string | null }, signal?: AbortSignal) =>
    request<Page>(`/cards${qs({ limit: 50, topicId: p.topicId, q: p.q, after: p.after })}`, 'GET', undefined, signal),
  createCard: (topicId: string | null, question: string, answer: string) =>
    request<CreateResult>('/cards', 'POST', { topicId, cards: [{ question, answer }] }),
  updateCard: (id: string, patch: Pick<Card, 'question' | 'answer' | 'topicId'>) => request<Card>(`/cards/${id}`, 'PATCH', patch),
  deleteCard: (id: string) => request<void>(`/cards/${id}`, 'DELETE'),
  topics: () => request<Topic[]>('/topics'),
  createTopic: (name: string) => request<Topic>('/topics', 'POST', { name }),
  renameTopic: (id: string, name: string) => request<Topic>(`/topics/${id}`, 'PATCH', { name }),
  deleteTopic: (id: string) => request<void>(`/topics/${id}`, 'DELETE'),
  apiKeys: () => request<ApiKey[]>('/api-keys'),
  createKey: (name: string) => request<CreatedKey>('/api-keys', 'POST', { name }),
  revokeKey: (id: string) => request<void>(`/api-keys/${id}`, 'DELETE'),
}

/** Shared by every screen that lists topics. Newest first, so a topic you just added shows at the top. */
export const topicsQuery = { queryKey: ['topics'], queryFn: async () => (await api.topics()).reverse() }
