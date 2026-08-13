# Coverage gates (CI/CD)

Both backend and frontend enforce coverage gates in CI via `.github/workflows/ci.yml`.

## Frontend (Vitest + v8) — **100%**

```bash
cd frontend
npm run test:coverage   # or: npm run ci
```

Thresholds: lines / branches / functions / statements = **100%**.

Scope (`vite.config.ts`): `src/**/*.{ts,tsx}` except `main.tsx`, tests, and assets.

## Backend (Coverlet) — **100%** on application code

```bash
cd backend
dotnet test Calciolari.DataHub.sln
```

- Collector during `dotnet test`; reports: `backend/tests/Calciolari.DataHub.Tests/coverage/`
- Coverlet requires LINE and BRANCH covered ratio = `1.00` (`Threshold` / `ThresholdType` in the test `.csproj`)

### QRP / EMF package

`Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp` is **excluded from the hard 100% gate** (still compiled, tested, and present in the assembly). `Program.cs` and the OpenAPI source-generator file `OpenApiXmlCommentSupport.generated.cs` are also excluded.

Reason: binary EMF/QRP opcode branches are dense; acceptance is enforced by fixture A/B golden tests plus dedicated edge unit tests. Do not invent undocumented format variants just to paint branches.

To inspect QRP coverage locally without the exclude, temporarily remove the Coverlet `<Exclude>` in the test project and re-run `dotnet test`.

## Local CI parity

```bash
cd backend && dotnet test Calciolari.DataHub.sln
cd frontend && npm ci && npm run ci
# E2E (API already running on :8080):
./scripts/seed-e2e.sh && cd frontend && npx playwright test
```
