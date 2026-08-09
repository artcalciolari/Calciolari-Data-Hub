# API — Calciolari Data Hub

Base path: `/api`. Money/quantities are decimal **strings**. `LocalDateTime` is ISO-8601 without offset.

## Imports

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/imports/qrp` | `multipart/form-data` field `files` (`.qrp`, max 20, 32MB each). `202` + `Location`. Requires `IMPORTER`/`ADMIN` when security enabled |
| `GET` | `/api/imports` | paginated jobs |
| `GET` | `/api/imports/{jobId}` | job + file summaries |
| `GET` | `/api/imports/{jobId}/files/{fileId}` | admin detail (hash, hints, validations) |
| `POST` | `/api/imports/files/{fileId}/reprocess` | Admin only (`ADMIN` when security on). Verifies raw via `openVerified`, new parse attempt, atomic swap of `active_parse_attempt_id` on success. Failure keeps prior pointer. `409` on corruption or active lease. Not in primary UI nav. |

## Catalog / sales / analytics

| Method | Path |
|---|---|
| `GET` | `/api/products?q=&page=&size=` |
| `GET` | `/api/products/{id}` |
| `GET` | `/api/sales?from=&to=&productId=&page=&size=` |
| `GET` | `/api/sales/{id}` |
| `GET` | `/api/dashboard?from=&to=&productId=` |

Queries only include rows from `artifact_publication.active_parse_attempt_id`.

## Auth (when `datahub.security.enabled=true`)

HTTP Basic. Roles: `VIEWER` (GET), `IMPORTER` (POST upload), `ADMIN` (reprocess + actuator metrics + all).

Unauthenticated API calls → `401`. Insufficient role → `403`.

## Errors

`application/problem+json` via Spring `ProblemDetail`. Stack traces never included.

## Ops

Actuator: `/actuator/health`, `/actuator/info` (public); `/actuator/metrics` (ADMIN when security on).

See `docs/ops.md` for CORS, limits, backup/restore and PWA cache rules.
