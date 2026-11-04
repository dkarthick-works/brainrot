# Flashcards Backend Plan

Spring Boot + MongoDB service with a REST API (web UI) and an MCP endpoint (agents) running in **one process**. Clerk handles auth. SM-2 handles scheduling. Coolify deploys it from a Dockerfile.

Scope: backend only. The frontend (React + shadcn, black/white/grey) comes later. This plan only fixes the API shape the frontend will use.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.1.2 + Spring AI 2.0.1 |
| Build | Maven (wrapper committed) |
| DB | MongoDB via `spring-boot-starter-data-mongodb`, `MongoTemplate` only (no repository interfaces) |
| Auth | `spring-boot-starter-oauth2-resource-server` (validates Clerk JWTs) plus a small API-key filter for agents |
| MCP | `spring-ai-starter-mcp-server-webmvc`, `STATELESS` streamable HTTP at `POST /mcp`, `@McpTool` methods |
| Health | `spring-boot-starter-actuator` (`/actuator/health` only) |
| Tests | JUnit 5 and Testcontainers (Mongo) |

Lombok (`@Getter @Setter @NoArgsConstructor`) on the mutable documents `Card` and `ApiKey`. Records for `Topic` and for request/response DTOs. No MapStruct: documents are returned directly or through one small record. No validation starter: `CardService` validates every input itself, so REST and MCP share one check.

---

## 2. Domain model (Mongo)

There is no `users` collection. The Clerk `sub` (for example `user_2abc...`) is the `userId` everywhere.

### `topics`

```
_id        String   "<userId>:default" for the default topic (deterministic, so no race); ObjectId hex for others
userId     String
name       String   "Default" for the default topic; max 100 chars
nameKey    String   lowercased, trimmed name; unique per user
createdAt  Instant
```

- The default topic is created lazily with an idempotent `save()` the first time a user creates a card or lists topics.
- Index: `{userId: 1, nameKey: 1}` **unique**, so topic names are case-insensitively unique per user.
- Rules: the default topic can be renamed but never deleted. Any topic can be deleted only when it has **no cards**; otherwise the API returns 409 and the user must move or delete the cards first.

### `cards`

```
_id          ObjectId
userId       String
topicId      String     defaults to "<userId>:default"
question     String     required, max 2000 chars
answer       String     required, max 5000 chars
questionKey  String     lowercase, whitespace-collapsed question, used for dedupe
# SM-2 state
easeFactor   double     starts at 2.5, floor 1.3
interval     int        days, starts at 0
repetitions  int        starts at 0
dueAt        Instant    starts at createdAt (new cards are due immediately)
lastReviewedAt Instant?
createdAt    Instant
updatedAt    Instant
```

**Indexes** (`@CompoundIndex` with `spring.data.mongodb.auto-index-creation=true`):

- `{userId: 1, dueAt: 1}`: the due-cards query, the hottest path.
- `{userId: 1, topicId: 1, questionKey: 1}` **unique**: dedupe. Agents will push the same question repeatedly, and the DB rejects the duplicates.

### `api_keys`

```
_id         ObjectId
userId      String
name        String     e.g. "claude-desktop"
keyHash     String     SHA-256 of the raw key, unique index
prefix      String     first 8 chars, shown in the UI to identify the key
createdAt   Instant
lastUsedAt  Instant?
```

The raw key looks like `fc_<32 random bytes base64url>`. It is shown once at creation and never stored.

---

## 3. SM-2 algorithm

This is one pure static function, `Sm2.review(card, quality, now)`, which returns the updated card. Quality is an integer from 0 to 5 (classic SM-2).

```
if quality >= 3:
    if repetitions == 0: interval = 1
    elif repetitions == 1: interval = 6
    else: interval = round(interval * easeFactor)
    repetitions += 1
else:
    repetitions = 0
    interval = 1

easeFactor += 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
easeFactor = max(1.3, easeFactor)
dueAt = now + interval days
lastReviewedAt = now
```

The frontend will use four Anki-style buttons that map to quality values: **Again = 1, Hard = 3, Good = 4, Easy = 5**. The API accepts the raw 0–5 value so agents and the UI share one contract.

Deliberately skipped:

- Anki's learning steps (re-showing a failed card within minutes). A failed card is due again tomorrow.
- Fuzz on intervals.
- Day-boundary or timezone cutoffs. "Due" simply means `dueAt <= now`.

All three can be layered on later inside `Sm2` alone.

---

## 4. REST API (web UI)

Base path: `/api`. Every route requires auth (a Clerk JWT or an API key). `userId` always comes from the security context and is never taken from the request.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/cards/due?limit=20&topicId=` | **Landing screen.** Optional `topicId` practises one topic. Due cards ordered by `dueAt` ascending. Response `{ cards: [...], dueCount: n, nextDueAt: "..." | null }`, where `dueCount` is the total due right now, not just this batch, and `nextDueAt` is the soonest `dueAt` still in the future (for "Next card due in 3h"; null when nothing else is scheduled). It is computed on every request by a second query, `countDocuments({userId, dueAt: {$lte: now}})`, answered from the `{userId, dueAt}` index. There is no stored counter. No cursor needed: reviewing a card pushes `dueAt` into the future, so it drops out of the next fetch on its own. Client flow: keep a local queue; when 5 or fewer remain and `dueCount` says more exist, prefetch the next batch and skip any card id already seen this session (unreviewed cards from the current batch come back too). An empty response means done. |
| POST | `/api/cards/{id}/review` | Body `{ quality: 0-5 }`. Applies SM-2 and returns the updated card. |
| POST | `/api/cards` | Body `{ topicId?, cards: [{question, answer}] }` (max 100). Creates in batch (a single card is a batch of one). Response `{ created: [...], skipped: [{question, reason:"duplicate"}] }`. |
| GET | `/api/cards?limit=50&after=<cursor>&q=<text>&topicId=` | List and search, newest first, optionally within one topic. `q` is plain search text, not a regex: it is escaped with `Pattern.quote` and matched case-insensitively against the question or the answer. `after` is the `nextCursor` from the previous page (the last card's `_id`); the query is `_id < after`, sorted by `_id` descending, fetching `limit + 1` to know if more exist. Response `{ cards: [...], nextCursor: "..." | null }`. Used by the "manage cards" screen (load more / infinite scroll). |
| PATCH | `/api/cards/{id}` | Edit the question, answer and/or `topicId` (moves one card). Leaves the SM-2 state untouched. 409 if the target topic already has the same question. |
| DELETE | `/api/cards/{id}` | Deletes the card. |
| GET | `/api/topics` | Lists topics with `cardCount` and `dueCount` per topic (one aggregation grouped by `topicId`). |
| POST | `/api/topics` | Body `{ name }`. 409 if the name exists (case-insensitive). |
| PATCH | `/api/topics/{id}` | Body `{ name }`. Rename, including the default topic. 409 on a name clash. |
| DELETE | `/api/topics/{id}` | 409 with `"Topic has N cards. Move or delete them first."` if it has cards. 409 for the default topic. |
| POST | `/api/topics/{id}/move-cards` | Body `{ toTopicId }`. Moves **all** cards to another topic, card by card. Duplicates (same question already in the target) are skipped. Response `{ moved: n, skipped: [{id, question, reason:"duplicate"}] }`. |
| POST | `/api/api-keys` | Body `{ name }`. Returns `{ id, key, prefix }`; the key is shown once. |
| GET | `/api/api-keys` | Lists keys (no secrets). |
| DELETE | `/api/api-keys/{id}` | Revokes a key. |

Cross-user access to a card returns **404**, not 403: every query filters on both `userId` and `id`.

Validation uses `jakarta.validation` annotations on request records. One `@RestControllerAdvice` maps errors to `ProblemDetail` (400 for validation, 404 for not found).

---

## 5. MCP layer (same app, same port)

- The starter `spring-ai-starter-mcp-server-webmvc` uses the `STATELESS` protocol and serves `POST /mcp`. It goes through the same Spring Security chain as `/api/**`.
- `CardTools.java` is a `@Component` with Spring AI `@McpTool` methods. The tools call `CardService`, the same service the REST controllers call. Because the controllers are thin wrappers, this is the same code path as the API with no HTTP hop.

### Tools

Descriptions are written for an LLM.

| Tool | Args | Returns |
|---|---|---|
| `create_flashcards` | `cards: [{question, answer}]`, `topic?` (a **name**) | created count, skipped duplicates. The topic is found case-insensitively or created; omitted means Default. |
| `list_topics` | none | topic names with card and due counts, so agents reuse existing topics |
| `search_flashcards` | `query?`, `limit?`, `after?` | matching cards plus `nextCursor`, so agents can check before creating |
| `update_flashcard` | `id`, `question?`, `answer?`, `topic?` (name) | updated card |
| `delete_flashcard` | `id` | ok |
| `get_due_flashcards` | `limit?` | due cards plus total due count |
| `review_flashcard` | `id`, `quality 0-5` | updated card with next due date |

The last two make agent-driven quizzing possible at no extra cost. Topic rename/delete/move-all stay UI-only; agents don't need them.

### Agent auth

Clerk session JWTs expire after about 60 seconds, so they are unusable in an agent's MCP config. Agents use a long-lived API key instead:

```json
{ "mcpServers": { "flashcards": {
    "type": "http", "url": "https://<host>/mcp",
    "headers": { "Authorization": "Bearer fc_..." } } } }
```

**Resolved:** on a servlet app Spring AI sets `immediateExecution(true)`, and the stateless transport handles each call synchronously, so tools run on the request thread and read `userId` from `SecurityContextHolder`. Covered by `CardFlowTest.apiKeysAndMcp`.

---

## 6. Security

One `SecurityFilterChain`:

- `/actuator/health` is public. Everything else is authenticated.
- **Clerk JWT:** a `JwtDecoder` bean uses `${CLERK_ISSUER}/.well-known/jwks.json` (fetched lazily, so startup doesn't need Clerk) and validates the issuer plus `azp` against `ALLOWED_ORIGINS`, as Clerk recommends. A token without `azp` passes.
- **API key:** `ApiKeyFilter` runs before `BearerTokenAuthenticationFilter`. If the bearer token starts with `fc_`, it hashes the token, looks up `api_keys`, sets an `Authentication` whose principal is the `userId`, and updates `lastUsedAt`.
- A custom `BearerTokenResolver` returns `null` for `fc_` tokens so the JWT decoder never tries to parse them.
- `SecurityConfig.userId()` is `authentication.getName()`: the JWT `sub`, or the API key's `userId`.
- CORS: allow `${ALLOWED_ORIGINS}`.
- Error dispatches are permitted so a real error isn't masked as 401. Errors are `ProblemDetail` via `ResponseStatusException` (`spring.mvc.problemdetails.enabled`).
- Sessions are stateless and CSRF is disabled (bearer-only API).

API-key creation and revocation require a Clerk JWT, so an API key cannot mint more API keys.

---

## 7. Project layout

The project uses a flat package with no layering ceremony.

```
pom.xml
Dockerfile
.dockerignore
compose.yaml           // dev-only MongoDB on localhost:27018
src/main/resources/application.yml
src/main/java/app/flashcards/
  FlashcardsApplication.java
  Card.java            // @Document, Lombok, mutable (SM-2 and PATCH update it in place)
  Topic.java
  ApiKey.java
  Sm2.java             // pure function
  CardService.java     // cards + topics: create (dedupe), due, review, update, delete, search, topic CRUD, move
  CardController.java  // /api/cards, /api/topics
  ApiKeyController.java// /api/api-keys (hashing inline)
  CardTools.java       // MCP tools -> CardService
  SecurityConfig.java  // chain, ApiKeyFilter, BearerTokenResolver, azp validator, CORS, userId()
src/test/java/app/flashcards/
  Sm2Test.java         // pure unit test of the algorithm table
  CardFlowTest.java    // Testcontainers: cursor paging; API keys + MCP tools/call; create -> due -> review -> no longer due; dedupe; cross-user 404; delete non-empty topic -> 409; move-cards skips duplicates
```

---

## 8. Config (env vars for Coolify)

See `src/main/resources/application.yml`. Env vars:

| Var | Dev default | Production |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27018/flashcards` | Coolify MongoDB internal URI |
| `CLERK_ISSUER` | `https://right-perch-3118.clerk.accounts.dev` | Clerk production instance URL |
| `ALLOWED_ORIGINS` | `http://localhost:8089` | frontend URL(s), comma-separated |
| `PORT` | `8088` | `8088` |

---

## 9. Dockerfile

See `Dockerfile`: multi-stage, `eclipse-temurin:25`, runs as `ubuntu` (non-root), installs `curl` for the `HEALTHCHECK` (the JRE image has neither curl nor wget, and Coolify's health check needs one).

Coolify setup:

- Point Coolify at the repo using the Dockerfile build pack, with "Ports Exposes" set to `8088`. Do not map a host port: Coolify's proxy routes by domain.
- Add a MongoDB resource in Coolify and use its internal URI as `MONGODB_URI`.
- Set `CLERK_ISSUER` and `ALLOWED_ORIGINS`.
- Health check path: `/actuator/health`.

---

## 10. Status

Steps 1–6 are done: scaffold, model + SM-2, service + controllers, security, API keys + MCP, tests (`./mvnw test`, 7 passing). The Docker image builds and runs healthy locally.

Remaining:

- Manual check with a real Clerk token once the frontend exists.
- Connect Claude Code to `/mcp` with an API key.
- Deploy to Coolify.

Run locally:

```bash
docker compose up -d                                   # Mongo on 27018
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw spring-boot:run   # API + MCP on 8088
```

---

## Deliberately skipped (add when needed)

- **New-cards-per-day limit.** Agents can push hundreds of cards, and all of them land as due today. Add an Anki-style "max N new/day" if the landing queue becomes overwhelming. It's a filter in `CardService.due()`.
- **MCP OAuth (spec auth flow).** Add it only if a client (for example claude.ai custom connectors) refuses header-based bearer auth.
- **Rate limiting, review history/stats collection, card import/export, soft delete.**
