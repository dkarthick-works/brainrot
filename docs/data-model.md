# Data model and scheduling

[Documentation home](../README.md) · [API contracts](api.md)

## Persistence and ownership

[CardService.java](../src/main/java/app/flashcards/CardService.java), [SecurityConfig.java](../src/main/java/app/flashcards/SecurityConfig.java) and [ApiKeyController.java](../src/main/java/app/flashcards/ApiKeyController.java) access MongoDB directly through `MongoTemplate`. There are no Spring Data repository interfaces. The Clerk subject/authentication name supplies the owning user ID; callers do not choose a different owner in request bodies. Card/topic lookups and mutations are owner-scoped.

| Collection / model | Stored data and indexes |
| --- | --- |
| `cards` / [Card.java](../src/main/java/app/flashcards/Card.java) | Owner, topic ID, question/answer, normalized `questionKey`, ease/interval/repetitions, due/review/create/update instants. Index `(userId,dueAt)` and unique `(userId,topicId,questionKey)`. |
| `topics` / [Topic.java](../src/main/java/app/flashcards/Topic.java) | ID, owner, name, normalized `nameKey`, creation instant. Unique `(userId,nameKey)`. |
| `api_keys` / [ApiKey.java](../src/main/java/app/flashcards/ApiKey.java) | Owner, name, SHA-256 `keyHash`, display prefix, creation/last-use instants. Indexed owner and unique hash. No raw key, expiry or scopes stored. |

[application.yml](../src/main/resources/application.yml) enables automatic index creation. There is no versioned migration runner or explicit schema validation layer in this source tree. Index creation is not a data migration: pre-existing conflicting data can prevent creation of unique indexes. Do not run the application against an existing database as a documentation check.

The Default topic is created lazily with ID `<userId>:default`; initial name/key are `Default`/`default`. It may be renamed but cannot be deleted; identity depends on its ID, not its current name. Names are unique per user, so creating another topic with the same normalized name conflicts. Topic listing computes total and due counts from cards; counts are not stored on topics. A non-default topic must be empty before deletion. Bulk moving retains duplicate source cards and reports them as skipped rather than overwriting target cards.

## Card validation and duplicates

Creation accepts 1–100 cards per request. Questions and answers are stripped, nonblank and limited to 2,000 and 5,000 characters respectively; topic names are stripped, nonblank and limited to 100. Uniqueness keys trim, lowercase with `Locale.ROOT`, and collapse whitespace. The same normalized question is allowed in different topics, but duplicates in one user's topic are skipped during creation. Updates/moves preserve review state; a conflicting update returns 409. This is question uniqueness, not answer similarity or semantic deduplication.

Creation and topic moves write cards individually. There is no cross-card transaction or rollback promise. Moving loads the entire source topic; topic deletion uses a count-then-delete check with an explicitly accepted concurrency race in the service. No foreign-key constraint, distributed lock, background queue or batch job is implemented.

## Review rules

[Sm2.java](../src/main/java/app/flashcards/Sm2.java) is the authoritative scheduler. This is SM-2 ease adjustment with **grade-specific intervals**, not a claim of unmodified classic SM-2.

- New cards start with ease 2.5, interval 0, repetitions 0 and `dueAt=now`.
- Quality 0–2 resets repetitions to 0 and sets interval to **one day**, not an immediate relearning loop.
- With zero repetitions, Hard (3), Good (4), Easy (5) schedule 1, 2 and 4 days respectively; a successful review increments repetitions.
- On later successful reviews, compute the candidate intervals below from the old interval/ease, then choose the grade's value.
- After choosing the interval, update ease (floor 1.3), set `dueAt` to the review instant plus that many 24-hour days and set `lastReviewedAt`; the service also updates `updatedAt`.

```text
hard = max(interval + 1, round(interval * 1.2))
good = max(hard + 1, round(interval * easeFactor))
easy = max(good + 1, round(interval * easeFactor * 1.3))
d = 5 - quality
newEase = max(1.3, oldEase + 0.1 - d * (0.08 + d * 0.02))
```

The due endpoint queries stored instants when requested; it does not require a scheduling daemon. Only current review state/last-review time is persisted, not a separate event history or study-session record. Editing does not reset that state. Repeated review requests are not idempotent: each applies the scheduler again.

[frontend/src/logic.ts](../frontend/src/logic.ts) mirrors these intervals for button hints; the backend calculates the saved result. The UI uses Again=1, Hard=3, Good=4, Easy=5. [Sm2Test.java](../src/test/java/app/flashcards/Sm2Test.java) and [logic.test.ts](../frontend/src/logic.test.ts) share grade cases including rounding and low-ease states.

## Frontend state and network limits

[Play.tsx](../frontend/src/screens/Play.tsx) keeps its review queue in component memory, fetches due cards in batches, deduplicates seen IDs and prefetches when five or fewer remain. Reveal a card, choose a grade and swipe (or use the keyboard) to submit a review. A failed save restores the card and offers retry. [Cards.tsx](../frontend/src/screens/Cards.tsx), [Topics.tsx](../frontend/src/screens/Topics.tsx) and [SaveError.tsx](../frontend/src/components/SaveError.tsx) use optimistic in-memory cache changes and error/retry handling; this is not durable offline synchronization.

[vite.config.ts](../frontend/vite.config.ts) precaches built shell assets, uses `/index.html` as navigation fallback and auto-updates the service worker. There is no configured API runtime caching, IndexedDB card store or offline write queue. Clerk loading and all card reads/writes need the network; an installed app opening its cached shell does not mean it can study offline. Theme preference in [theme.ts](../frontend/src/theme.ts) is separate from study data.
