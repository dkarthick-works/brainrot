# Freestyle — detailed frontend implementation plan

**Status:** Implemented. Verification: 70 frontend unit/component tests, 9 Chromium browser tests, 14 backend tests, and production frontend build passed. Packaged Spring direct-load/refresh checks passed. See `freestyle-verification.md` for coverage and remaining manual checks. Testing stopped at the user's request before commit; no physical-device, real Clerk, or installed-PWA verification is claimed.
**Feature scope:** `docs/freestyle-feature.md` records the agreed behavior.
**Design source:** `prototype/DECISIONS.md`; use prototype source for values/reference, never import or copy its components into production.

## 1. Outcome and boundaries

Build a separate **Freestyle** mode with:
- Anytime entry from the drawer, plus a shortcut on Home's confirmed **Brain Upgraded** state.
- All cards, incrementally loaded, with each fetched page shuffled on the client.
- Question-first, tap-to-flip, either-direction swipe to advance, including before revealing.
- No visible Next button, grades, interval hints, scheduling writes, or saved progress.
- Always-visible **Exit Freestyle**, one-pass completion, **Play again**, and **Back to home**.

Do not add collection-wide shuffle, topic selection, resume, mastery/disclaimer copy, points, timers, review history, or offline deck caching. Do not change scheduled-review grading or require the user to complete due cards before entering Freestyle.

### One small hosting change, not an API change

**User approved this change during plan discussion.**

In plain terms: clicking Freestyle in the sidebar is handled by React, but refreshing `/freestyle` or opening it directly asks Spring for that address. Spring must return the frontend app so React can show the Freestyle screen. Without this mapping, a direct visit or refresh could return 404. This is the same handling already used for `/cards`, `/topics`, and `/settings`.

The earlier backend estimate needs a qualification: `src/main/java/app/flashcards/WebApp.java` explicitly maps `/topics`, `/cards`, `/settings`, and `/settings/agents` to the SPA. Add `/freestyle` to that mapping so a direct load or refresh works in production. No endpoint, schema, algorithm, database index, or backend session changes are needed.

## 2. Current code: reuse and constraints

| Current file | Relevant behavior | Planned treatment |
|---|---|---|
| `frontend/src/App.tsx` | Shell, drawer, route table, app-wide save banner | Add route/menu item; mount a scheduled-review write owner above routes. |
| `frontend/src/screens/Play.tsx` | Scheduled-review queue, local review writes, private Stack component | Keep Home route/name for now; add safe completion shortcut and move write ownership out of this route. Do not turn it into a generic dual-mode screen. |
| `frontend/src/api.ts` | `api.cards({ topicId, q, after })` already returns pages of 50 | Use with `topicId: null`, `q: ''`; support optional read cancellation without changing endpoint semantics. |
| `frontend/src/types.ts` | `Card`, `Page`, `Topic` match backend | Reuse; no API response/type changes expected. |
| `frontend/src/styles/play.css` | Shared card/stack faces, study scrolling, count, grade bar, done states | Reuse visual classes. Add scoped Freestyle layout rules in a new stylesheet, not global redesigns. |
| `frontend/src/components/Loading.tsx` | Delayed full loader, random stable message | Reuse for initial load and an exhausted loading buffer. |
| `frontend/src/components/ErrorState.tsx` | Standard offline/server copy and retry | Reuse for blocking load errors. |
| `frontend/src/components/SaveError.tsx` | Global banner with a single retry closure, auto-clears after 8s | Do not use for read failures. Existing banner is not durable storage for failed scheduled reviews. |
| `frontend/src/main.tsx` | StrictMode, shared QueryClient, styles | Import Freestyle stylesheet; preserve existing providers. |
| `frontend/src/logic.test.ts` | Vitest pure logic tests only | Keep regression suite; add session and component coverage. |
| `frontend/package.json` | Vitest present; no DOM testing library or Playwright currently installed | Add small DOM/component test setup explicitly; add browser test tooling if making the smoke test automated. |

Important current issues to avoid propagating:
- Scheduled review's `canSwipe = flipped && grade != null` does not apply to Freestyle.
- The existing Stack installs global keyboard handlers and a delayed advancement timeout. Do not blindly reuse those handlers in the new mode.
- Existing card CSS has the essential `will-change: transform`; keep it.
- Existing Home considers an optimistic empty queue done even if review writes are still pending. Gate confirmed completion during this work.
- Existing review retry closures refer to route-local state. A route switch can unmount that state; fix write ownership rather than assuming the banner solves it.

## 3. Finalized UI direction — no separate prototype

The user approved proceeding directly with the existing UI patterns/components. Do not build a separate prototype or require another design approval. Implement the layout below in production, making small placement/copy decisions consistent with the current app. Record the approved drawer and completion-screen additions in the design reference as documentation, not as a prerequisite approval step. Do not reopen unrelated locked decisions.

### 3.1 Update the design reference

In `prototype/DECISIONS.md`:
1. Change the drawer specification to include **Freestyle**, with a distinct icon.
2. Add a Freestyle section with ungraded/ungated swipe rules, page-local shuffle, no Next button, and no saved progress.
3. Add the Home completion shortcut without changing **Brain Upgraded**, next-due copy, or the scheduled-review summary.
4. Specify that `/` remains scheduled review and that Freestyle does not affect its queue/schedule.
5. Document the new header and all state layouts listed below.

No prototype implementation, new reference screenshots, or separate visual sign-off is required.

### 3.2 Layout to implement using existing patterns

- Drawer order: **Freestyle, Topics, Cards, Settings**. Keep the existing bottom alignment and wordmark-to-Home behavior.
- Freestyle header: existing top-left drawer control, centered **Freestyle** title, top-right outlined/text pill **Exit Freestyle**.
- Keep the Shell's normal subpage drawer-opening affordance; the separate Exit control always goes to `/`, not browser-back.
- On narrow screens, use a two-row header if needed so the full exit label remains readable; do not squeeze or overlap it with the title/menu pill.
- Header is outside all loading/error/empty/complete branches. Exit remains visible throughout the mode, except temporarily behind an intentionally open drawer/scrim.
- Stack width up to 640px and height up to 800px, matching the current study layout. Omit the grade footer entirely; let the stack use that space while leaving safe-area/bottom room for its counter.
- Use `Card N` below the stack. Do not show a guessed total.
- Home: primary **Freestyle** action below the existing summary/next-due text. No new disclaimer copy.
- Complete: **Freestyle complete**, primary **Play again**, secondary **Back to home**.
- Empty: **No cards yet**, **Add cards**, **Back to home**. Do not show successful completion for an empty collection.

### 3.3 Production states to verify

Implement and verify in both themes:
- Question; answer; drawer open with Freestyle selected.
- Initial loading; initial offline/server error; empty library.
- Completion; buffer-empty loading; later-page failure with retry.
- Home completion with Freestyle action.

Check long question/answer text, a long topic name, narrow phone, short landscape viewport, desktop width, and reduced motion in the real implementation. No mock/prototype code changes are needed.

## 4. Routing and navigation contract

### 4.1 Route

Use `/freestyle`, rendered by `screens/Freestyle.tsx`.
- Add the route before the wildcard fallback in `App.tsx`.
- Add a drawer item using the existing `MENU` structure and `Icon` convention. A shuffle-style icon is suitable; use existing stroke/color conventions.
- Let the existing route-based current-item logic select Freestyle.
- Drawer navigation closes the drawer as it does today.
- Clicking Freestyle while already on `/freestyle` should simply close the drawer, not restart the session.
- The current Home component can remain named `Play.tsx`. Avoid a rename-only diff across this feature; call it scheduled review in comments/new interfaces.

### 4.2 Session lifetime

- Entering `/freestyle` mounts a fresh local session.
- Opening/closing the drawer does not end the session; card input pauses while it is open.
- Navigating to another route unmounts and discards the session.
- Exit and Back to home navigate explicitly to `/`; they do not call `navigate(-1)`.
- Browser-back navigation is allowed without confirmation. Browser-forward reentry starts again; no queue is reconstructed from history or cache.
- Play again resets the session in place and refetches the first page.
- Refresh starts again; no localStorage, IndexedDB, URL cursor, or persisted query state.

### 4.3 Production serving

Add `/freestyle` to the `WebApp.java` SPA route mapping. Verify both a clean direct request and refresh after deployment; Vite's development fallback alone is not proof that production routing works. Existing API/security route behavior must remain unchanged.

## 5. Component and module structure

Proposed new files:

```text
frontend/src/
  freestyle.ts                        # pure shuffle/session transition helpers + state types
  freestyle.test.ts                   # pure logic tests
  hooks/
    useFreestyleSession.ts            # local session orchestration, requests, cleanup
    useFreestyleSession.test.tsx      # async lifecycle/pagination tests
  screens/
    Freestyle.tsx                     # header + state composition
    Freestyle.test.tsx                # mode behavior and network safety
  components/
    FreestyleStack.tsx                # flip/swipe presentation, no API or grade knowledge
    FreestyleStack.test.tsx           # gesture and timer tests
    ReviewWrites.tsx                 # shell-owned scheduled-review write coordinator
    ReviewWrites.test.tsx            # navigation/retry regression coverage
  styles/
    freestyle.css                    # scoped mode/header/layout styles
  test/
    setup.ts                         # DOM test cleanup/polyfills, if needed
```

Keep these modules small. The hook owns network/session state; the stack owns transient gestures/flip animation. The screen chooses which visual state to show. Only the scheduled-review coordinator may submit review writes.

### Why not generalize the current Stack immediately?

The current Stack combines grades, interval hints, keyboard shortcuts, and scheduled-review advancement. Extracting an all-purpose multi-mode component would increase regression risk. Implement a small Freestyle-specific interaction component using the approved card structure/classes, not prototype code. Reusing CSS is enough initially. A later shared card-face extraction is optional only if the implementation reveals genuinely identical markup; do not make it a prerequisite.

### Component contracts

`Freestyle` receives `menuOpen` from Shell and passes it down as interaction suspension.

`useFreestyleSession()` exposes approximately:
- `queue`, `advancedCount`, initial-load/next-load status, error, and derived screen state.
- `advance(cardId)` — consumes only if that ID is still the top card.
- `retry()` — retries the failed cursor without changing accepted pages.
- `restart()` — invalidates old work, clears state, and begins a fresh request.

`FreestyleStack` receives:
- Up to the next three cards, topic lookup, current counter, `suspended`, and `onAdvance(cardId)`.
- No `quality`, `onReview`, scheduling callbacks, API clients, or interval functions.

Do not import `GRADES`, `nextInterval`, or the review coordinator into Freestyle components.

## 6. Session state and invariants

Use an explicit reducer/pure transitions for accepted session state and refs for immediate request/gesture guards. Do not randomize inside a reducer or React state updater: React may call pure work again in development.

Conceptual session fields:

```text
queue: Card[]                 # accepted, shuffled cards not yet advanced
seenIds: Set<string>          # IDs accepted anywhere in this pass
advancedCount: number         # consumed cards, not graded/reviewed cards
firstPageLoaded: boolean
nextCursor: string | null     # meaningful after first-page success
loadState: idle | loading | error
error: unknown | null
failedCursor: string | null   # first-page null is disambiguated by firstPageLoaded
```

Request-control refs:
- `generation`: increments on restart/unmount; old responses are ignored.
- Current request token and AbortController, preventing concurrent requests.
- Accepted/requested cursor bookkeeping to avoid applying a page twice or looping on a non-progressing cursor.

Invariants:
1. Only one active page request per current session.
2. Accepted page order never changes once appended.
3. A card ID is accepted at most once, including duplicates within one response.
4. Advancing removes exactly the current top card and increments `advancedCount` once.
5. Completion requires first-page success, no queued cards, no outstanding next cursor, and at least one advanced card.
6. Zero cards after the first successful traversal means empty, not complete.
7. A failed request does not clear cards, counters, accepted cursors, or seen IDs.
8. A stale response cannot update a newer session or remounted screen.
9. Nothing in the session changes a `Card` object's scheduling fields.

Avoid storing derived booleans such as both `complete` and `hasMore` when they can disagree with `nextCursor` and queue state.

Memory: discard consumed card objects; retain only remaining/prefetched cards plus seen IDs and cursors. This is incremental content loading, not constant-memory traversal—ID bookkeeping grows with the number of cards visited.

## 7. Fetching, shuffle, retry, and cancellation

### 7.1 API usage

Call the existing card listing with all-topic/no-search arguments:

```text
api.cards({ topicId: null, q: '', after: cursor })
```

Current page size is 50; retain it initially. There is no need to add a limit option or tune page size for this feature before measurements.

Extend the private `request` helper and `api.cards` with an optional AbortSignal argument, preserving existing callers. Pass it into `fetch`. Preserve abort identity instead of wrapping AbortError as `ApiError(0)`; deliberate cancellation must not render an offline error. Alternatively, generation checks remain mandatory even if a mocked transport ignores abort.

Do not refactor every endpoint or introduce a new general-purpose fetching library.

### 7.2 Page acceptance algorithm

For each successful response belonging to the active generation/request:
1. Validate cursor progression before accepting the response; an unexpected repeated/non-progressing next cursor becomes a recoverable load error, not an infinite fetch loop.
2. Filter duplicates against session `seenIds` **and** a local set for this response.
3. Clone the new-card array.
4. Shuffle with Fisher–Yates using an injectable RNG for tests (`Math.random` in production). Do not use `sort(() => Math.random() - 0.5)`.
5. Construct the accepted-page event outside the reducer with its already-shuffled card list.
6. Append cards, update seen IDs, and save the server's original `nextCursor` atomically.
7. Mark the page accepted exactly once.

Important details:
- Never mutate a server response, a TanStack Query cache array, or card fields.
- Never take the cursor from the last shuffled card.
- Empty/deduplicated pages with a progressing next cursor are not end-of-deck; continue fetching.
- A final empty page with `nextCursor: null` can finish a previously nonempty traversal.
- Never promise that two shuffles produce different orders. Randomness can legitimately reproduce an order, especially with one or two cards.

### 7.3 Prefetch policy

- Fetch the first page on mount.
- Fetch one next page when five or fewer queued cards remain and a next cursor exists.
- Start another needed fetch only after the previous one settles and the state is accepted.
- Do not fetch the entire library eagerly; with pages of 50, normal memory holds the remaining current page plus at most one fetched page.
- A visible stack remains usable during prefetch.
- If the buffer empties while a page is loading, show the delayed full loader under the persistent header.
- Stop automatic fetch attempts on error. Explicit retry reuses the failed cursor.
- Do not refetch/reset this local deck on window focus, topic cache invalidation, or connectivity changes. Retry is explicit after an error.

### 7.4 Errors while cards remain

- Keep the stack usable; show a compact, mode-scoped read-failure notice with **Try again** below/near the count.
- Do not use `SaveErrorBanner`: no save failed and no card was undone.
- Use the existing offline/server wording, not new joke text. Use the app's existing compact notice styling; no separate design review is needed.
- When the accepted queue runs out, replace the stack with the existing full `ErrorState`, still under the header.
- Retry does not restart the session or reshuffle accepted cards.

### 7.5 StrictMode, restart, and unmount

- Acquire request guards synchronously, not only by setting a React `fetching` boolean.
- Effect cleanup cancels its owned request. React StrictMode's setup/cleanup/setup sequence must allow the second setup to issue a replacement request; an aborted old request must not leave a busy ref stuck.
- Restart increments generation, aborts old requests, clears local deck/progress/error, resets visual interaction state, and fetches the first page.
- A response/finally from an old request must not clear the new request's guard or overwrite new state.
- On unmount abort reads, clear timers, and ignore remaining async settlements.
- Do not try to abort scheduled-review POSTs through this read lifecycle.

## 8. Topic labels and auxiliary data

Use `useQuery(topicsQuery)` for names, converting the result to an ID-to-name map.
- Cards determine whether a deck is empty; topic counts are not the Freestyle deck source or exact session total.
- Do not block an otherwise loaded card on topic-name loading.
- If names are unavailable, omit the topic label; never show `undefined` or a raw database ID.
- Do not let a topic-list failure replace a successfully loaded deck with a blocking error.
- No new topic endpoint, per-card lookup request, or topic selection UI.

## 9. Card gestures and animation

### 9.1 State local to the stack

Maintain flip state, horizontal displacement, active pointer ID/start coordinates, direction lock, and exit-animation state. Reset when the top card ID changes or the session restarts.

Use a synchronous ref guard as well as render state for advancement, so repeated pointer releases cannot queue multiple exits before React renders.

### 9.2 Tap and swipe rules

Start with current interaction constants as the baseline:
- Movement under 6px in both axes: tap/flip.
- Vertical motion beyond 6px and greater than horizontal motion: lock to vertical scroll for that gesture.
- Horizontal release beyond 100px: advance in that direction.
- Below threshold: snap back without flipping.

These preserve current muscle memory. Check on narrow screens before changing thresholds; don't invent velocity-based dismissal in v1.

Requirements:
- Only the primary pointer/left mouse button participates.
- Ignore additional touches and pointer events from the wrong pointer ID.
- Use `touch-action: pan-y` to preserve native vertical scrolling.
- Handle pointer cancel/lost capture by clearing drag state, not advancing.
- Ignore flip/advance during an exit animation or while the drawer is open.
- Opening the drawer cancels an unfinished drag. A committed exit may finish once, but cannot trigger another card action behind the drawer.
- Do not allow a release outside the card to strand the stack: use pointer capture and clean it up safely.
- Selecting text, if selection is enabled in the final design, must suppress tap-to-flip after a nonempty selection. Existing card CSS disables selection; do not claim selection support without changing/testing it.

### 9.3 Animation completion

- Retain the existing 260ms slide-out duration and card flip timing as baseline values.
- Prefer transition completion plus a bounded fallback timer, guarded so only one path advances. Filter transition events by the relevant element/property.
- Clear fallback timers on unmount/restart; exiting midway through animation must never consume a card in a later session.
- Reset the new card to question, centered position, and its content scroll position to the top.
- Preserve `will-change: transform` on the rotating inner element.
- Reduced motion: disable 3D rotation/large fly-out transitions and consume via the same guarded advance path without waiting for a long timer.

### 9.4 Input semantics and accessibility boundary

- No visible Next control or new global arrow-key advancement shortcut in this version; the alternative is deliberately deferred in `BACKLOG.md`.
- Retain semantic buttons for Exit, retry, completion, and Add cards.
- The top card can be focusable and support local Enter/Space flip; do not register a global handler that hijacks focused Exit/Retry/drawer buttons.
- Only the visible face/top card is exposed to assistive technology; decorative stacked cards and inactive answer face must not announce duplicate/hidden content. CSS `backface-visibility` alone does not hide content from accessibility APIs.
- Use a polite status for the card position/loading as appropriate, without reading an entire answer automatically.
- This is not a claim of full non-gesture accessibility. Record the deferred advancement limitation honestly.

## 10. Scheduled-review navigation safety

This is the main integration risk, separate from the Freestyle deck itself.

### 10.1 Existing problem

`Play.tsx` sends `api.review` and stores success/failure/rollback callbacks against its local session. Leaving `/` unmounts that state. The global save banner survives, but its retry closure can still refer to the unmounted route. Also, the completion screen currently does not wait for `inflight === 0`.

Adding anytime Freestyle entry makes that unsafe path easier to hit. Do not solve it by disabling Freestyle until reviews finish; anytime entry is part of the agreed feature.

### 10.2 Approved approach: save tracking in the persistent parent

**User approved this approach during plan discussion.**

In plain terms: put scheduled-review save tracking in the parent component that stays mounted while screens change, rather than inside Home.

The existing `Shell` component in `frontend/src/App.tsx` is that parent. “App shell” is only a name for the shared layout/navigation, not a browser feature, new service, or additional framework. It contains the sidebar, current route, and save-error banner. When Home is replaced by Freestyle, this parent remains mounted.

The “review write coordinator” is a small piece of frontend code that tracks pending/successful/failed saves and handles retries. Keeping it under this persistent parent allows saves and recovery to survive screen navigation. Freestyle itself remains read-only.

Add a small context/provider inside the authenticated Shell, above `Routes`:
- Own only scheduled-review submission lifecycle, not Freestyle state or the whole application's data layer.
- Track immutable submission records: local submission ID, card ID/card snapshot, quality, originating Home session ID, status, result/error, and retry-attempt ID.
- Expose submit/retry/pending/failure information and session-scoped status subscriptions to Home.
- Persist in memory across route navigation; clear on sign-out/Shell unmount. Do not promise offline/reload durability.
- Own `api.review`, cache invalidation, and retry callbacks. A retry captures its record ID, not `setState` on a route component.
- Do not automatically retry failed POSTs. The API has no idempotency key; ambiguous network failure may mean the server already accepted a review. Preserve the current explicit retry model and record this API limitation rather than claiming exactly-once delivery.
- Never cancel a POST just because the route changes. An aborted client request does not prove the review was not applied.

### 10.3 Integration with Home

- Home still owns its due queue/visual session, but submits through the coordinator.
- When mounted, reconcile settlements for that Home session exactly once. Use submission IDs, not `reviewed.slice(0, -1)`, to roll back the actual failed card's quality when requests settle out of order.
- Restore a failed card once for the active session and preserve an actionable failure record even if Home unmounts.
- On a fresh Home mount, fetch `/cards/due` only after outstanding review submissions settle so a still-saving reviewed card is not immediately requeued. Header/menu navigation remains usable while waiting.
- Do not apply an old session's queue mutation to a new Home session. A fresh due fetch supplies the authoritative queue; failed submissions remain visible as unresolved outcomes.
- A failed review should not silently replay later against a newer successful review for the same card. Retire/supersede its retry record after a successful replacement review, using per-card attempt tracking.
- Home completion is confirmed only after its queue/prefetch work and all relevant writes have settled successfully or unresolved failures have been surfaced. Do not present the completion shortcut while a review is still pending/failed.
- If topic data failed, do not infer “has cards” merely from a failed lookup. Use reviewed-session evidence or successfully loaded counts to distinguish completion from the zero-card state.

### 10.4 Failure visibility after navigation

Keep the existing banner for the immediate save-failure notification, but store the failure independently of its 8s timeout.
- Provide a small shell-level unresolved-review notice/status with a **Back to home** action when a review fails off-route; a retry action, if provided, must invoke the coordinator.
- Keep a durable-in-memory recovery path on Home after the transient banner is dismissed.
- Do not label this a Freestyle save failure; identify it as a scheduled review that could not be saved.
- Avoid stacking duplicate notices for the same failure; choose concise wording and placement using existing notice patterns during implementation.

This is a targeted review-write lifecycle correction, not a rewrite of the scheduling algorithm or due-queue pagination. It needs its own regression tests before adding the new entry points.

## 11. Home completion shortcut

In `Play.tsx`:
1. Preserve **Brain Upgraded**, existing due-time copy, and current-session summary.
2. Add a primary **Freestyle** button navigating to `/freestyle` only in the confirmed completed-with-cards branch.
3. Preserve Add cards / Connect an agent for no-card users.
4. Show a loading/saving state instead of premature completion while the final review request is outstanding.
5. Surface unresolved review failure/recovery rather than hiding it behind a completion CTA.
6. Returning from Freestyle mounts Home afresh and obtains current due state. Freestyle never supplies a “completed due” flag.

No change to grade values, interval hints, reveal/grade gating, or review-on-swipe semantics.

## 12. Styling and integration details

Add `styles/freestyle.css`, imported from `main.tsx` after base card styles.
- Scope with `.freestyle-*` / `.freestyle` to avoid changing `.grades`, edit-card dialogs, or Home geometry.
- Use existing tokens (`--bg`, `--card`, `--line`, `--ink`, `--muted`, `--good`, `--on-grade`) and typefaces.
- Keep safe-area bottom padding and usable margins on 100dvh layouts.
- Account for a header height larger than the normal 68px on narrow screens.
- Reserve enough room for count/read-error notice so it does not cover the card hint.
- Keep the header in normal flex layout, with a separately constrained central title, rather than relying on overlapping absolute text.
- Drawer/scrim z-index remains above the mode; Exit must not float above the open drawer.
- Add Freestyle-scoped reduced-motion rules for both swipe and flip. Do not globally change Home animation as incidental cleanup.
- Keep completion actions the existing rounded/teal visual language, without celebration effects.

No new UI framework, routing library, state-management package, animation library, or runtime dependency is needed.

## 13. Testing plan

### 13.1 Test setup

Retain Vitest. Add `@testing-library/react` and a compatible DOM environment (for example `jsdom`) as development dependencies for hooks/component tests. Add `@testing-library/user-event` only if its keyboard/click helpers are used. Update the lockfile and test config explicitly.

Use mocked `api.cards`/topics and deferred promises for orchestration tests. Do not add an authenticated API bypass to production code. Supply local component/provider test wrappers instead.

Mock pointer capture, `matchMedia`, and transition/fake-timer behavior where the DOM environment lacks them. Real touch/scroll/compositing behavior still needs browser/manual validation.

### 13.2 Pure shuffle and reducer tests

- Empty and single-card arrays; no mutation of input array/card objects.
- Deterministic Fisher–Yates output with injected RNG.
- Output IDs match input IDs exactly; no dropped/duplicated cards.
- Page acceptance deduplicates within a page and across pages.
- Next cursor remains the response cursor, regardless of shuffle order.
- Advance with stale/non-top ID is ignored.
- Accepted page/order stable across unrelated renders/events.
- Empty page with next cursor does not finish; final empty page can finish.
- Empty first traversal gives empty state, not completed.
- Reset clears all local session state.

Do not test randomness by requiring every shuffle to differ from input or prior output; that is flaky and mathematically incorrect.

### 13.3 Hook/network lifecycle tests

- Initial request uses null topic, empty search, first cursor.
- Prefetch triggers at five remaining, not above it.
- Only one active request per session, including rapid advances and StrictMode.
- Old aborted response cannot update a restarted/unmounted session.
- Old `finally` cannot clear a newer in-flight guard.
- Initial failure → retry succeeds.
- Prefetch failure preserves cards/count/order; retry uses the failed cursor.
- Buffer-empty loading/error is not treated as completion.
- Repeated cursor triggers an error instead of looping.
- Topic lookup failure does not block loaded cards.
- Play again refetches and runs shuffle again; no expectation of a necessarily different order.
- Abort is not displayed as an offline failure.

### 13.4 Component and gesture tests

- Question shown first, answer hidden semantically until flip.
- Tap flips both ways; next card resets face and scroll.
- Left/right swipe advances exactly once without reveal/grade.
- Short horizontal drag snaps back; vertical movement does not flip/advance.
- Pointer cancel/additional pointer does not advance.
- Repeated input during exit is ignored.
- Drawer suspension prevents background interaction.
- Focused Exit/Retry button receives Enter/Space normally, without global interception.
- Exit present in loading/error/empty/playing/complete states.
- No visible Next, grades, difficulty hints, or scheduling labels.
- Reduced-motion path advances once without waiting for a long animation.
- Unmount/restart during animation clears pending callbacks.

### 13.5 Scheduled-review regression tests

- Last swipe does not show confirmed completion while its POST is pending.
- Navigate to Freestyle with a POST pending: it continues; success invalidates cards/topics without updating an obsolete screen.
- Off-route failure remains actionable after the transient banner expires.
- Retry uses the stored card/quality record, not an unmounted local callback.
- Out-of-order failures roll back the correct submission's stats and restore only that card once.
- Return Home while POST pending waits before authoritative due fetch.
- Old-session settlement cannot mutate a new Home queue.
- Successful replacement review supersedes a stale failure retry.
- Home still requires reveal + grade before scheduled-review swipe.

### 13.6 Read-only safety assertions

For Freestyle-only test sessions:
- Assert no call to `api.review`, `api.updateCard`, `api.createCard`, or `api.deleteCard`.
- Freeze card fixtures or compare scheduling fields before/after every transition.
- Network/browser smoke test permits deck/topic GETs and no card/review writes caused by Freestyle. Distinguish an intentionally pending Home review from writes initiated by the new mode.

### 13.7 Integration/browser smoke coverage

There is no existing Playwright dependency/test setup despite the older frontend plan mentioning it. Either add a small explicit Playwright setup in a test-only commit, or document and perform the following manual smoke flow. Do not claim an automated E2E suite exists when it does not.

Fixtures/scenarios:
1. User with due and future-due cards enters from drawer before completing reviews.
2. User with no due cards enters from Home shortcut.
3. More than 100 cards, including duplicate test responses and one failing later page.
4. Empty library; single card; long multi-paragraph question/answer.
5. Exit during first fetch, prefetch, and slide-out; reenter/restart.
6. Home review POST delayed/fails across navigation, then recovery.
7. Direct `/freestyle` load and refresh under the packaged Spring deployment.
8. Light/dark, 390×844 phone, narrow/short viewport, desktop, and reduced motion.
9. Back/forward navigation starts a new Freestyle session; drawer open/close does not.

A browser test can use route-level API stubs and a test-only auth wrapper without requiring real Clerk credentials for core interactions. Real authenticated deployment smoke testing is a separate verification step.

## 14. Implementation sequence and checkpoints

### Phase 1 — Record the finalized direction

- Record approved functional additions in the design reference alongside implementation.
- Reuse existing layout, card, drawer, button, loading, and error patterns.
- Proceed directly to production implementation; no prototype, screenshot deliverable, or design approval gate.

**Exit criterion:** the agreed scope is documented. This is a brief documentation step, not a separate design phase.

### Phase 2 — Navigation-safe scheduled-review writes

- Add Shell-owned write coordinator and tests.
- Route Home submission/retry through it.
- Correct final-save completion gating and submission-specific failure rollback.
- Ensure off-route failure recovery remains visible after banner timeout.

**Exit criterion:** existing review behavior passes regression tests, including navigation away during success/failure. This can ship independently as a reliability fix.

### Phase 3 — Pure Freestyle session logic

- Implement shuffle and reducer transitions, with tests first where useful.
- Implement paginated session hook, single-request guards, abort/generation cleanup, retry/restart.
- Add topic-name lookup without making it a blocking dependency.

**Exit criterion:** multi-page traversal, shuffle, retries, and lifecycle invariants pass without relying on UI gestures.

### Phase 4 — Freestyle UI and interaction

- Build header and visual state branches.
- Build FreestyleStack with ungraded tap/swipe, cancel handling, animation cleanup, face semantics, and reduced motion.
- Add scoped styling and tests.

**Exit criterion:** complete usable session in isolation, all visual states covered, no write dependencies.

### Phase 5 — App integration and serving

- Add `/freestyle` route, drawer item, and confirmed Home completion action.
- Pass drawer-open state to pause interactions.
- Add the Spring SPA fallback entry and a focused route-serving check.
- Update `FRONTEND_PLAN.md` route/API tables and note that Freestyle is read-only; link this plan rather than duplicating it wholesale.

**Exit criterion:** both entry points work, leaving/returning respects scheduled-review state, direct production route works.

### Phase 6 — Regression and release validation

- Run unit/component tests and production build.
- Run browser/manual smoke scenarios and packaged route check.
- Check new UI states for consistency with existing app patterns in both themes; no new prototype references are required.
- Confirm no scheduling writes/field changes from Freestyle.
- Update feature/plan status with actual implementation and verification results.

**Exit criterion:** acceptance checklist below passes, or any remaining limitation is explicitly agreed and documented.

## 15. Commands and verification reporting

From `frontend/`:

```sh
npm test
npm run build
```

If browser tooling is added, provide its exact script and prerequisites in the development documentation; do not list a nonexistent command as already available.

For the small Spring route change, add/run the appropriate focused route test using the repository's existing Spring test conventions, and verify the packaged SPA with built static assets. Use `./mvnw test` for backend regressions when changing that mapping.

Report test/build results separately from manual checks. Do not imply that jsdom validates touch scrolling, rendering/compositing, real authentication, or installed-PWA behavior.

## 16. File-change checklist

### New production frontend files
- `src/screens/Freestyle.tsx`
- `src/components/FreestyleStack.tsx`
- `src/hooks/useFreestyleSession.ts`
- `src/freestyle.ts`
- `src/styles/freestyle.css`
- `src/components/ReviewWrites.tsx`

### Existing production frontend files
- `src/App.tsx`: provider, route, drawer item.
- `src/screens/Play.tsx`: write integration, safe completion, Freestyle action.
- `src/api.ts`: optional abort signal for card-list reads, preserve cancellation identity.
- `src/main.tsx`: stylesheet import.
- `src/components/SaveError.tsx`: only if needed to separate generic transient notification from coordinator-owned review recovery; preserve other screens' behavior.
- `src/styles/app.css`: only minimal shared failure-notice styling if needed; avoid global card redesign.

### Tests/tooling/docs
- New pure/hook/component regression tests and test setup/config.
- `frontend/package.json` and lockfile for test-only dependencies.
- Optional explicit Playwright setup if automating smoke flows.
- Design-reference documentation of the approved additions; no prototype code or new screenshots required.
- `FRONTEND_PLAN.md` navigation/API/status updates.
- `docs/freestyle-feature.md` status/links after implementation.
- `src/main/java/app/flashcards/WebApp.java` plus a focused serving test: `/freestyle` SPA fallback only.

No `Card`, `CardService`, SM-2, repository, database, or API-controller changes are planned.

## 17. Effort estimate

Indicative engineering time for one developer, not a delivery commitment:

| Workstream | Estimate |
|---|---|
| Record finalized UI direction | Included in implementation |
| Review-write navigation safety and regression tests | 1–2 days |
| Freestyle pagination/shuffle/session logic and tests | 0.5–1 day |
| Card gestures, header/states, styling, integration | 1–1.5 days |
| Test setup, browser/manual regression, production serving verification | 0.5–1 day |
| **Total** | **About 3–5.5 developer days** |

The Freestyle read-only deck is not the largest risk; review-write lifecycle correction and real gesture testing are. Estimates assume the existing design system/API remain unchanged. A new full E2E authentication setup or changes after visual review can extend this.

## 18. Final acceptance checklist

- [ ] Sidebar Freestyle works whether due cards exist or not.
- [ ] Confirmed Home completion offers Freestyle; no-card Home retains its existing actions.
- [ ] `/freestyle` direct load/refresh works in production.
- [ ] All-topic, all-due-state cards load across pages.
- [ ] Each accepted page is shuffled once; its server cursor is preserved.
- [ ] No duplicate cards or infinite cursor loops in a pass.
- [ ] Question-first flip and both swipe directions work without reveal/grade gating.
- [ ] No visible Next button, grade bar, interval hints, mastery warning, or resume behavior.
- [ ] Header and Exit remain available in every state.
- [ ] One pass ends with Play again / Back to home; empty is distinct from complete.
- [ ] Exit/reload discards progress; opening the drawer does not.
- [ ] Prefetch/retry never loses progress, reshuffles accepted cards, or reports false completion.
- [ ] Unmount/restart/StrictMode do not cause stale updates or duplicate advancement.
- [ ] Long-content scroll, narrow viewports, both themes, and reduced motion verified.
- [ ] Freestyle initiates no review/card writes or scheduling/stat changes.
- [ ] Scheduled-review writes/retries remain safe and actionable across navigation.
- [ ] Existing scheduled-review grading/swipe behavior remains unchanged.
- [ ] Tests/build results and actual manual checks recorded.
