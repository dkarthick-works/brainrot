# brainrot

Flashcards with spaced repetition: a React PWA, a Spring Boot REST API and seven MCP tools sharing the same user-scoped MongoDB data and scheduling logic.

- [Local setup, configuration, tests and deployment](docs/development.md)
- [Architecture and data flow](docs/architecture.md) ([canonical Mermaid](docs/architecture.mmd))
- [REST, MCP and authentication contracts](docs/api.md)
- [Data model and scheduling](docs/data-model.md)
- [Freestyle practice and review-save recovery](docs/freestyle-feature.md) ([verification and browser tests](docs/freestyle-verification.md))

## Quick start

Use JDK 25, Node.js and Docker for a **local development database**. Maven 3.10.0 is supplied by `./mvnw`; the container build uses Node 24. Node 26.7.0 and npm 11.19.0 were used successfully for the documented frontend checks. The repository does not declare a Node engine range.

From the repository root, with ports 27018, 8088 and 8089 available:

```sh
# Development only: creates/reuses this Compose project's named mongo-data volume.
# Use the isolated alternative in docs/development.md if existing data must not be touched.
docker compose up -d mongo
./mvnw spring-boot:run
```

In another terminal, from the repository root:

```sh
cp frontend/.env.example frontend/.env.local
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open `http://localhost:8089` and sign in with Clerk. The example publishable key and backend issuer refer to the repository's development Clerk instance; use your own matching instance if you do not have access. Database availability alone does not provide a working sign-in. See [configuration](docs/development.md#configuration) before using a different origin or Clerk instance.

## Repository map

| Path | Purpose |
| --- | --- |
| `src/main/java/app/flashcards/` | REST, security, MCP, data access and scheduler |
| `src/test/java/app/flashcards/` | Scheduling unit tests; REST/MCP/OAuth integration tests |
| `frontend/` | Real React app, API client and PWA build |
| `bruno/` | Example HTTP and MCP requests; select a local environment explicitly |
| `prototype/` | Throwaway design mock, not the production frontend |

[AGENTS.md](AGENTS.md) explains contributor/design rules. [BACKEND_PLAN.md](BACKEND_PLAN.md), [FRONTEND_PLAN.md](FRONTEND_PLAN.md) and [BACKLOG.md](BACKLOG.md) describe intent or deferred work, not implemented API guarantees. The source-linked documentation above describes implementation.

## Validation boundary

The incremental review of `5bd432a569b429f086b7a94ffbdcd9821a90e3f4..8c2a9bf3ce5e2c609cafb8dbef920f5ef3026131` verified Freestyle practice, navigation-safe scheduled-review saves and the updated card layouts. At the latter source, Maven tests passed (14), frontend unit/component tests passed (71), Chromium browser tests passed (9), and the frontend production build passed. Integration tests used disposable Testcontainers MongoDB; OAuth tests used local signing keys/JWKS; browser tests used a test-only Clerk adapter and stubbed API. This does not verify a live Clerk login, deployed service, container image build, physical touch input or installed PWA. The PWA caches an app shell, **not offline card data or writes**. Review-save recovery survives route changes only in memory, not reload or sign-out.
