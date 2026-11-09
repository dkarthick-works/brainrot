# API, MCP and authentication

[Documentation home](../README.md) · [Data semantics](data-model.md)

## Authentication boundaries

All data requests use `Authorization: Bearer <token>`. The [security configuration](../src/main/java/app/flashcards/SecurityConfig.java) is stateless and disables CSRF; CORS allows configured origins, methods GET/POST/PATCH/DELETE and headers Authorization/Content-Type. Static web assets and non-data paths are public; `/api/**` and `/mcp` (including `/mcp/**`) are protected.

| Credential | REST cards/topics | REST key management | MCP |
| --- | --- | --- | --- |
| Clerk session JWT | Yes | Yes | Yes |
| `fc_` API key | Yes | No (403) | Yes |
| Clerk OAuth access token | No (403) | No (403) | Yes |

Session JWTs validate signature, issuer and timestamps through the standard decoder; if `azp` exists, it must match an allowed origin. OAuth tokens use header type `at+jwt` or `application/at+jwt`, validate signature/issuer/timestamps, require a nonblank `sub`, and must contain the exact `MCP_RESOURCE_URL` in `aud`. They receive a distinct authority and are not signed-in sessions. No per-tool scope-based authorization is implemented: all three accepted credential kinds act as their owning Clerk user within their allowed surface.

Public protected-resource metadata is at `/.well-known/oauth-protected-resource/mcp`. MCP authentication challenges point clients to it; it advertises the configured issuer as authorization server and `profile`, `email` scopes. Configure the MCP server/resource in Clerk and let an OAuth-capable MCP client perform sign-in. This backend verifies tokens and publishes resource metadata; it does not implement its own authorization server or consent UI. [OAuthMcpTest.java](../src/test/java/app/flashcards/OAuthMcpTest.java) exercises metadata, challenges, real local token decoding and the REST boundary.

### API keys

[ApiKeyController.java](../src/main/java/app/flashcards/ApiKeyController.java) requires a session `JwtAuthenticationToken` to create, list or revoke keys. A key's raw value is returned only on creation. It consists of `fc_` plus 32 random bytes encoded URL-safe without padding; only the SHA-256 hash is stored. Authentication updates `lastUsedAt`. There is no expiry field or implemented per-key scope; revoke a key to remove its stored record. Keep keys out of repository files, URLs and shared logs.

## REST contracts

Paths below are relative to `/api`. JSON request fields with `?` are optional. Responses are controller/service records and model serialization, not an OpenAPI-generated schema. Sources: [CardController.java](../src/main/java/app/flashcards/CardController.java), [CardService.java](../src/main/java/app/flashcards/CardService.java), [ApiKeyController.java](../src/main/java/app/flashcards/ApiKeyController.java).

| Method and path | Input | Success response |
| --- | --- | --- |
| `GET /cards` | Query `topicId?`, `q?`, `after?`, `limit=50` | 200 `{cards, nextCursor}` |
| `GET /cards/due` | Query `topicId?`, `limit=20` | 200 `{cards, dueCount, nextDueAt}` |
| `POST /cards` | `{topicId?, cards:[{question,answer}]}` | 200 `{created:[Card], skipped:[Skipped]}` |
| `POST /cards/{id}/review` | `{quality}` integer 0–5 | 200 updated Card |
| `PATCH /cards/{id}` | `{question?, answer?, topicId?}` | 200 updated Card; scheduling state retained |
| `DELETE /cards/{id}` | — | 204 |
| `GET /topics` | — | 200 `[{id,name,isDefault,cardCount,dueCount}]`, oldest-created first |
| `POST /topics` | `{name}` | 201 Topic |
| `PATCH /topics/{id}` | `{name}` | 200 Topic |
| `DELETE /topics/{id}` | — | 204; default/nonempty topics cannot be deleted |
| `POST /topics/{id}/move-cards` | `{toTopicId}` | 200 `{moved, skipped:[Skipped]}` |
| `POST /api-keys` | `{name}` (nonblank, at most 100 characters before stripping) | 201 `{id,name,prefix,key}`; raw `key` shown once |
| `GET /api-keys` | — | 200 `[{id,name,prefix,createdAt,lastUsedAt}]` |
| `DELETE /api-keys/{id}` | — | 204 |

`Card` contains `id`, `topicId`, `question`, `answer`, `easeFactor`, `interval`, `repetitions`, `dueAt`, `lastReviewedAt`, `createdAt`, `updatedAt`. `Topic` contains `id`, `name`, `createdAt`; it differs from the enriched topic-list response. `Skipped` contains `id`, `question`, `reason`; duplicate inserts have null `id`, duplicate moves identify the existing source card. Owner identifiers, normalized uniqueness keys and API-key hashes are JSON-ignored.

`/cards` searches literal case-insensitive substrings in question or answer, newest ObjectId first. Pass the returned `nextCursor` as `after`; it must be a valid ObjectId. Limits are clamped to 1–100. Omitted topic filters mean all topics; omitted create `topicId` means Default. Due results are oldest-due first and include the full matching due count, plus the earliest future due time (or null), even when the returned batch is limited.

Validation errors normally use HTTP 400, unknown/non-owned card or topic IDs 404, and uniqueness/deletion conflicts 409. Missing/invalid authentication is 401; forbidden credential surfaces return 403. Service/controller errors use Spring Problem Details; [api.ts](../frontend/src/api.ts) reads its `detail` with an HTTP-status-text fallback. Do not assume every authentication failure has the same JSON error shape. Creation and moving are per-card operations, not atomic batch transactions: earlier writes can survive a later validation/storage failure.

## MCP contract

[application.yml](../src/main/resources/application.yml) configures Spring AI's **SYNC, STATELESS** WebMVC server at `POST /mcp`, named `flashcards`, version `1.0.0`. Tools execute on the request thread so the authenticated user remains in the security context. Use an HTTP MCP client; this is not a stdio server. Examples in [bruno/MCP/](../bruno/MCP) send JSON-RPC with `Content-Type: application/json` and `Accept: application/json, text/event-stream`; `initialize` uses protocol version `2025-06-18` in that collection.

The authoritative tool argument descriptions and required flags are in [CardTools.java](../src/main/java/app/flashcards/CardTools.java). `tools/list` obtains the server-generated schemas at runtime. Optional arguments may be omitted.

| Tool | Required arguments | Optional arguments | Result |
| --- | --- | --- | --- |
| `create_flashcards` | `cards: [{question,answer}]` | `topic: string` | `{created: number, skipped}` (count, unlike REST's array) |
| `search_flashcards` | — | `query: string`, `topic: string`, `limit: integer` (20), `after: string` | `{cards,nextCursor}` |
| `get_due_flashcards` | — | `limit: integer` (20), `topic: string` | `{cards,dueCount,nextDueAt}` |
| `review_flashcard` | `id: string`, `quality: integer` (0–5) | — | Card |
| `update_flashcard` | `id: string` | `question: string`, `answer: string`, `topic: string` | Card |
| `delete_flashcard` | `id: string` | — | `{id,deleted:true}` |
| `list_topics` | — | — | Enriched topic list |

These are Java result shapes carried by MCP tool results, not REST response envelopes. Search, due and list-topics tools declare read-only/non-destructive hints; a read can still lazily create the Default topic. There are no MCP topic rename/delete/bulk-move or key-management tools.

MCP addresses topics **by name**, REST by ID. Create/update tools create named topics when missing; null/blank create topic means Default, while null/blank update topic leaves it unchanged. Search/due with no topic search across topics; an explicit unknown topic name gives 404 rather than an empty result. Normalization and ownership rules are shared with REST through CardService.

Example tool call body (use only a local/disposable environment):

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "create_flashcards",
    "arguments": {
      "topic": "Example",
      "cards": [{"question": "What is the example answer?", "answer": "Example"}]
    }
  }
}
```

The Agent access UI displays the derived MCP URL and a Claude Code command in [AgentAccess.tsx](../frontend/src/screens/AgentAccess.tsx). Actual client OAuth interoperability and live Clerk configuration were not checked by the audit; integration tests use a local issuer. Never paste keys into a PR or evidence report.
