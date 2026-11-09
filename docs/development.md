# Development and operations

[Documentation home](../README.md)

## Prerequisites and checks

Run commands from the repository root unless stated otherwise. [pom.xml](../pom.xml) targets Java 25, Spring Boot 4.1.2 and Spring AI 2.0.1. The [wrapper properties](../.mvn/wrapper/maven-wrapper.properties) select Maven 3.10.0; no system Maven installation is needed. The executable [mvnw](../mvnw) downloads Maven on first use. Dependencies also require network access initially.

```sh
java -version
./mvnw --version
node --version
npm --version
```

Verified audit toolchain: Temurin 25.0.5, Maven 3.10.0, Node 26.7.0, npm 11.19.0. The [Dockerfile](../Dockerfile) uses Node 24 Alpine and Temurin 25. Node 24 was not separately exercised during the audit. Frontend dependency versions are locked in [package-lock.json](../frontend/package-lock.json); scripts are defined in [package.json](../frontend/package.json).

## Local database and server

The root [compose.yaml](../compose.yaml) is development-only: MongoDB 8, host port 27018, persistent named `mongo-data` volume. `docker compose up -d mongo` reuses that volume. Do not point development commands at production or an existing database you must preserve.

For a disposable database instead, choose an unused port (27028 here) and run this in one terminal:

```sh
docker run --rm --name brainrot-docs-local -p 127.0.0.1:27028:27017 mongo:8
```

In another terminal:

```sh
MONGODB_URI=mongodb://localhost:27028/brainrot_disposable ./mvnw spring-boot:run
```

Stop the disposable container with Ctrl-C when finished. No volume is mounted. This is a local setup recipe; the audit exercised Testcontainers instead of this manual server recipe. For normal Compose development, the default URI already matches its port and database.

Start the frontend as shown in the [README](../README.md). Vite listens strictly on 8089; it will fail rather than silently choose another port. The frontend calls the API directly, not through a Vite API proxy. Local Maven startup does not build or copy the frontend into the jar.

## Configuration

Backend source of truth: [application.yml](../src/main/resources/application.yml) and [SecurityConfig.java](../src/main/java/app/flashcards/SecurityConfig.java).

| Variable | Default / requirement |
| --- | --- |
| `PORT` | `8088` |
| `MONGODB_URI` | `mongodb://localhost:27018/flashcards`; use a disposable URI for experiments |
| `CLERK_ISSUER` | `https://right-perch-3118.clerk.accounts.dev`; must match the frontend Clerk instance |
| `ALLOWED_ORIGINS` | Comma-separated `http://localhost:8089,http://localhost:8088`; CORS allowlist and session JWT `azp` validation when present |
| `MCP_RESOURCE_URL` | `http://localhost:8088/mcp`; must exactly match the resource/audience registered in Clerk |

Frontend source: [.env.example](../frontend/.env.example), [.env.production](../frontend/.env.production), [api.ts](../frontend/src/api.ts) and [main.tsx](../frontend/src/main.tsx).

| Variable | Use |
| --- | --- |
| `VITE_CLERK_PUBLISHABLE_KEY` | Public Clerk instance key; configure for local Vite and provide as a Docker build argument. Never use a Clerk secret key here. |
| `VITE_API_URL` | Local example: `http://localhost:8088/api`; production file: `/api` for same-origin hosting. Include `/api`. |

Vite values are build-time values in production; changing runtime container environment does not rewrite the browser bundle. The UI derives the MCP endpoint as `/mcp` on the API origin. For a deployment, align its public origin, `ALLOWED_ORIGINS`, issuer and `MCP_RESOURCE_URL`; OAuth metadata uses configured URLs rather than proxy request headers. Backend JWT verification lazily fetches the issuer's `/.well-known/jwks.json`; successful startup is not proof authentication works.

## Tests and build

```sh
./mvnw -B test
npm --prefix frontend ci
npm --prefix frontend test
# Supply the public key for the intended instance; .env.local can also provide it.
VITE_CLERK_PUBLISHABLE_KEY=YOUR_PUBLIC_CLERK_KEY npm --prefix frontend run build
```

- [Sm2Test.java](../src/test/java/app/flashcards/Sm2Test.java): four pure scheduling tests, no database.
- [CardFlowTest.java](../src/test/java/app/flashcards/CardFlowTest.java): four integration tests for isolation, duplicates, pagination, topics, API keys and MCP.
- [OAuthMcpTest.java](../src/test/java/app/flashcards/OAuthMcpTest.java): five integration tests, including signed-token decoding, metadata/challenges and MCP-only OAuth restrictions. Serves its own local JWKS rather than calling Clerk.
- [TestcontainersConfiguration.java](../src/test/java/app/flashcards/TestcontainersConfiguration.java) supplies disposable `mongo:8` service connections for integration tests. Docker access/image pulls are required; no Compose database is required. Avoid production environment overrides.
- [logic.test.ts](../frontend/src/logic.test.ts): nine frontend tests including scheduler hints and queue helpers. `npm run build` runs TypeScript and Vite/PWA generation; it is not a browser interaction test.

For a database-free backend subset: `./mvnw -B -Dtest=Sm2Test test`.

Audit execution at source `5bd432a569b429f086b7a94ffbdcd9821a90e3f4`: all 13 Maven tests passed with no skips, all nine frontend tests passed, and the frontend production build passed. Install used `npm ci --ignore-scripts --no-audit --no-fund`; no dependency/lockfile edits were needed. Warnings included deprecated `glob`, Lombok/Unsafe, Mockito dynamic agent attachment and Spring MCP bean/provider warnings; none failed these checks. No production calls, real credentials, existing database or live Clerk login were used.

## Container packaging and health

From the repository root, after setting a public instance key:

```sh
docker build --build-arg VITE_CLERK_PUBLISHABLE_KEY="$VITE_CLERK_PUBLISHABLE_KEY" -t brainrot:local .
```

The Dockerfile builds `frontend/dist`, copies it into `src/main/resources/static` in the build stage, and packages the jar with `-DskipTests`. The final Temurin 25 JRE runs as `ubuntu`, exposes 8088 and probes `http://localhost:8088/actuator/health` with curl. If overriding `PORT`, the baked-in health probe still targets 8088. The public health endpoint is the only exposed Actuator endpoint in application configuration. The database must be supplied separately; Compose defines only MongoDB, not the application.

A Maven-only `./mvnw package` does not bundle the frontend automatically. The Docker image build/run, deployment/reverse proxy, health behavior under failure, live authentication and browser end-to-end behavior were not executed by the documentation audit. Do not interpret successful unit/integration checks as deployment verification.

## Request examples

Import [bruno/](../bruno) and explicitly select a local environment. Requests include card/topic CRUD, key management and MCP `initialize`, `tools/list`, `tools/call`. Key-management examples use a Clerk session token separately from the normal bearer token. Scripts can store returned IDs and raw keys in Bruno environment state: keep those local, never commit populated credentials. Do not run the production environment as a documentation check. See [API contracts](api.md) for endpoint details.
