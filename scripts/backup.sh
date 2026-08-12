#!/usr/bin/env bash
# Backup PostgreSQL + raw-storage as one logical unit.
set -euo pipefail

DEST_ROOT="${1:-./backups}"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
OUT="${DEST_ROOT}/datahub-${STAMP}"
mkdir -p "$OUT"

RAW_ROOT="${DATAHUB_RAW_STORAGE_ROOT:-./data/raw-storage}"

if [[ -n "${DATAHUB_CONNECTION_STRING:-}" ]]; then
  CS="$DATAHUB_CONNECTION_STRING"
  HOST="$(echo "$CS" | sed -n 's/.*Host=\([^;]*\).*/\1/p')"
  PORT="$(echo "$CS" | sed -n 's/.*Port=\([^;]*\).*/\1/p')"
  DB="$(echo "$CS" | sed -n 's/.*Database=\([^;]*\).*/\1/p')"
  DB_USER="$(echo "$CS" | sed -n 's/.*Username=\([^;]*\).*/\1/p')"
  DB_PASS="$(echo "$CS" | sed -n 's/.*Password=\([^;]*\).*/\1/p')"
  PORT="${PORT:-5432}"
  DB_USER="${DB_USER:-datahub}"
  DB_PASS="${DB_PASS:-change-me}"
else
  DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/datahub}"
  DB_USER="${SPRING_DATASOURCE_USERNAME:-datahub}"
  DB_PASS="${SPRING_DATASOURCE_PASSWORD:-change-me}"
  HOST="$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')"
  PORT="$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://[^:/]+:([0-9]+)/.*#\1#')"
  DB="$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://[^/]+/([^?]+).*#\1#')"
  if [[ "$PORT" == "$DB_URL" ]]; then PORT=5432; fi
fi

echo "Backing up PostgreSQL ${HOST}:${PORT}/${DB} …"
export PGPASSWORD="$DB_PASS"
pg_dump -h "$HOST" -p "$PORT" -U "$DB_USER" -d "$DB" -Fc -f "${OUT}/pg.dump"

echo "Backing up raw storage ${RAW_ROOT} …"
if [[ -d "$RAW_ROOT" ]]; then
  tar -C "$(dirname "$RAW_ROOT")" -czf "${OUT}/raw-storage.tgz" "$(basename "$RAW_ROOT")"
else
  echo "(raw root missing — creating empty archive)"
  mkdir -p "${OUT}/_empty_raw"
  tar -C "${OUT}/_empty_raw" -czf "${OUT}/raw-storage.tgz" .
  rm -rf "${OUT}/_empty_raw"
fi

{
  echo "created_at_utc=${STAMP}"
  echo "postgres=${HOST}:${PORT}/${DB}"
  echo "raw_root=${RAW_ROOT}"
  echo "pg_dump_format=custom"
  sha256sum "${OUT}/pg.dump" "${OUT}/raw-storage.tgz"
} > "${OUT}/MANIFEST.txt"

echo "Backup complete: ${OUT}"
cat "${OUT}/MANIFEST.txt"
