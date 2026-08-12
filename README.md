# Calciolari Data Hub

Camada própria de dados e analytics da empresa: importa exportações InterPDV (`.QRP`),
preserva o arquivo bruto e publica dados canônicos auditáveis.

## Estado atual

- Parser InterPDV QRP (PoC port) + Fixtures A/B
- Persistência PostgreSQL (EF Core + SQL `V1`), raw storage, dedup
- API REST: imports, products, sales, dashboard
- Frontend React + TypeScript mobile-first (Resumo, Vendas, Produtos, Importar)
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
npm run test       # vitest
npm run typecheck  # tsc -b
npx playwright test  # E2E (mobile + desktop) com backend em :8080
```

## Infra

```bash
cp .env.example .env
docker compose up -d   # quando Docker estiver disponível
```

## Backup / restore

Trate PostgreSQL + `DATAHUB_RAW_STORAGE_ROOT` como uma unidade lógica:

```bash
./scripts/backup.sh ./backups
./scripts/restore.sh ./backups/datahub-YYYYMMDD-HHMMSS
```

Detalhes em `docs/ops.md`.

## Segurança (resumo)

- Default local: API aberta — **somente em rede controlada**.
- Perfil `production`: autenticação HTTP Basic obrigatória (roles `VIEWER` / `IMPORTER` / `ADMIN`).
- Não exponha na internet sem autenticação. CORS vazio = same-origin.

## Limitações

Riscos residuais e fora de escopo: `docs/residuals.md` e plano §18.

## Coverage / CI

Gates de 100% (Coverlet + Vitest): `docs/coverage.md` e `.github/workflows/ci.yml`.
