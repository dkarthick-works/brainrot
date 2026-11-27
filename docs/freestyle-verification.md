# Freestyle implementation verification

## Incremental documentation review

Frozen source: `8c2a9bf3ce5e2c609cafb8dbef920f5ef3026131`; reviewed range: `5bd432a569b429f086b7a94ffbdcd9821a90e3f4..8c2a9bf3ce5e2c609cafb8dbef920f5ef3026131` (six commits, including the earlier documentation PR and its merge).

- `npm --prefix frontend ci --ignore-scripts --no-audit --no-fund`: exit 0; no dependency/lockfile edits.
- `npm --prefix frontend test`: exit 0; **71 tests in seven files**. The current suite includes the answer-only study-back regression added after the original implementation checks below.
- `VITE_CLERK_PUBLISHABLE_KEY=pk_test_ZXhhbXBsZS5jbGVyay5hY2NvdW50cy5kZXYk npm --prefix frontend run build`: exit 0; synthetic example key, not live authentication evidence.
- `npm --prefix frontend run test:browser`: exit 0; nine Chromium cases using stubbed API and a test-only Clerk adapter.
- `env -u MONGODB_URI -u CLERK_ISSUER -u MCP_RESOURCE_URL -u ALLOWED_ORIGINS ./mvnw -B test`: exit 0; 14 tests, zero failures/errors/skips. MongoDB was disposable Testcontainers state; OAuth signing/JWKS fixtures were local. Java 25.0.5, Maven 3.10.0, Node 26.7.0 and npm 11.19.0 were used.

The focused MVC test verifies forwarding for `/freestyle` and other explicit client routes on GET/refresh. The packaged-jar checks recorded below were **not rerun** in this incremental review. No container image build, deployment, real Clerk login, physical-device touch, Safari/WebKit or installed-PWA verification is claimed. Review-write recovery remains memory-only and explicit retries are not exactly-once delivery. See [implemented data/session semantics](data-model.md#frontend-state-and-network-limits) and [test setup](development.md#chromium-browser-tests).

## Results

The following records the earlier implementation pass, not additional executions by the incremental documentation review.

- `cd frontend && npm test`: 70 tests passed across 7 files.
- `cd frontend && npm run build`: TypeScript and production Vite/PWA build passed.
- `cd frontend && npm run test:browser`: 9 Chromium tests passed.
- `./mvnw test` with Java 25: 14 backend tests passed, including the focused SPA route test.
- Packaged Spring jar with built frontend assets: direct GET and cache-bypassing refresh of `/freestyle` returned HTTP 200 and exactly matched the built `index.html`; unauthenticated `/api/cards` still returned 401. Temporary app/Mongo processes were stopped.

## Automated coverage

Pure session and hook tests cover shuffle, duplicate filtering, cursor progression, prefetch, failure/retry, stale requests, StrictMode, restart, and empty versus completed passes. Component tests cover gestures, face semantics, drawer suspension, timers, reduced motion, read-only safety, scheduled-review navigation/retries, submission-specific rollback, and final-save completion gating.

Browser tests use a separate test-only Vite entry and Clerk adapter in `frontend/e2e/`; production authentication is unchanged. They exercise both themes at 390×844, 320×568, 667×375, and 1280×900, long question/answer scrolling, header geometry, flip, drawer selection, swipe, replay, exit, Home shortcut, and back/forward. A 102-card reduced-motion pass covers deduplication, later-page failure/retry, both swipe directions, and absence of writes. Browser testing found and fixed Chromium wheel scrolling inside the 3D card by compositing its scroll container.

## Running browser tests

From `frontend/`:

```sh
npm ci
npx playwright install chromium
npm run test:browser
```

On hosts lacking browser system libraries, install Playwright's OS dependencies. The suite starts and stops its own test-only server on port 8091. No Clerk credentials or production API access are needed.

## Remaining release/manual checks

Testing stopped at the user's request before commit. Physical-device touch/native scrolling, Safari/WebKit rendering, real authenticated deployment, installed-PWA behavior, and human visual sign-off are not claimed. Automated theme/viewport assertions are not a substitute for those checks. No deployment was performed.

Review recovery is in-memory only. Explicit retries may repeat a review accepted before an ambiguous network failure because the API has no idempotency key. Non-gesture Freestyle advancement remains deferred in `BACKLOG.md`.

Dependency installation reported 6 existing/current audit findings (2 low, 4 moderate); no forced dependency upgrades were made.
