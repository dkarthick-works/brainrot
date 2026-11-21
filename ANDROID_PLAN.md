# brainrot Android Plan

A native Android app in Kotlin with the same design as the web PWA (`frontend/`). Builds are distributed to testers through **TestApp.io** using **fastlane**, run on this machine. There is no Play Store release.

**Sources of truth** (same as `FRONTEND_PLAN.md`)
- **Design:** `prototype/DECISIONS.md` (🔒 sections are final) and `prototype/screens/` (every state, light and dark).
- **Exact values:** `prototype/src/index.css` (colors, spacing, radii, motion timing). Copy the values, not the components.
- **Behavior:** `FRONTEND_PLAN.md` §3–§5 and the shipped web app. Android matches it screen for screen unless this file says otherwise.
- **API:** the backend code (`src/main/java/app/flashcards/`) and `BACKEND_PLAN.md` §4.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Language / build | Kotlin, Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), JDK 17 |
| SDK levels | `minSdk 26` (Android 8.0), `targetSdk`/`compileSdk` = latest stable |
| UI | Jetpack Compose. Compose *foundation* plus hand-built components on our own theme. Material 3 only where it saves real work (text field internals, ripple off), never its look |
| Auth | Clerk Android SDK (`com.clerk:clerk-android-api` + `clerk-android-ui`): `AuthView` for sign-in/up, Clerk's profile view for **Manage account**, `getToken()` for the bearer token |
| HTTP | OkHttp + Retrofit + kotlinx.serialization. An interceptor adds `Authorization: Bearer <Clerk token>` |
| State | `ViewModel` + `StateFlow` per screen; small repositories own the cached topics/cards/keys (the web app's TanStack Query keys become repository flows) |
| Navigation | Navigation Compose, type-safe routes |
| Settings storage | SharedPreferences (theme; a synchronous read avoids a wrong-theme flash at launch) |
| DI | Manual (one `AppContainer` in `Application`). No Hilt: the app is small |
| Tests | JUnit + Turbine for logic and ViewModels; Compose UI tests for swipe/flip locks, run on a device |

Not used: Room/offline cache (data needs the network, same as the PWA), Firebase, Play Services.

---

## 2. Layout

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
  Gemfile, Gemfile.lock            # fastlane + fastlane-plugin-testappio (~> 2.0)
  fastlane/Fastfile, Appfile, Pluginfile
  fastlane/.env.example            # names of the secrets (real .env is gitignored)
  app/
    build.gradle.kts               # flavors: debug (local API) / release (production API)
    src/main/
      AndroidManifest.xml
      res/font/                    # Inter 400/500/600, Space Grotesk 700 (bundled)
      res/mipmap-*/                # adaptive icon: "b." on paper
      java/app/flashcards/android/
        BrainrotApp.kt             # Application: Clerk.initialize, AppContainer
        MainActivity.kt            # edge-to-edge, splash, theme, nav host
        api/                       # Retrofit service, DTOs (Card, TopicView, Due, Page, ApiKey…), ApiError
        data/                      # TopicsRepo, CardsRepo, KeysRepo, ReviewWrites (lives above routes)
        logic/                     # nextInterval, dueLabel, untilLabel, queue dedupe, freestyle shuffle
        ui/theme/                  # Tokens (colors light/dark), Type, Shapes, Motion
        ui/components/             # SwipeRow, FlipCard, CardStack, Composer, Dialog, Segmented,
                                   # Loading, ErrorState, SaveError, Drawer, TopBar
        ui/screens/                # Play, Freestyle, Topics, Cards, EditCard, Settings, AgentAccess
    src/test/                      # JVM unit tests
    src/androidTest/               # Compose UI tests
```

The Gradle project lives in `android/` and builds on its own; nothing changes in the backend or `frontend/` builds.

---

## 3. Design system port

- **Tokens:** one `BrainrotColors` data class with a light and a dark instance, holding the exact values from `index.css` (`bg`, `ink`, `muted`, `card`, `line`, `again/hard/good/easy`, `onGrade`, the shadow). Provided through a `CompositionLocal`. No Material color roles.
- **Theme:** System / Light / Dark from Settings, stored in SharedPreferences. System follows `isSystemInDarkTheme()`. Status bar and navigation bar icons switch with it.
- **Type:** Inter for everything, Space Grotesk 700 for the wordmark and headlines. Sizes match the prototype (wordmark 30, empty-state headline 32, loader 24, error 28 / 22).
- **Shadows:** the CSS uses a two-layer warm shadow. Compose's `shadow()` is tinted grey, so use a custom `drawBehind` shadow to match the screenshots.
- **Motion:** durations and easings come from `index.css`. If the system's animator scale is 0 (Android's "remove animations"), treat it as reduced motion: no fly-out/3D transitions, loaders stay still.
- **Focus ring:** a teal outline on keyboard/D-pad focus, the same as the web.
- **Check:** each screen gets a side-by-side check against its `prototype/screens/*.png` in light and dark, at a 390dp-wide device.

---

## 4. Android-specific behavior

These have no web equivalent, so they are decided here:

| Topic | Decision |
|---|---|
| System back | Closes, in order: an open dialog → the drawer → an open swipe row → composer rename/answer mode → otherwise navigate. Agent access → Settings. Any drawer screen → Play. Back on Play exits the app. Freestyle back = Exit Freestyle |
| Edge-to-edge | Draw under the system bars and use `WindowInsets` for the top bar and bottom composer (the web's `safe-area-inset` equivalent). The composer rises with the keyboard (`imePadding`) |
| Orientation | Portrait only, same as the PWA manifest |
| Splash | Android 12+ splash API: paper background, "b." icon. Then the 300ms-delayed loader, as on the web |
| App icon | Adaptive icon: "b." in Space Grotesk on `#f4f1ea`, plus a monochrome layer for themed icons |
| Copy buttons | `ClipboardManager`. Agent-key copies are marked sensitive (`EXTRA_IS_SENSITIVE`) so Android 13+ hides the preview |
| Process death | Play/Freestyle queues are not restored (same as a web reload). Unsent reviews retry until the process dies, same as the web's in-memory promise |
| Haptics | None in v1. They aren't in the design |

---

## 5. Auth

1. Clerk dashboard (dev and production instances): turn on **Native API** and register the Android package name and signing-certificate SHA-256.
2. `Clerk.initialize(context, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)`. Signed out → `AuthView` themed with `ClerkTheme` to the paper palette. A session expiry sends the user back to it, as on the web.
3. **Backend `azp` check:** `SecurityConfig` accepts a token whose `azp` is missing or listed in `ALLOWED_ORIGINS`. Phase 1 decodes a real native token. If it carries an `azp` the backend doesn't list, add that value to `ALLOWED_ORIGINS` in both environments. No code change is expected.

---

## 6. Builds and distribution

### Toolchain on this machine (one-time)
Ubuntu 24.04, 6 cores, 12 GB RAM, no `/dev/kvm` (so **no emulator**: testing happens on a physical phone).
- `openjdk-17-jdk-headless` (apt), passed to Android builds only. The default `java` stays JDK 25.
- Android command-line tools in `~/Android/Sdk` (`platforms`, `build-tools`, `platform-tools`), with `ANDROID_HOME` set in `~/.profile`.
- Ruby 3.2 (apt) + Bundler. fastlane and the TestApp.io plugin are installed through `android/Gemfile`, so the versions are pinned in the repo.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx3g`, 3 workers, no daemon, configuration cache on.
- Builds run under `systemd-run` with `MemoryMax=5G` at low CPU priority. No swap (user decision).

### Signing
- A release keystore is generated once at `~/.config/brainrot/release.jks`, **outside the repo**, and backed up somewhere the user chooses. Every TestApp.io build must be signed with the same key, or testers have to uninstall before they can update.
- The passwords and alias are read from environment variables.

### Secrets
All of these go in `android/fastlane/.env`, which is gitignored (`.env.example` lists the names):
`TESTAPPIO_API_TOKEN`, `TESTAPPIO_APP_ID`, `BRAINROT_KEYSTORE_PATH`, `BRAINROT_KEYSTORE_PASSWORD`, `BRAINROT_KEY_ALIAS`, `BRAINROT_KEY_PASSWORD`, `CLERK_PUBLISHABLE_KEY` (`pk_live_…`).

### Versioning
- `versionCode` = `git rev-list --count HEAD`, so it always goes up and needs no file edits.
- `versionName` comes from `android/version.properties` (starting at `0.1.0`), shown in Settings → About as `0.1.0 (123)`.

### Lanes (`android/fastlane/Fastfile`)
| Lane | Does |
|---|---|
| `test` | `./gradlew testDebugUnitTest lintDebug` |
| `beta` | Refuses a dirty working tree → `test` → `assembleRelease` (signed, R8 on) → `upload_to_testappio(release: "android", git_release_notes: true, git_commit_id: true, notify: true)` → tags the commit `android-v<name>-<code>` |
| `debug_build` | `assembleDebug`, no upload, for installing on a phone over adb |

Run with `cd android && bundle exec fastlane beta`. "CI on this machine" means this command. There is no runner service. If builds should start on their own later, a cron job or git hook can call the same lane.

---

## 7. Phases

Each phase ends with the screen matching its screenshots in light and dark on a real phone.

0. **Toolchain + pipeline first:** install the toolchain, create the empty Compose app, add signing and fastlane, and push one "hello" build through `fastlane beta` to TestApp.io. This proves distribution before any UI exists.
1. **Scaffold:** tokens, fonts, theme switch, Clerk sign-in, the `azp` check (§5), API client + typed errors, Drawer/TopBar shell, navigation and the back rules.
2. **Play:** due queue + prefetch/dedupe, flip, grading with interval hints, locked swipe with rubber-band, `ReviewWrites` above routes, Brain Upgraded, brand-new user.
3. **Freestyle:** paginated page-shuffled stack, swipe to advance, header with Exit, completion/empty, read notice on later-page errors.
4. **Topics:** list, search when more than 10, composer add/rename, SwipeRow Rename/Delete, delete dialogs, tap opens Cards.
5. **Cards:** chips, search, infinite list, due labels, two-step add, flashcard EditCard with topic move, swipe Edit/Delete.
6. **Settings + Agent access:** Clerk user + Manage account + Sign out, theme segmented bar, About with the version, keys list/create/reveal-once/revoke, copy buttons, MCP URL.
7. **Loading & errors everywhere:** the 37 messages, the 300ms delay, list placeholders, Brain.exe page errors, SaveError banner with retry and 8s auto-clear.
8. **Polish + first tester build:** icon, splash, reduced motion, TalkBack labels (same as the web's screen-reader labels), R8 rules for kotlinx.serialization and Clerk, the `beta` lane to TestApp.io.

### Tests
- **JVM:** port the web's `logic.test.ts` and `freestyle.test.ts` cases (`nextInterval`, due label, untilLabel, queue prefetch/dedupe, page shuffle), plus ViewModel tests for optimistic write + rollback and review-write retry.
- **Device (Compose UI tests, run over adb):** Play swipe locked until flipped and graded; one swipe row open at a time; composer modes.
- **Manual smoke on each `beta` build:** sign in → grade a card → Freestyle pass → add a topic → add a card → edit it → delete it → create and revoke a key → switch theme.

---

## 8. Open items

| Item | Owner | Notes |
|---|---|---|
| TestApp.io app + API token | user | Create an Android app in portal.testapp.io and a token under profile → tokens. Put both in `fastlane/.env` |
| Clerk native setup | user | Turn on Native API; add the package name and the release key's SHA-256 (printed in phase 0) on dev and production |
| Keystore backup | user | Losing it forces every tester to uninstall and reinstall |
| Test phone | user | Needed from phase 0 (no emulator here). USB or wireless adb for debug builds; TestApp.io app for release builds |
| Application ID | decided | `app.flashcards.android`. It can't change once testers have installed the app |
| Backend gaps | — | Same as `FRONTEND_PLAN.md` §8 (Hard/Good/Easy intervals, account-deletion webhook, Move cards to…) |

Out of scope: Play Store, notifications/reminders, widgets, offline mode, tablets/landscape, iOS.
