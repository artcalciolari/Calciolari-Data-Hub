# 0003 — Security default and production fail-fast

- Status: Accepted
- Date: 2026-08-09

## Context

The MVP runs on a controlled network first. Full OAuth/OIDC is out of scope,
but the plan requires that external exposure without authentication is blocked,
with roles `VIEWER` / `IMPORTER` / `ADMIN` before any public release.

## Decision

1. Default (`datahub.security.enabled=false`): API is open. Suitable only behind
   VPN/firewall. Documented loudly in README and `docs/ops.md`.
2. Profile `production`: forces `enabled=true` and `require-enabled=true`. Startup
   fails if users are missing.
3. When enabled: HTTP Basic + in-memory users from `DATAHUB_SECURITY_USERS`
   (`user:pass:ROLE1|ROLE2`). Actuator health/info stay public; metrics require ADMIN.
4. CORS defaults to empty (same-origin). Prefer reverse-proxy same-origin in prod.

## Consequences

- Local/dev and integration tests stay simple (auth off).
- Production misconfiguration fails closed.
- HTTP Basic is a stopgap; replace with SSO before internet exposure if needed.
