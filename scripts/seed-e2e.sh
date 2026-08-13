#!/usr/bin/env bash
# Wrapper so `bash scripts/seed-e2e.sh` works in CI and locally.
set -euo pipefail
exec python3 "$(cd "$(dirname "$0")" && pwd)/seed-e2e.py" "$@"
