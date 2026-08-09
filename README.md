# Calciolari Data Hub

Camada própria de dados e analytics da empresa: importa exportações InterPDV (`.QRP`),
preserva o arquivo bruto e publica dados canônicos auditáveis.

## Estado atual

Implementação inicial (Fase 0/2 parcial):

- PoC preservado em `docs/poc/index.html`
- Parser backend Java portado do PoC (`backend/…/interpdv/qrp`)
- Fixture B presente com regressão §13.2
- Fixture A ainda ausente — gate completo da Fase 2 pendente
- API HTTP, PostgreSQL e frontend **ainda não** iniciados (conforme plano)

Ver `IMPLEMENTATION_PLAN.md`, `docs/fase-0-status.md` e `docs/qrp-format.md`.

## Stack fixada

Ver `docs/versions.md` (Java 21, Spring Boot 4.1.0, PostgreSQL 18.4).

## Backend

```bash
cd backend
./mvnw test
```

O teste `InterPdvQrpParserFixtureBTest` exige
`backend/src/test/resources/fixtures/qrp/fixture-b.qrp`.

## Infra local (Fase 3A+)

```bash
cp .env.example .env
docker compose up -d
```

Docker não é necessário para os testes unitários/parser atuais.
