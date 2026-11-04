# Backlog

## MCP OAuth (after v1)

v1 ships with API keys (`fc_...` in the agent's `Authorization` header). Add OAuth when a client needs it. claude.ai web/mobile connectors and ChatGPT connectors probably don't accept a static header; confirm for each client you plan to use.

Nothing in the data model, tools or REST API changes, because everything resolves the user through `SecurityConfig.userId()`. API keys stay for scripts and cron.

### Steps
1. **Clerk dashboard:** enable OAuth applications and Dynamic Client Registration. No code.
2. **`GET /.well-known/oauth-protected-resource`** (RFC 9728): JSON with `resource` = `https://<host>/mcp` and `authorization_servers` = [Clerk issuer]. First check whether Spring Security or `spring-ai-community/mcp-security` provides this.
3. **401 on `/mcp` without a token:** a custom `AuthenticationEntryPoint` that sends `WWW-Authenticate: Bearer resource_metadata="https://<host>/.well-known/oauth-protected-resource"`.
4. **Accept Clerk OAuth access tokens:** validate the audience (the token must be issued for our `/mcp` resource) and map `sub` to `userId`.

### Open question to resolve first
Are Clerk OAuth access tokens JWTs or opaque strings?
- **JWT:** the existing JWT decoder handles them; add the audience validator.
- **Opaque:** use Spring's opaque-token introspection against Clerk, plus an `AuthenticationManagerResolver` that routes each token by its shape (`fc_` key, Clerk JWT, or OAuth token). About 30 extra lines.

### Effort
Half a day to a day, about 50–100 lines. Most of the time goes into testing with real clients (Claude Code, claude.ai connector).

## Delete user data when a Clerk account is deleted

Users delete their account from Clerk's profile screen (Settings → Manage account). That only removes the Clerk login: the backend has no user-deletion path, so the user's cards, topics and API keys stay in MongoDB. That leaves orphaned personal data behind, and in-app account deletion is required for App Store apps with sign-up.

### Steps
1. **Clerk dashboard:** add a webhook endpoint subscribed to `user.deleted`.
2. **`POST /webhooks/clerk`:** verify the Svix signature (Clerk signs webhooks with Svix; store the signing secret as an env var), then read `data.id` as the `userId`.
3. **Delete everything for that `userId`:** `cards`, `topics` and `api_keys`. Deleting the API keys also cuts off any agent still using one.
4. **Permit the route** in `SecurityConfig` without a JWT; the signature check is its auth.

Make it idempotent: Clerk retries failed deliveries, and deleting an already-deleted user should still return 2xx.

## Frontend: "Move cards to…" when deleting a topic

The backend refuses to delete a topic that still has cards ("Move or delete them first"). Today the Topics screen only shows that message with OK (see `prototype/DECISIONS.md` → Topics → Deleting), so a topic with cards can't be deleted from the app.

The backend already supports the move: `POST /api/topics/{id}/move-cards` with `{ "toTopicId": "…" }`. It returns how many cards moved plus a `skipped` list for cards whose question already exists in the target topic. Frontend work only:
1. In the "Can't delete *Name*" dialog, add **Move N cards to…** with a topic picker.
2. Call `move-cards`. If any were skipped, list them ("3 cards already exist in *Target*") and offer to delete those or cancel.
3. Once the topic is empty, delete it in the same flow.

Design it in the prototype first, the same way as the other screens.

## Other deferred items
These come from `BACKEND_PLAN.md` → "Deliberately skipped".
- New-cards-per-day limit (needed if agent-pushed cards flood the landing queue)
- Rate limiting, review history/stats, import/export, soft delete

## SM-2 extras
All three changes stay inside `Sm2` (plus the due query for the day cutoff). Nothing else changes.
- **Learning steps (Anki-style):** show a failed card again within minutes (for example 1m → 10m) instead of tomorrow. v1 makes a failed card due again tomorrow.
- **Interval fuzz:** add a small random spread (about ±5%) to intervals so cards added together don't all come due on the same day. v1 intervals are exact.
- **Day-boundary/timezone cutoff:** "due today" should follow the user's local day (Anki uses a 4am rollover), which needs a user timezone. v1 counts a card as due when `dueAt <= now`.
- **Separate Hard, Good and Easy:** today Hard (3), Good (4) and Easy (5) all produce the same next interval. They only change the ease factor, which affects later intervals, so the UI shows identical hints (for example "15d" on all three buttons) and users can't tell them apart. Fix: give Hard a shorter interval, for example about 1.2× the previous interval as Anki does (at least previous + 1 day), keep Good at interval × ease, and give Easy a bonus (Anki uses about 1.3× on top of Good). Found while prototyping the Play Screen.
