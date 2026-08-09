# API — Calciolari Data Hub (Fase 3B)

Base path: `/api`. Money/quantities are decimal **strings**. `LocalDateTime` is ISO-8601 without offset.

## Imports

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/imports/qrp` | `multipart/form-data` field `files` (`.qrp`, max 20, 32MB each). `202` + `Location` |
| `GET` | `/api/imports` | paginated jobs |
| `GET` | `/api/imports/{jobId}` | job + file summaries |
| `GET` | `/api/imports/{jobId}/files/{fileId}` | admin detail (hash, hints, validations) |

## Catalog / sales / analytics

| Method | Path |
|---|---|
| `GET` | `/api/products?q=&page=&size=` |
| `GET` | `/api/products/{id}` |
| `GET` | `/api/sales?from=&to=&productId=&page=&size=` |
| `GET` | `/api/sales/{id}` |
| `GET` | `/api/dashboard?from=&to=&productId=` |

Queries only include rows from `artifact_publication.active_parse_attempt_id`.

## Errors

`application/problem+json` via Spring `ProblemDetail`.

## Ops

Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`.
