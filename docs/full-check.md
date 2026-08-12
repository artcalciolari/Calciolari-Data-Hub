# Full check — backend and frontend

Review of `main` at `de8e497` (12 Aug 2026). Scope: C# / ASP.NET Core 10 backend, React + Vite PWA frontend, CI, Compose, ops scripts, and docs. Parser binary layout is **not** re-specified here; it remains locked by fixture A/B goldens (`docs/qrp-format.md`).

This is a code and architecture review, not a rewrite plan. Items already accepted as MVP residuals in `docs/residuals.md` are called out as such rather than re-litigated.

---

## 1. Verdict

The MVP vertical slice is real and coherent: InterPDV `.QRP` upload → immutable SHA-256 store → parse/validate → optional canonical publish → PostgreSQL → REST → mobile-first dashboard. Identity, overlap, and “fail closed” rules from ADR 0002 are implemented. Coverage gates are 100% on both sides (QRP/EMF namespace excluded by design).

The main gaps are **not missing screens**. They are:

1. A few **correctness mismatches** between API and UI, and between ingest vs reprocess.
2. **Synchronous ingest on the request thread** while the HTTP contract says `202 Accepted`.
3. **Query and publish paths that load unbounded rows** into memory.
4. **Frontend not ready for `SecurityEnabled=true`**, plus SPA navigation that full-reloads.
5. **CI that does not run lint or Playwright**, and Postgres 16 in CI vs 18.4 in Compose.

None of this blocks local LAN use. Several items should be fixed before any production profile or larger datasets.

---

## 2. What changed (repo evolution)

The original plan (`IMPLEMENTATION_PLAN.md`) described Java 21 + Spring Boot. Runtime truth is ADR 0004: **.NET 10 LTS / C# 14**, EF Core + Npgsql, embedded SQL `V1`.

| When | Change | Notes |
|---|---|---|
| Plan + Fase 0 | Empty repo → fixtures, PoC, ADRs | Parser blocked until A/B existed |
| PR #2 | InterPDV QRP parser (PoC port) | Golden tests; do not invent format |
| PR #3 | Persistence, raw storage, ingest, REST | Schema V1, dedup, dashboard API |
| PR #4 | React mobile-first UI | Resumo / Vendas / Produtos / Importar |
| PR #5 | PWA + ops | App-shell SW, CORS, limits, backup scripts |
| PR #6 | Admin reprocess | Atomic `artifact_publication` swap |
| PR #7 | 100% coverage gates | Coverlet + Vitest in CI |
| PR #8 | Java → C# / ASP.NET Core, then .NET 10 | HTTP `/api` + `/actuator` kept |

**Plan items still open or only partially done**

| Plan item | Status |
|---|---|
| OpenAPI / Swagger | Not present |
| Async worker + lease reclaim after crash | Lease columns exist; parse still runs **inside the HTTP request** |
| Structured logs and real metrics | Actuator `/metrics` returns `{ names: [] }`; services do not log |
| Flyway-style versioned migrations | `SqlMigrator` applies V1 iff `raw_artifact` is missing; no V2 story |
| Frontend TypeScript `strict` | `tsconfig.app.json` has unused/fallthrough checks, **not** `"strict": true` |
| Upload progress / job polling | Upload is sync; UI fakes progress 0 → N |
| Accessibility / performance audit (Fase 5) | No `:focus-visible`, no axe in CI |
| Compose volume for raw bytes | `datahub_raw` declared, **not mounted** to any service |
| Human sensitivity review of fixtures | Still `PENDING_REVIEW` (`docs/fase-0-status.md`) |

Java leftovers that still compile or document: `SPRING_DATASOURCE_*` fallback in `AppHost` and backup scripts; Maven entries in `.gitignore`; historical Java in `IMPLEMENTATION_PLAN.md` (flagged at the top). Harmless, but they confuse new readers.

---

## 3. What is already strong

Keep these; they match the product principles.

- **Parser isolation.** QRP/EMF types stay in `Imports.Infrastructure.InterPdv.Qrp`. Canonical tables and HTTP DTOs do not leak QuickReport.
- **Immutable raw store.** Keys are `ab/cd/{sha256}`, never filenames. `PutIfAbsent` does not clobber divergent bytes. `OpenVerified` checks size + hash. Path traversal is rejected in `LocalRawFileStorage.ResolveKey`.
- **Fail closed.** Invalid/fatal parses do not publish. Overlapping `external_sale_id` across artifacts → `OVERLAPPING_REPORT`, no canonical write.
- **Money as decimal strings** on the wire; PostgreSQL `numeric`; frontend `decimal.js` for parse (display still goes through `Number` — see FE).
- **Filename is not identity.** Hints never invent a year from the clock.
- **Production fail-fast.** `ASPNETCORE_ENVIRONMENT=Production` forces Basic auth and refuses empty user lists (ADR 0003).
- **PWA cache policy.** Workbox is app-shell only; `/api` and `/actuator` are `NetworkOnly`.
- **Problem Details** without stack traces; upload limits (20 files, 32 MB, `.qrp` only).
- **Tests against real PostgreSQL**, not an in-memory stand-in.

---

## 4. Backend findings

### 4.1 Bugs and contract mismatches

**B1 — Reprocess does not always update `import_file.status`**

`FinalizeParse` (first ingest) always maps file status from parse status. `FinalizeReprocess` only updates status when `published` is true:

```233:236:backend/src/Calciolari.DataHub/Imports/Application/ImportIngestionService.cs
        file.ParseAttemptId = attempt.Id;
        if (published)
        {
            file.Status = MapFileStatus(parseStatus);
```

After a failed or overlapping reprocess, `parse_attempt_id` points at the new attempt (FAILED / WARNING) while `import_file.status` can remain `IMPORTED`. The file-detail UI then shows a green/imported badge next to the failed attempt’s validations. Canonical publication correctly keeps the previous pointer; the **file row** is the lie.

**Fix:** Always set `file.Status` from the attempt (same as ingest), or keep `parse_attempt_id` on the last *published* attempt and store the latest attempt elsewhere. Prefer the ingest mapping for consistency.

**B2 — `PublishCanonical` can no-op after ingest already marked published**

If `parsed.ExternalProductId` is null, `PublishCanonical` returns without writing `artifact_publication`, but the caller still sets `published = true` and file status `IMPORTED`. Queries only expose rows tied to an active publication, so the UI would show “imported” with empty catalog. Latent if the validator always errors on missing product id; still a hole in the state machine.

**B3 — Upload returns `202 Accepted` after the work is finished**

`POST /api/imports/qrp` hashes, stores, parses, and publishes **on the request thread**, then returns 202 + `Location`. That status means “accepted for processing,” not “already done.” There is no background worker, so clients that poll `PROCESSING` will usually miss it. The frontend does not poll; it only refreshes the list.

Either:

- keep sync and return **200/201** with the completed job, or
- return 202 immediately and parse on a worker (plan Fase 3B).

Today it is the worst of both: blocking *and* async-looking.

**B4 — `/actuator/health` is HTTP 200 when the database is down**

```109:113:backend/src/Calciolari.DataHub/AppHost.cs
        app.MapGet("/actuator/health", async (DataHubDbContext db) =>
            HealthJson(await db.Database.CanConnectAsync())).AllowAnonymous();
        // ...
        app.MapGet("/actuator/health/readiness", async (DataHubDbContext db) =>
            ReadinessJson(await db.Database.CanConnectAsync())).AllowAnonymous();
```

`HealthJson` always returns 200 with `{ status: "DOWN" }`. Readiness correctly uses 503. Load balancers that only hit `/actuator/health` will keep sending traffic. `AddDbContextCheck<DataHubDbContext>()` is registered and never mapped.

### 4.2 Correctness / scale in ingest and queries

**B5 — Peak memory on ingest is roughly 2–3× file size**

`SpoolAndHash` streams to a temp file, then `File.ReadAllBytes`, then `PutIfAbsent(new MemoryStream(spool.Bytes))`, then the parser `ReadLimited` copies the stream into another `byte[]`. A 32 MB QRP can sit in memory twice after the temp file is deleted. Stream hash → store → parse from the stored file (already verified) and drop the spool buffer.

**B6 — `PublishCanonical` loads every INTERPDV sale**

```528:530:backend/src/Calciolari.DataHub/Imports/Application/ImportIngestionService.cs
        var salesByExternal = _db.Sales
            .Where(s => s.ExternalSource == Source)
            .ToDictionary(s => s.ExternalSaleId, s => s);
```

This grows without bound. Restrict to the sale ids in the current parse (the overlap query already collects them).

**B7 — Dashboard and sale list are N+1 / unbounded**

- `DashboardQueryService.Summarize` does `.ToList()` on **all** published items in the date window, then aggregates in process, then one extra `Products` query per top product.
- `SaleQueryService.List` issues `SumPublishedTotalForSale` **per row** on the page (up to 100).
- `SaleQueryService.Get` looks up `Products` once per line item.

Fine for two fixtures. Not fine for months of PDV data. Push sums/group-bys into SQL; join product name in the item query.

**B8 — Products without published `OUT` lines are invisible**

`PublishedProducts()` requires a `sale_item` whose `parse_attempt_id` is an active publication. That matches “only OUT becomes Sale.” A report that only has `IN` / stock movements can parse VALID and still never appear in `/api/products`. Document it, or publish a product row even when there are no sale items.

**B9 — Process-local locks never shrink**

`ConcurrentLockMap` (reprocess) and `LocalRawFileStorage.Locks` are `ConcurrentDictionary`s keyed by Guid/SHA. They never evict. One process, many unique artifacts → slow leak. Use `SemaphoreSlim` with removal on release, or don’t cache locks for completed ids.

### 4.3 Security (beyond documented residuals)

Already accepted: default `SecurityEnabled=false`, HTTP Basic, plaintext `user:pass:ROLE` in env. Additional issues:

| Item | Risk | Suggestion |
|---|---|---|
| Password compare is `==` | Timing side channel (low on LAN) | `CryptographicOperations.FixedTimeEquals` |
| Passwords stored reversible | Env dump = full credential set | Hash at rest (or stop before internet exposure, as ADR 0003 says) |
| Health/info anonymous in Production | Info leak + unauthenticated DB probe | Keep liveness public; protect health/info or put them on an internal port |
| `AllowedHosts: *` | Host-header flexibility | Pin in Production |
| No HSTS / HTTPS redirect | Expected behind a proxy; not documented as a requirement | State “TLS terminates at reverse proxy” in `docs/ops.md` |
| No CSP | API-only origin is OK; if the API ever serves the SPA, add CSP | |
| Extension-only upload check | Garbage `.qrp` is stored (intentional: audit trail) | Keep; just don’t treat storage as “valid InterPDV” |
| CORS `AllowCredentials` | Fine with explicit origins; frontend never sends `Authorization` | See FE auth gap |

`FromSql($"… WHERE id = {id} FOR UPDATE")` is EF parameterized interpolation, not string concat. Leave it; don’t “simplify” to raw SQL.

### 4.4 Structure and maintainability

`ImportIngestionService` is **803 lines**: spool, job lifecycle, dedup, parse finalize, overlap, publish, reprocess, leases, lock map. Ingest vs reprocess overlap/publish blocks are copy-pasted (`FinalizeParse` vs `FinalizeReprocess`). That duplication is how B1 happened.

Split along the seams that already exist in comments: spool/hash, artifact open-or-create, parse finalize, publish, reprocess claim. Keep one overlap+publish function.

Other residue:

- `ProvenanceKind` is only exercised in unit tests; ingest never stamps SOURCE/CALCULATED/INFERRED on rows (filename hints are the main inferred surface, labeled only in the UI copy).
- `Product.Unit` column exists; API always returns `null` (`ProductQueryService` hard-codes it).
- `FilenameHintsParser.StripDirectory` only splits on `\`. A Unix path in `originalFilename` keeps the prefix in the stem used for date hints.
- Actuator `info.version` is hardcoded `"0.0.1"`.
- Controllers and EF calls are sync. Under multi-file 32 MB uploads this occupies thread-pool threads for the whole parse.
- Almost no `ILogger` in domain services — incidents will be Kestrel + Postgres logs only.
- `SqlMigrator` cannot apply a future `V2__*.sql` if `raw_artifact` already exists. Next schema change needs a version table **before** it is needed.

### 4.5 Tests

Coverlet 100% line+branch on application code (QRP namespace + `Program.cs` excluded) is doing its job and also shaping the suite:

- `IngestionCoverageTests` is branch-oriented (injectable serialize, fake parsers, lease races). Valuable for the gate; weaker as a specification.
- Shared DB + `DisableTestParallelization` is correct and slow.
- No OpenAPI contract test; frontend upload mocks `id` instead of `jobId` (see F1) so a drift would not fail CI.
- Parser goldens remain the right acceptance test. Do not add invented QRP variants to paint excluded branches.

---

## 5. Frontend findings

### 5.1 Bugs and contract mismatches

**F1 — Upload response typed as `ImportJob`, API returns `jobId`**

Backend:

```151:154:backend/src/Calciolari.DataHub/Imports/Application/ImportQueryService.cs
public sealed record ImportAcceptedResponse(
    Guid JobId,
    string Status,
    IReadOnlyList<ImportFileSummary> Files);
```

Frontend `uploadQrp` is `request<ImportJob>`, whose id field is `id`. JSON is `{ jobId, status, files }` — **no `id`, no `createdAt`**. The page only uses `job.status` and `job.files.length`, so the happy path works. Tests mock `{ id: 'j1', … }`, which **does not match production**. Navigating to `/imports/${job.id}` after upload would be `undefined`.

**Fix:** `ImportAcceptedResponse { jobId, status, files }` and `navigate(\`/imports/${job.jobId}\`)` after success.

**F2 — Table rows use `window.location.assign` instead of the router**

Same pattern on imports, import job files, products, and sales:

```111:111:frontend/src/features/imports/ImportsPage.tsx
                  <tr key={job.id} className="link-row" onClick={() => { window.location.assign(`/imports/${job.id}`) }}>
```

The inner cell already has `<Link>`. The row click **destroys the SPA**: full reload, new SW registration, lost in-memory state. Use `useNavigate` or make the row a link. Keyboard users only get the inner `<Link>` today; the row itself is not activatable.

**F3 — Dashboard dies if recent sales fail**

`DashboardPage` waits on `getDashboard()` **and** `listSales({ size: 5 })`. A sales-list error hides KPIs that already loaded. Split the error surfaces.

**F4 — No auth client**

When `DATAHUB_SECURITY_ENABLED=true`, every `fetch` in `shared/api.ts` will 401. There is no `Authorization` header, login UI, or credential storage (good that SW does not cache API). The UI cannot be used in the Production profile without a reverse-proxy that injects Basic, or a small login that sends Basic on same-origin requests (`credentials` + header). Do not put passwords in the service worker cache.

**F5 — `request()` header merge**

```134:138:frontend/src/shared/api.ts
  const response = await fetch(`${base}${path}`, {
    headers: { Accept: 'application/json', ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }) },
    ...init,
  })
```

`...init` after `headers` means a caller-supplied `headers` object **replaces** Accept/Content-Type entirely. Harmless today; footgun when adding auth headers. Merge headers explicitly.

### 5.2 Product completeness

| Gap | Detail |
|---|---|
| No pagination UI | API returns `totalPages`; lists hard-code `size: 10` (imports) or `50` (products/sales). Sales product filter is also capped at 50 products. |
| No date filter on Resumo | Dashboard API supports `from` / `to` / `productId`; the page always asks for everything. |
| Reprocess not in UI | Documented residual; still the only way to recover from parser version bumps. |
| Fake upload progress | Callback fires 0 then N; no XHR/`ReadableStream` progress. Remove or implement. |
| No post-upload navigation | User must find the new job in a 10-row history. |
| `errorElement` is `NotFoundPage` | Loader/render throws look like 404. Use a distinct error boundary. |

### 5.3 UX, a11y, PWA

- Mobile-first shell (bottom nav &lt; 760px, 44px targets, PT-BR copy, empty/error/skeleton states) is in good shape.
- **No `:focus-visible` rules** in `styles.css`. Keyboard focus is effectively invisible.
- No `prefers-reduced-motion` (shimmer / `fadeUp`).
- Dropzone is mouse/DnD; the file button saves it. Announce selected files to AT.
- Charts expose values only via `title` tooltips; `role="img"` without a text alternative of the series.
- `PwaUpdateBanner` starts `setInterval` hourly and **never clears it** on unmount (`StrictMode` will double it in dev).
- `vite-plugin-pwa` `devOptions.enabled: true` registers a SW during `npm run dev` — painful when debugging API/proxy.
- PT Serif is loaded in `index.html` and unused (body is Work Sans). Extra font request on mobile.
- Unused Vite scaffold assets under `src/assets/`; several public logos unused.
- `TableSkeleton` copied in three pages; bar-chart math copied in dashboard vs product detail (product detail recomputes `max` inside `.map` → O(n²)).

`formatMoney` / `formatQuantity` parse with `decimal.js` then call `.toNumber()` for `Intl`. Display-only, but it contradicts the “no binary floating money” rule for anything near the rounding boundary. Prefer `toFixed` + `Intl` on a string, or accept the residual for BRL display.

### 5.4 TypeScript and tests

- `"strict": true` is off. `noUnusedLocals` / `verbatimModuleSyntax` are on. Production `any` is rare; JSON is `as T`.
- Vitest 100% on `src/**` except `main.tsx` is real and also coverage-driven (icon map entries that the app never renders, `sumStrings` only used in tests).
- Playwright: 5 scenarios × mobile + desktop, **requires a seeded backend on :8080**. The Playwright config only starts Vite. Not in `npm run ci`. No upload E2E, no 404, no auth, no offline shell, no nested file-detail assertions beyond the job page.
- `oxlint` exists (`react/rules-of-hooks`) and is **not** in CI. `useAsync` disables `exhaustive-deps` with an ESLint comment oxlint may not honor.

---

## 6. Cross-cutting: CI, ops, docs

### CI (`.github/workflows/ci.yml`)

Runs on `main` and `cursor/**`:

- Backend: Postgres **16** service + `dotnet test` Release + Coverlet artifact.
- Frontend: Node 22 + `npm run ci` (typecheck + Vitest coverage).

Missing: `oxlint`, Playwright, any job that boots API + UI together. Compose uses Postgres **18.4-alpine**. Drift means a PG 18-only behavior would pass locally and fail CI, or the reverse.

### Compose and backup

- Postgres-only compose; backend/frontend are run on the host. Fine for this MVP.
- Volume `datahub_raw` is a comment with no mount. Backup scripts tar `DATAHUB_RAW_STORAGE_ROOT` (default `./data/raw-storage`) from the **host**, so the unused volume is misleading.
- `backup.sh` / `restore.sh` treat DB + raw as one unit (correct). Restore uses `pg_restore --clean` and moves the old raw dir to `.bak`. Document that `pg_restore` needs a pre-created database and compatible major version (16 vs 18 again).
- No application Dockerfile / image. Deploy story is “run `dotnet` + static `vite build` behind a proxy.”

### Docs

`docs/api.md`, `docs/ops.md`, `docs/residuals.md`, ADRs, and `docs/coverage.md` are accurate enough to operate the MVP. Gaps to fold in later (not blockers for this review file):

- Health 200 vs readiness 503.
- Upload 202 vs synchronous completion.
- Frontend has no Basic-auth path.
- `ImportAcceptedResponse.jobId` vs list DTO `id`.
- Postgres 16 (CI) vs 18.4 (Compose).

`IMPLEMENTATION_PLAN.md` is a historical execution log; ADR 0004 is the stack source of truth. New contributors will still trip on Java section headings.

---

## 7. Prioritized opportunities

### P0 — Fix before relying on Production profile or more than toy data

| ID | Area | Action |
|---|---|---|
| B1 | BE | Align reprocess file status with ingest (`MapFileStatus` always). |
| F1 | FE | Type upload as `{ jobId, status, files }`; navigate to the new job. |
| F2 | FE | Replace `window.location.assign` with router navigation on all link-rows. |
| F4 | FE+ops | Either a tiny Basic login (same-origin) or document that Production **requires** a TLS proxy that injects auth — the SPA cannot talk to a locked API today. |
| B4 | BE | `/actuator/health` → 503 when DB is down (or stop advertising it as the probe). |
| B5/B6 | BE | Stop double-buffering uploads; load sales by candidate ids, not the whole table. |

### P1 — Completeness and operability

| ID | Area | Action |
|---|---|---|
| B3 | BE | Pick sync 200 or async 202+worker; don’t keep both. |
| — | FE | Pagination on imports / products / sales; date range on Resumo. |
| F3 | FE | Independent error states for dashboard KPIs vs recent sales. |
| — | BE | Split `ImportIngestionService`; one publish/overlap path. |
| — | BE | Versioned SQL migrations (schema_history table). |
| — | CI | Run `oxlint`; add a Playwright job with API + seed **or** mark E2E as manual. |
| — | Infra | Same Postgres major in Compose and CI; mount or delete `datahub_raw`. |
| — | BE | Structured logs (job id, sha256, publish/dedup/fail). |

### P2 — Hardening and UX

| ID | Area | Action |
|---|---|---|
| — | FE | `"strict": true`; shared `TableSkeleton` + chart helper. |
| — | FE | `:focus-visible`, reduced motion, clear SW interval, disable SW in dev. |
| — | FE | Drop unused PT Serif + scaffold assets. |
| — | BE | Async EF/IO on ingest; constant-time password compare. |
| — | BE | SQL aggregations for dashboard and sale totals. |
| — | BE | Fill `/actuator/metrics` or remove the stub. |
| — | BE | Strip `/` and `\` in filename hints; don’t hard-code `unit: null` if the column stays. |

### P3 — Hygiene

- Remove or quarantine `ProvenanceKind` until rows actually carry it.
- Drop Maven leftovers from `.gitignore` when convenient.
- OpenAPI from the live ASP.NET endpoints (would have caught F1).
- E2E: real upload of fixture B, 404 page, import file validations, offline shell.
- Human review of fixture sensitivity (`docs/fase-0-status.md`).

---

## 8. Suggested order of work

Not a calendar estimate — a dependency order:

1. **Contract fixes** (B1, F1, F2, B4) — small diffs, high confusion reduction.
2. **Auth story for the SPA** (F4) so Production is actually usable from the UI.
3. **Ingest memory + publish lookup** (B5, B6) before larger QRPs or more products.
4. **Pagination + dashboard date filter** so the UI matches the API that already exists.
5. **Split ingest service + versioned migrations** before the next schema change.
6. **CI: lint + one E2E path + aligned Postgres**.

Do not invent QRP format branches, extra sources (Stone/CSV/ERP), Kafka, or offline parsing. Those remain out of scope (`IMPLEMENTATION_PLAN.md` §18).

---

## 9. File map (for navigation)

**Backend (61 C# files under `src/`)** — vertical slices `Imports` / `Catalog` / `Sales` / `Analytics` / `Persistence` / `Shared`. Orchestration lives in `ImportIngestionService.cs` and `AppHost.cs`.

**Frontend (~20 app TS/TSX files)** — `app/` shell + `features/{dashboard,imports,products,sales}` + `shared/{api,format,useAsync,UI}`. All HTTP goes through `frontend/src/shared/api.ts`.

**Tests** — xUnit + WebApplicationFactory + real Postgres; Vitest 100% on UI; Playwright against a seeded API that CI does not start.
