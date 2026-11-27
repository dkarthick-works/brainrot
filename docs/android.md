# Android client

[Documentation home](../README.md) · [Architecture](architecture.md) · [REST contracts](api.md)

The native client in `android/` is Kotlin/Jetpack Compose, separate from the React PWA. It uses the existing REST API and Clerk session authentication; it does not host a backend, connect directly to MongoDB, or call MCP tools. This page describes implementation at source `2ef025d983b77bb7c21600f7e43ebcfb288b8eea`, not the future work in [ANDROID_PLAN.md](../ANDROID_PLAN.md).

## Local setup and debug build

Run these commands from the repository root. Use JDK 17 for Android (the backend separately requires JDK 25), an Android SDK with the compile platform selected by [app/build.gradle.kts](../android/app/build.gradle.kts), and Git history available. The source selects compile SDK 37 minor API 2, target SDK 37 and minimum SDK 26. [Gradle wrapper properties](../android/gradle/wrapper/gradle-wrapper.properties) pin Gradle 9.9.2 with a distribution checksum; the [version catalog](../android/gradle/libs.versions.toml) pins AGP 9.5.2 and Kotlin 2.4.21. Initial wrapper/SDK/dependency downloads need network access. No system Gradle installation is needed.

```sh
# Adjust these two paths for your machine; do not change the backend JDK globally.
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
# Public key of the Clerk instance matching your local backend issuer.
export CLERK_PUBLISHABLE_KEY=YOUR_PUBLIC_CLERK_KEY
android/gradlew -p android testDebugUnitTest lintDebug assembleDebug
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. A blank public key is allowed by the debug build configuration but does **not** establish usable runtime sign-in. Unlike the web client, Android reads `CLERK_PUBLISHABLE_KEY`, not `VITE_CLERK_PUBLISHABLE_KEY`. Gradle reads this environment variable directly; it does not load the Fastlane `.env` file itself.

For a connected emulator/device with USB debugging, start a **local, disposable-data** backend using the [development guide](development.md#local-database-and-server), then:

```sh
adb reverse tcp:8088 tcp:8088
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Debug requests use the hard-coded `http://localhost:8088/api/`; the reverse mapping routes that device port to the host. The [debug manifest](../android/app/src/debug/AndroidManifest.xml) and [network security configuration](../android/app/src/debug/res/xml/network_security_config.xml) permit cleartext only for `localhost`, not arbitrary hosts. The [main manifest](../android/app/src/main/AndroidManifest.xml) requests Internet permission, disables Android backup and fixes the main activity to portrait. Installation, device networking and live login were not exercised in the documentation audit.

## Authentication and network boundary

[BrainrotApp.kt](../android/app/src/main/java/app/flashcards/android/BrainrotApp.kt) initializes Clerk and application-owned stores. [AuthGate.kt](../android/app/src/main/java/app/flashcards/android/ui/AuthGate.kt) shows initialization/error/sign-in states before the authenticated shell, using Clerk's UI with Google One Tap disabled. Provider configuration must still be supplied in Clerk; compiled code is not proof a provider works.

[ApiClient.kt](../android/app/src/main/java/app/flashcards/android/api/ApiClient.kt) builds Retrofit/OkHttp with a session-token interceptor. Each request gets a Clerk bearer token; an API 401 triggers one retry with a fresh token. A second API 401 remains an error rather than forcing sign-out. Clerk token failures classified as ended sessions (401/404) trigger sign-out; temporary token unavailability does not. Switching users clears user-bound in-memory stores and cancels their local jobs, but cannot undo an already accepted server write.

[BrainrotApi.kt](../android/app/src/main/java/app/flashcards/android/api/BrainrotApi.kt) and [Models.kt](../android/app/src/main/java/app/flashcards/android/api/Models.kt) define the existing cards/due/review, card CRUD, topic CRUD and API-key-management requests. Due reads default to 20 cards; paginated card reads default to 50. Review bodies contain `quality`; Android creates one question/answer pair per create request. API keys created in Agent Access are for external clients: the Android app itself continues using the Clerk session. Backend ownership, authentication restrictions and scheduling remain those in the [API](api.md) and [data model](data-model.md) documentation.

## Implemented behavior and state lifetime

- **Scheduled review:** reveal the answer, select Again/Hard/Good/Easy (qualities 1/3/4/5), then swipe or use the accessibility Next action to submit. Selecting a grade alone does not POST. Android displays local interval hints, but the server-returned card is authoritative. Due-card prefetch deduplicates previously seen IDs. See [PlayScreen.kt](../android/app/src/main/java/app/flashcards/android/ui/screens/PlayScreen.kt), [PlayController.kt](../android/app/src/main/java/app/flashcards/android/data/PlayController.kt) and [Logic.kt](../android/app/src/main/java/app/flashcards/android/logic/Logic.kt).
- **Review-save recovery:** [ReviewWrites.kt](../android/app/src/main/java/app/flashcards/android/data/ReviewWrites.kt) owns submissions above screen lifetime. Navigation/configuration changes do not by themselves cancel submitted reviews. Pending final saves prevent a premature completion summary; failures produce a global retry notice and return failed cards to the active queue. Explicit failed-save retries are user initiated, but HTTP 401 refresh can also replay a request. There is no idempotency key: an ambiguously accepted POST can be applied again on retry. This is not an exactly-once or durable background queue.
- **Freestyle:** [FreestyleController.kt](../android/app/src/main/java/app/flashcards/android/data/FreestyleController.kt) and [Freestyle.kt](../android/app/src/main/java/app/flashcards/android/logic/Freestyle.kt) traverse all cards without review writes or grading. Fisher–Yates shuffling is per fetched page, not a global uniform shuffle. IDs are deduplicated within/across pages; repeated/cyclic cursors fail. Prefetch starts with five or fewer buffered cards; failed reads preserve available cards and retry the failed cursor. Drawer use preserves a pass; leaving the route discards it. Restart begins a new pass. See [FreestyleScreen.kt](../android/app/src/main/java/app/flashcards/android/ui/screens/FreestyleScreen.kt).
- **Cards and topics:** [CardStore.kt](../android/app/src/main/java/app/flashcards/android/data/CardStore.kt), [TopicStore.kt](../android/app/src/main/java/app/flashcards/android/data/TopicStore.kt), [CardsScreen.kt](../android/app/src/main/java/app/flashcards/android/ui/screens/CardsScreen.kt) and [TopicsScreen.kt](../android/app/src/main/java/app/flashcards/android/ui/screens/TopicsScreen.kt) implement optimistic CRUD with rollback, card topic filters/server search/pagination, and local topic search above ten topics. The UI permits default-topic rename but not deletion, and requires moving/deleting cards before deleting a nonempty topic. Client duplicate checks inspect only loaded cards; server validation remains authoritative. Generic CRUD [save notices](../android/app/src/main/java/app/flashcards/android/data/SaveNotice.kt) retain only the latest retry closure and auto-dismiss after eight seconds, unlike the review failure notice.
- **Account and appearance:** [KeyStore.kt](../android/app/src/main/java/app/flashcards/android/data/KeyStore.kt) queues newly created raw keys for display until dismissal/user change/process loss; metadata refresh does not recover a dismissed secret. [AgentAccessScreen.kt](../android/app/src/main/java/app/flashcards/android/ui/screens/AgentAccessScreen.kt) displays/copies the derived `/mcp` endpoint and a command template requiring manual key substitution; it does not configure or run an agent. Sensitive clipboard marking is present, but no clipboard-clearing or screenshot-blocking guarantee is established. System/Light/Dark preference is saved to private SharedPreferences by [ThemeStore.kt](../android/app/src/main/java/app/flashcards/android/data/ThemeStore.kt).

Cards, topics, keys, study queues and pending/failed review records live in memory. They are not a persisted database/cache, offline write queue or WorkManager job. Process death loses this state; sign-out/user change clears user-bound stores. Theme persistence and some UI `rememberSaveable` state do not change that boundary. Existing buffered cards remaining visible after a network error is **not offline synchronization**. Card text is plain Compose text, not a Markdown/LaTeX/media renderer. There is no implemented Android UI here for bulk import/export, reminders, analytics or per-topic scheduled sessions.

## Release configuration and upload boundary

[app/build.gradle.kts](../android/app/build.gradle.kts) hard-codes a different HTTPS API base for release. No release/backend URL environment override is implemented. It enables shrinking/minification, requires a nonblank `CLERK_PUBLISHABLE_KEY` when a requested task name contains `Release`, and only configures release signing when `BRAINROT_KEYSTORE_PATH` is present. A release task is not proof a signed, authenticated or installable release was produced.

`versionName` comes from [version.properties](../android/version.properties); `versionCode` comes from `git rev-list --count HEAD`. Git history therefore affects release identity; shallow/different histories can give different counts. If configuring signing, `BRAINROT_KEYSTORE_PATH` also requires `BRAINROT_KEYSTORE_PASSWORD`, `BRAINROT_KEY_ALIAS` and `BRAINROT_KEY_PASSWORD`. Keep keystores, secrets and populated environment files out of Git.

[Fastfile](../android/fastlane/Fastfile) has `test` (unit tests/lint), `debug_build` (debug APK) and `beta` lanes. It overrides `JAVA_HOME` with the Linux JDK 17 path and defaults `ANDROID_HOME` to `~/Android/Sdk`. [Gemfile](../android/Gemfile), [lockfile](../android/Gemfile.lock) and [Pluginfile](../android/fastlane/Pluginfile) define the Ruby tooling; [the environment example](../android/fastlane/.env.example) additionally names `TESTAPPIO_API_TOKEN` and `TESTAPPIO_APP_ID`.

**Do not use the default [beta.sh](../android/beta.sh) as a local verification command.** It runs Fastlane `beta`, which tests, builds release, uploads to TestApp.io, notifies by default and adds a local Git tag unless `dirty:true`. `dirty:true` does not disable upload; `notify:false` disables notification only. The wrapper also requires a Linux user systemd manager, `nice`, Ruby/Bundler and configured tooling; its resource cap is not part of normal Gradle usage. No upload, tag creation, signing, release build or production request was performed in this documentation pass.

## Validation evidence and limits

At frozen source `2ef025d983b77bb7c21600f7e43ebcfb288b8eea`, using OpenJDK 17.0.21.1 and the configured SDK:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 android/gradlew -p android testDebugUnitTest lintDebug assembleDebug --console=plain
# Explicitly execute the test task rather than relying on an up-to-date result:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 android/gradlew -p android testDebugUnitTest --rerun --console=plain
```

Both completed with exit 0 after an initial combined invocation exceeded the runner timeout. The repeated combined invocation used existing build outputs for most tasks and completed lint. The explicit test rerun produced **63 tests in 11 suites, zero failures/errors/skips**; lint reported **seven warnings, no errors**, and a debug APK was produced. No real Clerk key was supplied: compilation and unit tests do not exercise sign-in.

Tests under [android/app/src/test/](../android/app/src/test/) cover session interception/problem details, topic/card/key optimistic stores, play/review recovery, Freestyle state/pagination and pure account/topic/scheduling helpers. They use test fixtures, not live Clerk/backend data. There are no Android instrumented UI tests in this source. Device/emulator behavior, Compose rendering/accessibility, OAuth/deep-link setup, clipboard behavior, process-death recovery, installation and signed/minified release behavior remain unverified. Android SDK installation itself was not rerun.
