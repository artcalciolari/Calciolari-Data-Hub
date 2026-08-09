# Calciolari Data Hub

Camada própria de dados e analytics da empresa: importa exportações InterPDV (`.QRP`),
preserva o arquivo bruto e publica dados canônicos auditáveis.

## Estado atual

- Parser InterPDV QRP (PoC port) + Fixtures A/B
- Persistência PostgreSQL/Flyway, raw storage, dedup
- API REST: imports, products, sales, dashboard
- Frontend React + TypeScript mobile-first (Resumo, Vendas, Produtos, Importar)

Ver `IMPLEMENTATION_PLAN.md`, `docs/api.md`, `docs/qrp-format.md`, `docs/decisions/`.

## Backend

```bash
# DB: postgres em localhost:5432 / datahub / change-me
cd backend
./mvnw test
SPRING_DATASOURCE_PASSWORD=change-me ./mvnw spring-boot:run
```

## Frontend

```bash
cd frontend
npm install
npm run dev        # http://127.0.0.1:5173 (proxy /api -> :8080)
npm run test       # vitest
npm run typecheck  # tsc -b
npx playwright test  # E2E (mobile + desktop) com backend em :8080
```

## Infra

```bash
cp .env.example .env
docker compose up -d   # quando Docker estiver disponível
```
