#!/usr/bin/env bash
# Restore PostgreSQL + raw-storage from a backup.sh output directory.
# Stop the backend before running.
set -euo pipefail

SRC="${1:-}"
if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "Usage: $0 /path/to/datahub-YYYYMMDD-HHMMSS" >&2
  exit 1
fi
if [[ ! -f "${SRC}/pg.dump" || ! -f "${SRC}/raw-storage.tgz" ]]; then
  echo "Missing pg.dump or raw-storage.tgz in ${SRC}" >&2
  exit 1
fi

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

echo "WARNING: this replaces database '${DB}' and raw root '${RAW_ROOT}'."
export PGPASSWORD="$DB_PASS"

echo "Restoring PostgreSQL …"
pg_restore -h "$HOST" -p "$PORT" -U "$DB_USER" -d "$DB" --clean --if-exists "${SRC}/pg.dump"

echo "Restoring raw storage …"
PARENT="$(dirname "$RAW_ROOT")"
BASE="$(basename "$RAW_ROOT")"
mkdir -p "$PARENT"
rm -rf "${RAW_ROOT}.bak" 2>/dev/null || true
if [[ -d "$RAW_ROOT" ]]; then
  mv "$RAW_ROOT" "${RAW_ROOT}.bak"
fi
tar -C "$PARENT" -xzf "${SRC}/raw-storage.tgz"
# If archive was empty placeholder, ensure directory exists
mkdir -p "$RAW_ROOT"

echo "Restore complete."
echo "Checklist:"
echo "  1. Start backend and hit GET /actuator/health"
echo "  2. SELECT count(*) FROM raw_artifact;"
echo "  3. Confirm storage keys exist under ${RAW_ROOT}"
echo "  4. Remove ${RAW_ROOT}.bak after verification"
