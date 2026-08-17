# Calciolari Data Hub

Camada própria de dados e analytics da empresa: importa exportações InterPDV (`.QRP`),
preserva o arquivo bruto e publica dados canônicos auditáveis.

## Estado atual

- Parser InterPDV QRP (PoC port) + Fixtures A/B
- Backend **C# / ASP.NET Core 10** (EF Core + SQL versionado `schema_history` / `V1`), raw storage, dedup, worker assíncrono
- API REST: imports (`202` + poll), products, sales, dashboard; OpenAPI em `/openapi/v1.json`
- Frontend React + TypeScript **strict** mobile-first (Resumo, Vendas, Produtos, Importar, `/login`)
- PWA instalável (app shell only) + segurança operacional / backup
- Reprocessamento admin (`POST /api/imports/files/{id}/reprocess`)

Ver `IMPLEMENTATION_PLAN.md`, `docs/api.md`, `docs/ops.md`, `docs/residuals.md`, `docs/qrp-format.md`, `docs/decisions/`.

## Backend

```bash
# DB: postgres em localhost:5432 / datahub / change-me
cd backend
dotnet test
dotnet run --project src/Calciolari.DataHub
# Produção (auth obrigatória):
# ASPNETCORE_ENVIRONMENT=Production DATAHUB_SECURITY_USERS='admin:…:ADMIN|…' \
#   dotnet run --project src/Calciolari.DataHub
```

## Frontend

```bash
cd frontend
npm install
npm run dev        # http://127.0.0.1:5173 (proxy /api -> :8080)
npm run build      # gera SW + manifest
npm run preview    # serve o build (PWA instalável em localhost)
npm run ci         # typecheck + oxlint + vitest 100%
npx playwright test  # E2E (mobile + desktop); API em :8080 já semeada (scripts/seed-e2e.sh)
```

## Infra

Compose is the primary local start path. It runs PostgreSQL, the backend, and
the Vite development server together; the backend reaches PostgreSQL at the
internal service name `postgres`, while browser requests stay same-origin at
the frontend and are proxied to `backend`.

```bash
cp .env.example .env
# Stop direct-run services using 5432/8080/5173 first, or override them in .env.
docker compose up -d --build   # UI :5173, API :8080, PostgreSQL :5432
docker compose down             # stop the stack; keep data volumes
# docker compose down -v         # also remove PostgreSQL data (destructive)
# bytes brutos: DATAHUB_RAW_STORAGE_ROOT (default ./data/raw-storage no host)
```

Health checks gate startup: the backend waits for healthy PostgreSQL and the
frontend waits for a healthy backend. Direct backend/frontend commands above
remain available when Compose is not needed.

## Backup / restore

Trate PostgreSQL + `DATAHUB_RAW_STORAGE_ROOT` como uma unidade lógica:

```bash
./scripts/backup.sh ./backups
./scripts/restore.sh ./backups/datahub-YYYYMMDD-HHMMSS
```

Detalhes em `docs/ops.md`.

## Segurança (resumo)

- Default local: API aberta — **somente em rede controlada**.
- Perfil `production`: autenticação HTTP Basic obrigatória (roles `VIEWER` / `IMPORTER` / `ADMIN`). A PWA usa `/login` (credenciais só em `sessionStorage`).
- Não exponha na internet sem autenticação. CORS vazio = same-origin.

## Limitações

Riscos residuais e fora de escopo: `docs/residuals.md` e plano §18.

## Coverage / CI

Gates de 100% (Coverlet + Vitest): `docs/coverage.md` e `.github/workflows/ci.yml`.
Playwright (Chromium mobile+desktop, axe no dashboard, fixtures A/B semeados via API) roda no job `e2e` do mesmo workflow.
