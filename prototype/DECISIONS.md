# Prototype design decisions

**App name:** brainrot (always lowercase).

The prototype is for settling the design only. It has no code-level impact on the real frontend.

## Screenshots
`screens/` has each screen's states at phone size (390×844). File names are `<screen>-<light|dark>-<n>-<state>.png`.
- **Play** (light and dark): `1-question` (before tapping), `2-answer` (flipped, no difficulty yet), `3-graded` (Good picked, swipe unlocked), `4-menu` (drawer open), `5-brain-upgraded` (finished today's cards), `6-fresh` (brand-new user, no cards), `7-loading` (app start; the message is random each time), `8-error` (page can't load: light shows no connection, dark shows server error). The "Replay mock deck" link in `5` is prototype-only.
- **Topics** (light): `1-list`, `2-swipe` (Rename/Delete revealed), `3-rename` (name loaded in the bottom bar), `4-delete-confirm` (empty topic), `5-delete-blocked` (topic still has cards), `6-save-failed` (failed-save banner; same on every screen). Dark: `1-list`, `2-swipe`.
- **Cards** (light): `1-list` (All selected), `2-swipe` (Edit/Delete revealed), `3-edit-question` (edit card, front), `4-edit-answer` (edit card, back), `5-add-answer` (second step of the add bar), `6-loading` (list placeholders; same pattern on Topics and API keys), `7-error` (list can't load; same on Topics and API keys). Dark: `1-list`, `2-edit-question`.
- **Settings** (light): `1-main`, `2-agent-access`, `3-revoke-swipe`, `4-revoke-confirm`, `5-key-created` (the one-time key dialog). Dark: `1-main`.
- Topics screenshots predate the send button turning teal; it shows dimmed (disabled) in them, so they're still accurate.

## Setup
- **Stack:** Vite + React + TypeScript, chosen as the plainest static setup with no server.
- **Port:** 5180 (fixed in `vite.config.ts`, because 5173 is used by another app).
- **No backend calls.** All data is mocked in `src/mock.ts`, using the same JSON shapes as the backend (`Card.java`, `TopicView`, `GET /api/cards/due` → `{ cards, dueCount, nextDueAt }`).
- **Login** is handled by Clerk, so the prototype starts after login.

## Play Screen (first screen after login) — 🔒 locked 2026-11-05
- Due cards are shown as a stack, with the next two peeking out behind the top card.
- Tapping a card flips it to show the answer, with the question shown small above.
- Difficulty buttons appear after the flip: Again / Hard / Good / Easy, with quality values Again=1, Hard=3, Good=4, Easy=5 (matching `Sm2.java`). Each button shows when the card would come back.
- Swiping is locked until the card is flipped **and** a difficulty is picked. A locked card only budges slightly when dragged, then snaps back. The back of the card says "Pick a difficulty" until one is chosen.
- Once unlocked, swiping left or right records the chosen difficulty and moves to the next card. There is no skip; every card that leaves the stack has been graded.
- The remaining count sits just below the card stack as position out of total ("1 / 5"), in small muted text. Chosen for being subtle and for tying the number to the stack it counts. The top of the screen holds only the menu button.
- **Implementation note:** the flipping card needs its own compositing layer (`will-change: transform` on the element that rotates). Without it, Chrome stops drawing the lower half of the flipped card after a repaint, and the card behind shows through.
- A rounded pill-shaped menu button with two lines sits top-left. Everything else in the app goes inside the menu (drawer).
- The drawer has exactly three items, each with an icon: **Topics**, **Cards**, **Settings**. No sign-out entry in the drawer. The items sit at the bottom of the drawer so they're within thumb reach on a phone.

## Play Screen empty states — 🔒 locked 2026-11-05
Both are centered on the Play Screen, with the headline in the wordmark font (Space Grotesk 700, 32px). No celebration effects. The difficulty bar is hidden.
- **Finished today's cards — "Brain Upgraded"** (exact text). Below it, in muted text: "Next card due in 3h." (from the soonest future `dueAt`: "in 40m", "in 3h", "tomorrow", "in 5 days"; "Nothing else is scheduled." if none). If cards were reviewed this session, a summary in normal text: "6 reviewed · 5 Good or Easy".
  - The session summary is counted on the device during the session; the backend keeps no review history yet.
  - "Review ahead" (studying tomorrow's cards early) was considered and dropped: the backend only returns cards that are already due.
- **Brand-new user, no cards at all — "Let's put those brain cells to work."** (exact text). Below: "Add your first cards, or connect an agent to make them for you." Two full-width buttons: **Add cards** (teal, opens Cards) and **Connect an agent** (outlined, opens Agent access).
- Prototype only: open the app with `?fresh` in the URL to see the brand-new state; "Replay mock deck" on the done state isn't part of the design.

## Loading states — 🔒 locked 2026-11-05
- **Messages:** every load shows one message picked at random from a master list of 37 (in `src/Loading.tsx`): "Warming up the neurons...", plus four sets supplied by the user: brain ("Brain cells assembling..."), internet ("Let him cook...", "Chat, are we cooked?"), developer ("SELECT * FROM brain..."), and calm ("A thought is forming..."). A new pick on every load; it doesn't change while that load is running.
- **300ms delay:** nothing is shown for the first 300ms, so fast loads never flash a loader.
- **App start and Play:** centered, a faint dashed outline of two stacked cards (front one opaque) that gently pulses as one, with the message below in the wordmark font (Space Grotesk 700, 24px). The menu button stays visible.
- **Topics, Cards, API keys:** the top bar, chips and composer stay in place. In the list area: the message in small muted text, then 5 placeholder rows shaped like real ones (a name bar of varied width and a short count bar), pulsing. Topics hides its search bar while loading (the topic count isn't known yet).
- **Loading more cards** (scrolling past 50): a small "Loading more…" row at the bottom of the list. Not built in the prototype (no paging in the mock).
- **Saving, adding, deleting:** no spinners. The screen updates immediately and the request runs in the background; on failure the change is undone and an error shows (error states: not designed yet).
- **Reduced motion:** placeholders and the card outline stay still when the phone asks for less motion.
- Prototype only: add `?loading` to the URL to hold every screen in its loading state.

## Error states — 🔒 locked 2026-11-05
- **Two kinds of error, kept apart.**
  1. **Specific errors** say exactly what to fix, in plain words, no jokes. They live where they happen: "*Name* already exists" (topics), "Already in *Topic*" (cards), "Can't delete *Name*. It still has N cards…", disabled Save while a field is empty.
  2. **General failures** use **one** headline, always the same: **"Brain.exe stopped working"**, with a plain line under it saying what happened. No random list.
- **A page can't load** (Play, Topics, Cards, API keys): the loading state is replaced by the headline (wordmark font: 28px on Play, 22px in list screens), the plain line, and a teal **Try again** button.
  - No connection: "Couldn't reach brainrot. Check your connection."
  - Server error: "Something broke on our end. Try again in a moment."
  - Top bar, chips and composer stay in place on list screens, so the user can still navigate away. There, the error sits inside a card in the list's place (same card style as the list).
- **A save fails** (add, rename, edit, delete, revoke): the screen already updated instantly, so the change is undone and a banner appears above the bottom bar: "Couldn't save. Your change was undone." with **Try again** (re-sends the same change) and ✕. Card background, red left edge, clears itself after 8s. No headline: the user is mid-task, so it stays small.
- **Session expired:** not an error screen. Clerk sends the user back to sign-in.
- Prototype only: `?error=offline` or `?error=server` shows the page-load failure on every screen; `?failsave` makes every save fail.

## Topics Screen — 🔒 locked 2026-11-05
- Opened from **Topics** in the drawer.
- **Top bar:** a back-arrow pill at top-left (same size and spot as the Play Screen's menu button) opens the drawer. "Topics" is centered. Nothing on the right.
- The top bar stays fixed (sticky) while the list scrolls, so rows never slide under the back button.
- **Search:** shown only when there are **more than 10 topics**; with 10 or fewer the list starts right under the top bar. When shown, it is a search field (magnifier icon, "Search topics") between the top bar and the list. It filters as you type, ignoring case; no match shows `No topics match "…"`. *(Tried and rejected: hiding search behind a top-right icon that expands over the title. It felt odd.)*
- **List:** one rounded grouped list, a row per topic, with the name on the left and the card count on the right ("42 cards", "1 card"). Data comes from `GET /api/topics` (`TopicView.cardCount`).
- **Adding a topic:** a chat-style bar pinned to the bottom of the screen (rounded input + round send button with an up arrow), inset 32px from each side so it's a little narrower than the list. Type a name, then tap send or press Enter. The new topic appears at the top of the list. Send is dimmed while the input is empty or the name is a duplicate; duplicates show `"Name" already exists` just above the bar, because the backend requires unique names per user, ignoring case. Replaced an earlier **+** button in the top bar.
- **Renaming:** swipe a row left to reveal a **Rename** action (teal) behind it; only one row stays open at a time, and tapping any row closes it. Rename loads the current name into the bottom chat bar, with "Renaming **Name** ✕" above it, and the row being renamed gets a teal edge. Send saves; ✕ or Escape cancels. Same duplicate rule as adding, except a rename may change only the capitalization. Names are capped at 100 characters (backend limit). 
- **Deleting:** the same swipe reveals a red **Delete** action next to Rename. Both actions are **icon-only** (pencil on teal for Rename, trash on red for Delete), 64px wide each, with screen-reader labels. The default topic shows only Rename, since the backend won't delete it. Delete opens a centered confirmation dialog:
  - **Empty topic:** "Delete **Name**? This can't be undone." with Cancel / Delete (red).
  - **Topic with cards:** "Can't delete **Name**. It still has N cards. Move or delete them first." with only OK, matching the backend rule. *("Move cards to…" is deferred: see `BACKLOG.md`.)*
- Tapping a topic row opens the Cards screen filtered to that topic.

## Cards Screen — 🔒 locked 2026-11-05
- Opened from **Cards** in the drawer (shows All) or by tapping a topic (shows that topic).
- **Top bar:** back arrow (opens drawer), "Cards" centered. Sticky, like Topics.
- **Topic chips:** one row under the top bar that scrolls sideways: **All** first, then every topic. The selected chip is filled teal; the others are outlined.
- **Search:** always visible (card lists get long fast). Matches question or answer text. Backend: `GET /api/cards?q=`.
- **Card row:** question in bold (up to 2 lines), answer below in muted text (1 line, cut off with …), and the topic name in small caps only when **All** is selected. A due label on the right: **"due now"** in teal, otherwise muted "in 3d" / "in 2mo".
- **Tap a row** to open the edit sheet. **Swipe left** reveals Edit (pencil) and Delete (trash), the same as Topics.
- **Edit (flashcard-style):** the card opens as a flashcard over the dimmed list (no sheet behind it). **Cancel / Save sit inside the card**, filling its bottom edge edge-to-edge (no gap between them, no margin, a thin line above, bottom corners following the card), on whichever side is showing. Cancel is plain; Save is filled with the palette's teal (the Good color, `#437c77` light / `#8fc0ba` dark), with the flip hint and (on the front) the topic pill stacked above them.
  - **Front:** the question as large editable text, placed near the top of the card (not centered) so there's room to type, and a topic dropdown pill near the bottom, just above "Tap card to edit answer" (changing it moves the card; `PATCH /api/cards/{id}` takes `topicId`). The pill uses its own chevron with even spacing on both sides, not the browser's default arrow.
  - **Back:** "ANSWER" label, the question small for reference, the answer as large editable text (also top-aligned), and "Tap card to edit question".
  - Tapping any empty part of the card flips it with the same 3D flip as the Play Screen; tapping a field edits it instead. A text field shows a faint dashed outline on hover and a teal outline with shaded background while editing.
  - Save is disabled while the question or answer is empty.
- **Delete:** confirmation dialog: "Delete this card? "*question*" and its review history will be gone. This can't be undone." Cancel / Delete (red).
- **Adding (two-step chat bar):** the bar says "New card in *Topic*…". Type the question and send; the bar switches to "Type the answer…" with "Answer for **question** ✕" above it (✕ or Escape goes back to editing the question). Send creates the card in the selected topic, or the default topic when **All** is selected. A question already in that topic is refused with "Already in *Topic*" (backend dedupes questions per topic).
- **Paging:** the real app should load 50 at a time as you scroll (`nextCursor`). The prototype shows all mock cards at once.
- Not now: selecting several cards at once to move or delete them.

## Settings Screen — 🔒 locked 2026-11-05
- Opened from **Settings** in the drawer. Same top bar as the other screens. Grouped lists under small uppercase section labels.
- **Account:** avatar (initials on teal), name and email from Clerk; **Manage account ›** (opens Clerk's profile screen); **Sign out** in red text.
- **Agent access:** one row, **API keys ›** with the key count, and a note: "Lets AI agents like Claude Code read and add cards for you over MCP."
- **Appearance:** Theme as a 3-part segmented bar, **System / Light / Dark** (selected part filled teal). System follows the phone; Light/Dark override it.
- **About:** "brainrot." wordmark and the version.
- Left out on purpose: daily new-card limit and time zone (backend doesn't support them yet; see `BACKLOG.md`), notifications (nothing sends them).

## Agent Access Screen (sub-screen of Settings) — 🔒 locked 2026-11-05
- The top-left arrow is a real **back button to Settings** here (not the drawer). The drawer still highlights Settings.
- **Connect:** a card with the **MCP endpoint** and a ready-to-paste **Claude Code** command (`claude mcp add --transport http …`), each in a monospace box with a teal **Copy** button that briefly says "Copied". The host is a placeholder in the prototype.
- **Keys:** list of keys: name in bold, key prefix (`fc_a1B2c…`) in monospace, and on the right "Created Oct 12" / "Used 2h ago" (or "Never used"). Swipe left → **Revoke** (trash, red) → confirm: "Revoke *name*? Agents using this key stop working immediately. This can't be undone."
- **New key:** name it in the bottom chat bar → send. A dialog shows the **full key once**: "Copy *name* now. You won't be able to see it again." with **Copy key** and **Done** (teal). The backend only stores a hash, so this is the only time the key is visible. Names are capped at 100 characters.

## Navigation
- The drawer marks the current screen (bold, shaded row).
- **Focus ring:** one teal outline for keyboard focus everywhere, replacing the browser's default.
- Tapping the **brainrot.** wordmark in the drawer goes back to the Play Screen, since the drawer has no Play entry. *(Proposed, not yet confirmed.)*

## Colour & type
- **Primary and selected states** use the palette's teal, never black: Save, the chat bar's send button, and the selected topic chip. Danger actions (Delete) use the Again red.
- **Body font (trial, may revert):** Inter (400 / 500 / 600) for all text except the wordmark, including card questions and answers. Before this trial: system sans-serif for interface text and system serif for card text.
- **Wordmark:** "brainrot." (with a trailing dot) in Space Grotesk 700 Bold, 30px. It appears **only** at the top-left of the drawer. The menu button, now an X, slides to the drawer's top-right, so the header reads `brainrot.  [✕]`. It is not shown on the Play Screen.
- **Base scheme:** warm paper look. Light background `#f4f1ea`, cards `#fffdf8`, text `#1d1b18`, plus a matching dark mode.
- **Difficulty palette (locked):** clay to teal, ordered as a scale where warm means you forgot and cool means it was easy.

  | Grade | Light     | Dark      |
  |-------|-----------|-----------|
  | Again | `#b4553f` | `#d9806a` |
  | Hard  | `#a2623f` | `#e0aa88` |
  | Good  | `#437c77` | `#8fc0ba` |
  | Easy  | `#2f6b78` | `#62a0ad` |

  Hard and Good are darkened in light mode so their text stays readable (contrast of at least 4.5:1). The four buttons form **one segmented bar** with no gaps: a single rounded outline with thin dividers between them. A selected segment fills edge to edge with its color.

## Open questions
- Should picking a difficulty move to the next card automatically, instead of needing a swipe?
- Should there be a deliberate "skip / bury" action in the menu for cards the user doesn't want to grade now?
