# Backlog

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
