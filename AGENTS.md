# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state (read this first)

This repository is **planning-only**. As of this writing it contains just three
tracked files: `README.md`, `LICENSE`, and `IMPLEMENTATION_PLAN.md`. There is
**no application source code, no build files, no lockfiles, no tests, no lint
config, no `compose.yaml`, and no CI**.

`IMPLEMENTATION_PLAN.md` (in Portuguese) is the design document for the planned
"Calciolari Data Hub" MVP. Section 1 explicitly states that no MVP code has been
implemented yet, and that the QRP binary format and its parser must **not** be
invented. Implementation is gated on artifacts that are not in the repo (the
proof-of-concept `index.html` and the real/sanitized `.QRP` fixtures A/B plus a
SHA-256 manifest — see plan Section 14, "Fase 0").

Consequences for environment setup:

- There is currently nothing to install, lint, test, build, or run. Do not
  fabricate a runnable app or scaffold the parser just to "make something run".
- Do not treat "no app to run" as an environment failure — it reflects the repo
  state, not a broken setup.

### Planned architecture (for when code lands)

The plan describes a **modular monolith**, not a microservice monorepo:

- **Backend** — Java 21 + Spring Boot, built with Maven (`backend/pom.xml` +
  `./mvnw` wrapper). Uses JPA, Flyway migrations, and Testcontainers (real
  PostgreSQL, never H2). This is where the QRP/InterPDV parsing happens.
- **Frontend** — React + TypeScript + Vite PWA (`frontend/package.json`),
  mobile-first dashboard/audit UI. Playwright for E2E.
- **PostgreSQL** — canonical data store (planned to be provisioned via
  `compose.yaml`). Raw `.QRP` bytes stored on the local filesystem.

Base VM toolchains already available: **Java 21**, **Node 22 / npm / pnpm**,
**Python 3.12**. Not preinstalled (and not needed until code exists): **Maven**
(use the `./mvnw` wrapper once `backend/` is scaffolded), **Docker**, and the
**`psql`** client.

### How to set up / run once code exists

Follow the phased plan and its documented commands (`IMPLEMENTATION_PLAN.md`
Sections 14–17). In summary, once the corresponding directories/manifests exist:

- Backend: `cd backend && ./mvnw test` (integration tests need Docker for
  Testcontainers/PostgreSQL), `./mvnw spring-boot:run` to run in dev mode.
- Frontend: `cd frontend && npm install && npm run dev` (Vite dev server);
  typecheck/lint/tests via the scripts defined in `package.json`.
- Local infra: `docker compose up` (planned `compose.yaml`) to start PostgreSQL
  + raw-storage volume. Docker is not preinstalled on the base VM.

The registered startup update script is intentionally guarded: it installs
backend (`./mvnw` offline resolve) and frontend (`npm install`) dependencies
**only if** `backend/pom.xml` / `frontend/package.json` exist, and is a safe
no-op in the current planning-only state.
