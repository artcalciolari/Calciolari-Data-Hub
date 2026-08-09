# Calciolari Data Hub

Camada própria de dados e analytics da empresa: importa exportações InterPDV (`.QRP`),
preserva o arquivo bruto e publica dados canônicos auditáveis.

## Estado atual

Implementação em andamento (Fase 3A):

- PoC preservado em `docs/poc/index.html`
- Parser backend Java portado do PoC (`backend/…/interpdv/qrp`)
- Fixtures A e B com regressão §13.2
- Persistência PostgreSQL + Flyway + `LocalRawFileStorage` + ingestão/dedup
- API HTTP e frontend ainda não iniciados (Fase 3B/4)

Ver `IMPLEMENTATION_PLAN.md`, `docs/fase-0-status.md`, `docs/qrp-format.md` e `docs/decisions/`.

## Stack fixada

Ver `docs/versions.md` (Java 21, Spring Boot 4.1.0, PostgreSQL 18.4 no Compose).

## Backend

```bash
cd backend
./mvnw test
```

Integração espera JDBC em `jdbc:postgresql://127.0.0.1:5432/datahub`
(usuário/senha `datahub`/`datahub`, sobrescrevíveis via `DATAHUB_TEST_JDBC_*`).

Fixtures: `backend/src/test/resources/fixtures/qrp/fixture-{a,b}.qrp`.

## Infra local

```bash
cp .env.example .env
docker compose up -d   # quando Docker estiver disponível
```
