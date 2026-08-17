# API — Calciolari Data Hub

Base path: `/api`. Money/quantities are decimal **strings**. `LocalDateTime` is ISO-8601 without offset.

OpenAPI document: `GET /openapi/v1.json` (anonymous).

## Imports

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/imports/qrp` | `multipart/form-data` field `files` (`.qrp`, max 20, 32MB each). Bytes are spooled and the job is accepted immediately. Response is **`202 Accepted`** + `Location: /api/imports/{jobId}`. The body is the **current** job; status is often `PROCESSING` while the in-process worker parses. Clients must poll `GET /api/imports/{jobId}` until the status is not `PENDING`/`PROCESSING`. File summaries include product/quantity/revenue when the parse produced them. Requires `IMPORTER`/`ADMIN` when security enabled |
| `GET` | `/api/imports` | paginated jobs |
| `GET` | `/api/imports/{jobId}` | job + file summaries |
| `GET` | `/api/imports/{jobId}/files/{fileId}` | admin detail (hash, hints, validations) |
| `POST` | `/api/imports/files/{fileId}/reprocess` | Admin only (`ADMIN` when security on). Verifies raw via `openVerified`, new parse attempt, atomic swap of `active_parse_attempt_id` on success. Failure keeps prior pointer. `409` on corruption or active lease. Not in primary UI nav. |

Duplicate content is a successful business response (`deduplicated: true`), not an HTTP error. The PWA upload client reports XHR progress and polls until the job is terminal.

## Debug (non-production)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/debug` | `{ enabled }` — whether destructive reset is available. `VIEWER`/`IMPORTER`/`ADMIN` when security is on |
| `POST` | `/api/debug/reset-dataset` | Admin only. Truncates canonical tables (keeps `schema_history`), deletes raw `.QRP` bytes, resets in-process import metrics. Same content can be imported again. **404** when debug mode is off. Forced **off** in `Production` |

`DATAHUB_DEBUG_ENABLED` overrides config. Development appsettings default to on; Production always disables it. The Importar page shows **Modo debug** only when `enabled` is true.

## Catalog / sales / analytics

| Method | Path |
|---|---|
| `GET` | `/api/products?q=&page=&size=` |
| `GET` | `/api/products/{id}` |
| `GET` | `/api/sales?from=&to=&productId=&page=&size=` |
| `GET` | `/api/sales/{id}` | Line items include `previousStock` / `resultingStock` when present in the source |
| `GET` | `/api/dashboard?from=&to=&productId=` |

Queries only include rows from `artifact_publication.active_parse_attempt_id`. Dashboard totals are computed in-process from published items (see `docs/residuals.md`).

## Auth (when `DATAHUB_SECURITY_ENABLED=true`)

HTTP Basic. Roles: `VIEWER` (GET), `IMPORTER` (POST upload), `ADMIN` (reprocess + debug dataset reset + actuator metrics + all).

Unauthenticated API calls → `401`. Insufficient role → `403`. The PWA login screen is `/login` (HTTP Basic stored in `sessionStorage`, never in the service worker cache).

Filename hints in import file detail are tagged `provenance: INFERRED_DATA` and may include `productCodeHint` plus incomplete date hints. They never overwrite source fields.

## Errors

`application/problem+json`. Stack traces never included.

## Ops

Actuator-compatible: `/actuator/health` and `/actuator/health/readiness` return **503** when PostgreSQL is down; `/actuator/health/liveness` stays up; `/actuator/info` (public); `/actuator/metrics` returns real counters (`imports.completed`, `imports.duplicates`, `imports.warnings`, `imports.failures`, `imports.duration.ms`, `raw.storage.bytes`) — ADMIN when security on.

See `docs/ops.md` for CORS, limits, backup/restore and PWA cache rules.
