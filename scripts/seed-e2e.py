#!/usr/bin/env python3
"""Upload fixtures A/B into a running Data Hub API for Playwright."""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = os.environ.get("DATAHUB_API_BASE", "http://127.0.0.1:8080").rstrip("/")
ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "backend/tests/Calciolari.DataHub.Tests/fixtures/qrp"
FILES = [
    (FIXTURES / "fixture-a.qrp", "AUDITORIA.QRP"),
    (FIXTURES / "fixture-b.qrp", "AUDITORIA 41, 01_07-20_07.QRP"),
]


def wait_health() -> None:
    deadline = time.time() + 90
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"{API}/actuator/health", timeout=2) as response:
                if b"UP" in response.read():
                    return
        except (urllib.error.URLError, TimeoutError):
            time.sleep(1)
    raise SystemExit(f"API not healthy at {API}")


def upload(path: Path, filename: str) -> str:
    data = path.read_bytes()
    boundary = "----DataHubE2EBoundary"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8") + data + f"\r\n--{boundary}--\r\n".encode("utf-8")
    request = urllib.request.Request(
        f"{API}/api/imports/qrp",
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request) as response:
        payload = json.loads(response.read().decode())
    return payload["id"]


def wait_job(job_id: str) -> str:
    deadline = time.time() + 60
    status = "UNKNOWN"
    while time.time() < deadline:
        with urllib.request.urlopen(f"{API}/api/imports/{job_id}") as response:
            status = json.loads(response.read().decode())["status"]
        if status not in {"PENDING", "PROCESSING"}:
            print(f"job {job_id} -> {status}")
            return status
        time.sleep(0.5)
    raise SystemExit(f"timeout waiting for job {job_id} (last status={status})")


def main() -> int:
    for path, _name in FILES:
        if not path.is_file():
            raise SystemExit(f"QRP fixture missing: {path}")
    wait_health()
    for path, name in FILES:
        wait_job(upload(path, name))
    print("seeded fixtures A/B")
    return 0


if __name__ == "__main__":
    sys.exit(main())
