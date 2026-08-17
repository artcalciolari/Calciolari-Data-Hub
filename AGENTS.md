# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state

Calciolari Data Hub MVP: InterPDV `.QRP` import, PostgreSQL canonical store, React dashboard.

- **Backend** — C# / ASP.NET Core 10 (`backend/Calciolari.DataHub.sln`). EF Core + Npgsql, SQL migration `V1__import_and_canonical.sql`, Coverlet **100% line+branch** (QRP/EMF parser namespace excluded from the hard gate). Real PostgreSQL only (never an in-memory stand-in).
- **Frontend** — React + TypeScript + Vite PWA (`frontend/package.json`), mobile-first dashboard/audit UI. Playwright for E2E.
- **PostgreSQL** — canonical data store via `compose.yaml`. Raw `.QRP` bytes stored on the local filesystem.

Do **not** invent the QRP binary format. Parser behavior is locked by fixture A/B golden tests.

Base VM toolchains: **.NET 10** (install the SDK if missing), **Node 22 / npm**, **Python 3.12**, **Java 21** (unused by the current backend). Not preinstalled: **Docker**, **`psql`**.

### How to set up / run

- Backend: `cd backend && dotnet test` (needs PostgreSQL at `127.0.0.1:5432` / `datahub` / `change-me`), `dotnet run --project src/Calciolari.DataHub` (port **8080**).
- Frontend: `cd frontend && npm install && npm run dev` (Vite; proxies `/api` and `/actuator` to `:8080`).
- Local infra: `docker compose up -d --build` starts PostgreSQL, backend, and frontend together.

The registered startup update script installs frontend (`npm install`) when `frontend/package.json` exists, and is a no-op for backend until `backend/Calciolari.DataHub.sln` is restored with `dotnet restore`.
