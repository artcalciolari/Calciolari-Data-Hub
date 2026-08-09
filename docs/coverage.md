# Coverage gates (CI/CD)

Both backend and frontend enforce coverage gates in CI via `.github/workflows/ci.yml`.

## Frontend (Vitest + v8) — **100%**

```bash
cd frontend
npm run test:coverage   # or: npm run ci
```

Thresholds: lines / branches / functions / statements = **100%**.

Scope (`vite.config.ts`): `src/**/*.{ts,tsx}` except `main.tsx`, tests, and assets.

## Backend (JaCoCo) — **100%** on application code

```bash
cd backend
DATAHUB_TEST_JDBC_PASSWORD=change-me ./mvnw verify
```

- Agent during `test`; HTML report: `backend/target/site/jacoco/index.html`
- `jacoco:check` on `verify` requires LINE and BRANCH covered ratio = `1.00`
- Thresholds: `datahub.coverage.line` / `datahub.coverage.branch` in `pom.xml`

### QRP / EMF package

`br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp` is **excluded from the hard 100% gate** (still compiled, tested, and visible in the HTML report when instrumented without excludes for report-only runs).

Reason: binary EMF/QRP opcode branches are dense; acceptance is enforced by fixture A/B golden tests plus dedicated edge unit tests (`InterPdvQrpParserEdgeTest`, container/extractor/validator/mapper tests). Do not invent undocumented format variants just to paint branches.

To inspect QRP coverage locally without the exclude, temporarily remove the JaCoCo `<excludes>` block and run `./mvnw test jacoco:report`.

## Local CI parity

```bash
cd backend && DATAHUB_TEST_JDBC_PASSWORD=change-me ./mvnw -B verify
cd frontend && npm ci && npm run ci
```
