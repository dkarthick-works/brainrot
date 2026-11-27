# Freestyle — feature draft

**Status:** Implemented using existing patterns/components. See [implementation plan](freestyle-implementation-plan.md) and [verification results](freestyle-verification.md). No separate prototype or design approval was required.
**Previously called:** Play. The feature is now named **Freestyle** to distinguish it from scheduled review (currently named Play internally).
**Workflow:** Draft the feature → plan implementation → implement. This file records the feature, not an implementation plan.

**Implementation reading guide:** The numbered sections retain the agreed feature requirements and historical design context, not independent execution evidence. Current source is [Freestyle.tsx](../frontend/src/screens/Freestyle.tsx), [FreestyleStack.tsx](../frontend/src/components/FreestyleStack.tsx), [useFreestyleSession.ts](../frontend/src/hooks/useFreestyleSession.ts) and [freestyle.ts](../frontend/src/freestyle.ts). Actual hints are “Tap to reveal · Swipe to continue” / “Tap for question · Swipe to continue”. Study-card backs show answer text without repeating the question. Scheduled-review recovery lives in [ReviewWrites.tsx](../frontend/src/components/ReviewWrites.tsx); its records survive route changes, not reload or sign-out. The implementation plan is historical intent; use [data semantics](data-model.md#frontend-state-and-network-limits) and [verification](freestyle-verification.md) for implemented behavior and checks.

## 1. Goal

Give the user a simple way to practise all their flashcards whenever they want, including after Home shows **Brain Upgraded**. Freestyle is optional, ungraded practice: see a question, tap to reveal its answer, and swipe to continue. Leave at any time.

Freestyle never changes spaced-repetition timing and never counts as completing scheduled reviews.

## 2. Confirmed experience

### Entry points

- Add **Freestyle** to the existing sidebar/drawer, available anytime—even when scheduled cards are still due.
- Also offer **Freestyle** on the **Brain Upgraded** completion state when no cards are due and the user has cards. This includes later visits with no reviews in that visit.
- Keep the existing completion headline, next-due message, and scheduled-review session summary.
- Do not launch Freestyle automatically.
- Keep the existing Add cards / Connect an agent first-use state for a user with no cards. The sidebar remains available; entering Freestyle with no cards offers Add cards / Back to home.

The sidebar's anytime access supersedes the initial suggestion to restrict the mode to users who have finished their due cards. The completion-screen shortcut remains a second entry point, not an eligibility rule.

### Card interaction

- Show a clear **Freestyle** label.
- Question first; tap to reveal the answer. Tapping again may return to the question.
- Swipe either left or right to advance. Both directions mean next, not correct/incorrect.
- Swiping is allowed before revealing the answer. No grading or reveal requirement gates progression.
- No visible **Next card** button in the initial design; keep the UI clean. A non-swipe advancement alternative is deferred to `BACKLOG.md`, with its accessibility limitation acknowledged.
- Every new card starts on its question face.
- Reuse the familiar stack presentation, topic label, and long-content scrolling behavior, with light/dark themes and reduced-motion support.
- Front hint: **Tap to reveal answer**. Back hint: **Swipe for next card**.
- Vertical scrolling or selecting text must not accidentally flip or dismiss a card.
- No grades, repetition intervals, due badges, timer, or scheduling writes.
- No mastery claims and no user-facing warning about mastery or memory benefits in v1. Any future explanatory copy is a backlog consideration, not a launch requirement.

### Exit and completion

- Always show **Exit Freestyle**, including during loading and errors.
- Exit immediately, without confirmation or saving progress; return to Home and refresh the due state.
- End after one pass through all available pages. Do not loop automatically.
- Completion headline: **Freestyle complete**. Actions: **Play again** and **Back to home**.
- Play again starts a new pass with newly shuffled pages.
- Do not reuse **Brain Upgraded** or call Freestyle cards “reviewed” in scheduled-review statistics.
- Cards becoming due during Freestyle do not interrupt it. Home shows the actual due queue on return.

## 3. Deck, shuffle, and session lifetime

- Include all the user's cards across all topics: future-due cards, due cards, and cards just reviewed.
- Topic selection is deferred.
- Load incrementally through the existing paginated card-list API; do not download the entire collection before starting.
- **Shuffle is required at launch:** randomly shuffle each fetched page on the frontend before appending it to the session queue. Keep that order stable while playing; do not reshuffle on rerenders or retries of an already accepted page.
- For example, if a page contains 10 cards, shuffle those 10; shuffle the next page separately when fetched. Page size is an implementation choice, not fixed to 10.
- This is **page-local shuffle**, not uniform random order across the collection. The current API returns newest-first pages, so early pages still favor newer cards. This limitation is accepted for v1; collection-wide randomization is recorded in `BACKLOG.md`.
- Client-side page shuffle requires no new backend shuffle parameter or backend session state. Keep the original response's `nextCursor`; do not derive pagination from the shuffled card order.
- Deduplicate by card ID within a session. Retrying a fetch must not replay already accepted cards.
- Prefetch before the loaded queue empties. Only declare completion after the final card and when no next cursor remains.
- Show **Card 12** while the total is unknown; do not present the first loaded page's count as the deck total. A reliable total could support **12 / 120** later.
- Progress exists only in memory. Exit or reload discards it. No saved progress or resume feature is needed.
- Do not promise a transactional snapshot: additions during a session may appear only in a new session; loaded cards may retain their loaded text after edits/deletions elsewhere.

## 4. Non-negotiable separation from scheduled review

- Never submit a review, including a fake Good/Easy review.
- Never write scheduling values and restore them afterward.
- Never call `POST /api/cards/{id}/review` or write card data from Freestyle.
- `dueAt`, `interval`, `easeFactor`, `repetitions`, `lastReviewedAt`, and `updatedAt` remain unchanged by Freestyle.
- Keep the Freestyle queue and progress separate from Home's due queue and quality/session summary.
- Playing due cards does not mark them complete; they remain due until a real scheduled review occurs.

## 5. Benefits and accepted limitations

| Choice | Benefit | Limitation / handling |
|---|---|---|
| Anytime sidebar access | Practise when desired, without waiting | Does not replace or complete due reviews; use the distinct Freestyle label. |
| No grading | Minimal interaction overhead | Swiping does not demonstrate mastery; do not make mastery claims. No disclaimer required at launch. |
| All cards | Simple scope, includes future-due material | Large decks can be tiring; immediate exit and incremental loading are required. |
| Per-page shuffle | Variation at launch with the existing API | Does not eliminate recency bias across pages; improve in the backlog. |
| No saved progress | No persistence or resume complexity | Every new session starts again. This is intentional, not a missing resume requirement. |
| No visible Next button | Clean, gesture-based UI | Non-swipe advancement is deferred, so some users/input methods may not be able to progress. |

Extra exposure can affect real-world recall even though the scheduler is untouched. This is an internal design consideration, not a claim that Freestyle has no memory benefit and not proposed launch copy.

## 6. Edge cases

- **No cards:** offer Add cards / Back to home; do not show a misleading completed session.
- **One card:** flip and swipe work normally; advancing ends the session.
- **Multiple pages:** preserve cursors independently of shuffle and distinguish a temporarily empty buffer from completion.
- **Load failure:** use existing app error patterns with retry and exit. Preserve accepted pages and local progress on later-page failures; never report completion because a fetch failed.
- **Offline:** loaded cards may remain usable; loading further pages needs connectivity. No offline-deck promise.
- **Concurrent library changes:** tolerate loaded snapshots and deduplicate IDs. Restart for a fresh pass.
- **Session expiry:** existing sign-in handling applies; there are no Freestyle review writes to recover.
- **Pending or failed scheduled-review saves:** sidebar access must not discard a pending review or hide its failure/retry path. The Home shortcut must not treat an optimistic empty queue as confirmed completion. Resolve mutation ownership and navigation safety during implementation planning.

## 7. Fit with the current application and design

- `frontend/src/screens/Play.tsx` currently implements scheduled review at `/`. Keep the concepts distinct: **Home / scheduled review** versus **Freestyle**. Any internal renaming and the new route belong in the implementation plan.
- Existing `GET /api/cards` returns `cards` and `nextCursor` in newest-first order. The frontend exposes this as `api.cards(...)`. This is sufficient for page-local client shuffle; no backend change is expected for v1.
- Do not use `/api/cards/due` as Freestyle's deck source: it excludes future-due cards. Home may still use it to refresh due state.
- `prototype/DECISIONS.md` currently locks the drawer to Topics / Cards / Settings and has no Freestyle completion action. The user has now requested these two design additions. Document these approved additions alongside implementation, without changing scheduled-review grading/swipe rules. The user explicitly waived a separate prototype and design-approval step; reuse the existing app patterns/components.
- This is not the previously rejected **Review ahead**: Freestyle never submits early scheduled reviews. The prototype-only **Replay mock deck** link is not a production implementation.
- No application code, prototype code, or locked design text is changed by this draft update.

## 8. Deferred work

Tracked in `BACKLOG.md`:
- Collection-wide shuffle beyond page-local shuffle.
- A non-swipe Next alternative/accessibility review.
- Optional learning-expectation copy, only if needed later.

Topic selection is deferred; v1 covers all cards. Previous-card navigation and a temporary “see again” pile remain possible future ideas, not requirements. Saved progress/resume is explicitly not needed.

## 9. Next step and acceptance criteria

Historical implementation plan (approved to proceed without a separate prototype): [`freestyle-implementation-plan.md`](freestyle-implementation-plan.md). It covers drawer and completion entry points, design states, route/naming choices, page-local shuffle, read-only pagination, pending scheduled-review writes, tests, and phased delivery. Implementation is present in source `8c2a9bf3ce5e2c609cafb8dbef920f5ef3026131`, including the Spring `/freestyle` SPA forward; no backend API change was introduced. See [verification results and remaining manual checks](freestyle-verification.md).

Minimum acceptance checks:
- Freestyle is accessible from the sidebar with or without due cards.
- Home offers Freestyle when scheduled reviews are complete and cards exist, including on a fresh visit.
- All topics and due states are eligible; all fetched pages can be traversed once.
- Every fetched page is shuffled before presentation; cursors still work correctly and accepted pages are not replayed on retry.
- Question-first, tap-to-flip, and either-direction swipe work; unrevealed cards can be advanced.
- No grades, interval controls, or visible Next button appear.
- Exit is always available; one pass ends with Play again / Back to home.
- No progress is saved after exit/reload.
- No review/card-write requests occur; scheduling fields and scheduled-review statistics stay unchanged.
- Home restores the actual due state on return, including any due cards seen in Freestyle.
- Empty, single-card, multi-page, failed-load, long-text, and reduced-motion cases behave sensibly.
