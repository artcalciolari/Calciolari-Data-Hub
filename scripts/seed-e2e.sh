#!/usr/bin/env bash
# Upload fixtures A/B into a running Data Hub API so Playwright can exercise
# the seeded dashboard (combined totals 3.705,88 / 63,828).
set -euo pipefail

API="${DATAHUB_API_BASE:-http://127.0.0.1:8080}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A="$ROOT/backend/tests/Calciolari.DataHub.Tests/fixtures/qrp/fixture-a.qrp"
B="$ROOT/backend/tests/Calciolari.DataHub.Tests/fixtures/qrp/fixture-b.qrp"

if [[ ! -f "$A" || ! -f "$B" ]]; then
  echo "QRP fixtures missing under backend/tests/.../fixtures/qrp/" >&2
  exit 1
fi

wait_health() {
  local i
  for i in $(seq 1 90); do
    if curl -sf "$API/actuator/health" | grep -q UP; then
      return 0
    fi
    sleep 1
  done
  echo "API not healthy at $API" >&2
  return 1
}

job_id_from_body() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

wait_job() {
  local id="$1"
  local i status
  for i in $(seq 1 120); do
    status="$(curl -sf "$API/api/imports/$id" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')"
    case "$status" in
      PENDING|PROCESSING) sleep 0.5 ;;
      *) echo "job $id -> $status"; return 0 ;;
    esac
  done
  echo "timeout waiting for job $id (last status=$status)" >&2
  return 1
}

upload() {
  local file="$1"
  local name="$2"
  curl -sS -X POST "$API/api/imports/qrp" \
    -F "files=@${file};filename=${name};type=application/octet-stream"
}

wait_health

id_a="$(upload "$A" "AUDITORIA.QRP" | job_id_from_body)"
wait_job "$id_a"
id_b="$(upload "$B" "AUDITORIA 41, 01_07-20_07.QRP" | job_id_from_body)"
wait_job "$id_b"
echo "seeded fixtures A/B"
