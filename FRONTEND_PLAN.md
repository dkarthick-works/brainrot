# brainrot Frontend Plan

A web app installable as a PWA, built from the locked design in `prototype/`. The Android app comes later, in Kotlin, and is out of scope here.

**Sources of truth**
- **Design:** `prototype/DECISIONS.md` (sections marked 🔒 are final) and `prototype/screens/` (every state, light and dark).
- **Exact values:** `prototype/src/index.css` (colors, spacing, radii, motion timing). Copy the values, not the components.
- **API:** the backend code (`src/main/java/app/flashcards/`) and `BACKEND_PLAN.md` §4. `prototype/src/mock.ts` only mirrors it.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Build | Vite + React + TypeScript, single-page app |
| Auth | `@clerk/react` (`@clerk/clerk-react` is deprecated): sign-in, `openUserProfile()`, standalone `getToken()` |
| Server data | TanStack Query |
| Routing | React Router |
| Styling | Plain CSS with the prototype's CSS variables (one `tokens.css`, plus one CSS file per screen) |
| Components | Hand-built; native `<dialog>` for modals; no UI library |
| PWA | `vite-plugin-pwa` |
| Tests | Vitest (logic + jsdom/Testing Library components), Playwright Chromium with test-only auth; real authenticated smoke remains manual |

Not used: Next.js (no server rendering needed behind a login), shadcn (the warm palette replaced the black/white/grey look it was chosen for).

---

## 2. Layout

```
frontend/
  index.html
  vite.config.ts            # dev server on port 8089 (backend CORS default: ALLOWED_ORIGINS=http://localhost:8089)
  public/                   # PWA icons
  src/
    main.tsx                # ClerkProvider, QueryClientProvider, Router
    api.ts                  # fetch wrapper + typed endpoint functions
    types.ts                # Card, Topic(View), ApiKey, Due, Page, CreateResult, MoveResult
    styles/tokens.css       # palette, dark mode, data-theme, fonts, focus ring
    components/             # SwipeRow, Composer, Dialog, Segmented, Loading, ErrorState, SaveError, Drawer, TopBar
    screens/                # Play, Topics, Cards, EditCard, Settings, AgentAccess
```

One folder per concern, no deeper nesting until a folder gets crowded.

---

## 3. Routes

| Path | Screen | Top-left button |
|---|---|---|
| `/` | Play | Two-line menu pill (opens drawer) |
| `/freestyle` | Freestyle (read-only practice) | Back arrow (opens drawer); persistent Exit Freestyle → `/` |
| `/topics` | Topics | Back arrow (opens drawer) |
| `/cards?topic=<id>` | Cards (no `topic` = All) | Back arrow (opens drawer) |
| `/settings` | Settings | Back arrow (opens drawer) |
| `/settings/agents` | Agent access | Back arrow (goes back to `/settings`) |

Signed-out users get Clerk's `<SignIn />`; when a session expires Clerk sends them back to it (no error screen).

---

## 4. API layer

- `api.ts`: one `request(path, init)` that adds `Authorization: Bearer ${await getToken()}`, sends JSON to `VITE_API_URL`, and throws a typed error carrying the HTTP status and the `ProblemDetail` `detail` text.
- Errors split the way the design does:
  - **Specific (409 / 400):** shown where they happen ("*Name* already exists", "Can't delete *Name*…"). The text comes from the design, not the server message.
  - **General (network failure / 5xx):** "Brain.exe stopped working" plus the plain line (no connection vs. server error).
- **Query keys:** `['topics']` (newest first, so a new topic shows at the top), `['cards', topicId, q]` (infinite), `['api-keys']`. Play keeps its due queue in component state (fetched on each visit).
- **MCP URL:** derived from `VITE_API_URL`'s origin + `/mcp` (same host as the API); no separate env var.

### Per screen

| Screen | Reads | Writes |
|---|---|---|
| Play | `GET /cards/due?limit=20` | `POST /cards/{id}/review` `{quality}` |
| Freestyle | `GET /cards?limit=50&after=` (all topics), `GET /topics` for labels | None |
| Topics | `GET /topics` | `POST /topics`, `PATCH /topics/{id}`, `DELETE /topics/{id}` |
| Cards | `GET /cards?limit=50&after=&q=&topicId=` (infinite) | `POST /cards` (one card per batch), `PATCH /cards/{id}`, `DELETE /cards/{id}` |
| Agent access | `GET /api-keys` | `POST /api-keys`, `DELETE /api-keys/{id}` |

### Behaviour that matters

- **Optimistic writes:** every write updates the cache first, rolls back on failure, and shows the `SaveError` banner ("Couldn't save. Your change was undone." with Try again re-running the same mutation, auto-clear after 8s). On success, invalidate the affected keys: topic counts change when cards are added, moved or deleted.
- **Play queue:**
  - Keep a local queue from `/cards/due`.
  - When 5 or fewer remain and `dueCount` says more exist, prefetch the next batch, skipping ids already seen this session (`BACKEND_PLAN.md` §4).
  - The counter shows `position / dueCount`.
  - A review is sent when the card is swiped away (not when the difficulty is tapped), so changing your mind before swiping costs nothing.
- **Brain Upgraded:**
  - "Next card due in …" comes from `nextDueAt` on `/cards/due` (null → "Nothing else is scheduled.").
  - Session stats ("6 reviewed · 5 Good or Easy") are counted on the device.
- **Brand-new user:** `dueCount === 0` and `/topics` shows every `cardCount` at 0, so show "Let's put those brain cells to work."
- **Interval hints** on the grade buttons: port `nextInterval` from the prototype (mirrors `Sm2.java`). Update it if the backend's Hard/Good/Easy change in `BACKLOG.md` lands.

---

## 5. Components to port from the prototype

Rebuild these cleanly; the prototype is reference only.

| Component | Notes |
|---|---|
| `SwipeRow` | Pointer events, one open row at a time (parent-owned), `touch-action: pan-y`, arrow-key fallback. Topics: Rename + Delete (Rename only for the default topic). Cards: Edit + Delete. Keys: Revoke. |
| Card flip | 3D flip shared by Play and EditCard. The rotating element needs `will-change: transform` (Chrome repaint bug, see DECISIONS). |
| Play swipe | Locked until flipped **and** graded; rubber-band when locked. |
| `Composer` | Bottom chat bar, 32px side inset. Modes: add topic, rename topic, two-step add card, name a key. |
| `Dialog` | Native `<dialog>` + `showModal()`: confirmations, edit card, key reveal. |
| `Segmented` | Theme picker; same look as the grade bar. |
| `Loading` | 37-message master list, random per load, 300ms delay. Variants: full (Play) and list. |
| `ErrorState` / `SaveError` | One headline "Brain.exe stopped working" + plain line + Try again. Banner for failed saves. |
| `Drawer` | Wordmark (taps to Play), Freestyle / Topics / Cards / Settings at the bottom, current item shaded. |

---

## 6. Theme & PWA

- **Theme:** System / Light / Dark from Settings, stored in `localStorage` (read inside try/catch), applied as `data-theme` on `<html>`. The CSS already supports it.
- **Fonts:** Inter 400/500/600 and Space Grotesk 700. Self-host them for the PWA (offline shell, no third-party request) instead of loading them from Google Fonts.
- **PWA:**
  - Manifest: name "brainrot", `theme_color` / `background_color` = `#f4f1ea`, `display: standalone`, portrait.
  - Icons: the wordmark "b." on the paper color.
  - The service worker caches the app shell only. Data needs the network; offline shows the "Couldn't reach brainrot" error.
- **iOS:** respect `env(safe-area-inset-*)` (already in the CSS); add `apple-touch-icon` and `apple-mobile-web-app-capable`.

---

## 7. Phases

Each phase ends with the screen matching its screenshots in light and dark.

1. **Scaffold:** `frontend/`, Vite on 8089, tokens + fonts, ClerkProvider + SignIn, Router, Drawer/TopBar shell, `api.ts`.
2. **Play:** due queue + prefetch, flip, grading, locked swipe, review mutation, Brain Upgraded, brand-new user.
3. **Topics:** list, search when there are more than 10, composer add/rename, swipe Rename/Delete, delete dialogs, tap opens Cards.
4. **Cards:** chips, search, infinite list, due labels, two-step add, flashcard EditCard (topic move), swipe Edit/Delete.
5. **Settings + Agent access:** Clerk user, `<UserProfile />`, sign out, theme, keys list/create/reveal-once/revoke, copy buttons, MCP URL from config.
6. **Loading & errors everywhere:** loading/error states on every screen, SaveError on every mutation, offline check.
7. **PWA + deploy:** ✅ manifest, icons (`public/`, "b." in Space Grotesk as a path), service worker (app shell only), iOS tags. Shipped **inside the backend jar**: the root `Dockerfile` builds `frontend/` and copies `dist/` into `classpath:/static`; Spring serves it (`WebApp.java`: client routes forward to `index.html`, `/assets/**` cached forever, everything else `no-cache`). One Coolify service, one domain, no CORS in production. Deploy on Coolify:
   - Existing backend resource (Dockerfile build pack, repo root). Build variable: `VITE_CLERK_PUBLISHABLE_KEY=<pk_live_…>`. The API URL is `/api` (`frontend/.env.production`).
   - Backend env: `ALLOWED_ORIGINS=https://<domain>` (still needed: it is checked against the Clerk token's `azp`), `CLERK_ISSUER=<production Clerk issuer>`.
   - Clerk dashboard (production instance): add the domain.
8. **Tests:**
   - Vitest: `nextInterval`, due label, untilLabel, queue prefetch/dedupe.
   - Planned manual authenticated smoke: sign in → grade a card → add a topic → add a card → edit it → delete it. Freestyle browser coverage is installed: `npm run test:browser` (first run: `npx playwright install chromium`); see `docs/freestyle-verification.md`.
9. **Freestyle:** implementation specification and verification checklist: [`docs/freestyle-implementation-plan.md`](docs/freestyle-implementation-plan.md). Local paginated, page-shuffled, unscheduled practice; no persistence or writes. Scheduled-review submission ownership lives above routes, with durable-in-memory recovery and confirmed-save completion gating. Explicit POST retries remain potentially ambiguous (no API idempotency key).

---

## 8. Open items and backend gaps

| Item | Impact | Proposal |
|---|---|---|
| **Hard / Good / Easy give the same interval** | Grade hints look identical | `BACKLOG.md` → SM-2 extras. |
| **Clerk account deletion leaves data** | Orphaned data | `BACKLOG.md` → webhook. |
| **Move cards to…** | Topics with cards can't be deleted | `BACKLOG.md` → design it in the prototype first. |
| **Wide screens** | Design is phone-only; on desktop it's a narrow column | Ship centered at max ~480px for v1; design a wide layout later in the prototype. |
| **Deploy shape** | Frontend-only changes redeploy the backend | Decided: served from the Spring jar (one service). Split out to nginx later if deploys need decoupling. |
| **Version control** | `fshs/` isn't a git repo | `git init` before phase 1. |

Out of scope: Android (Kotlin, later), bulk card actions, "Review ahead", notifications, daily limits / time zones (backlog).
