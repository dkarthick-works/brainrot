# brainrot (flashcards)

Spring Boot backend in `src/`. Backend plan: `BACKEND_PLAN.md`. Frontend plan: `FRONTEND_PLAN.md` (web PWA in `frontend/`). Deferred work: `BACKLOG.md`.

## Frontend design reference
The approved UI design lives in `prototype/`. It's a throwaway mock (Vite + React, mock data, no API calls) used only to settle the design.

When building the real frontend:
- **Read `prototype/DECISIONS.md` first.** It's the spec. Sections marked 🔒 locked are final; don't change them without asking.
- Look at `prototype/screens/` for each screen's states in light and dark.
- Use `prototype/src/` for exact values only (colors, spacing, fonts, animation timing). Don't copy or import its code; it's a mock, not production code.
- The real frontend calls the real API. The shapes in `prototype/src/mock.ts` mirror the backend, but the backend code is the source of truth.
