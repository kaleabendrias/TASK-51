#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# mysql-entrypoint.sh — Loads shared secrets then delegates to the
# official MySQL entrypoint.  Waits up to 30 seconds for the
# secrets file to appear (handling volume propagation delay), then
# refuses to start if it is still missing.
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

SECRETS_FILE="/run/secrets/booking/secrets.env"
MAX_WAIT=30
WAIT_INTERVAL=1

# ── Wait for secrets file with retry loop ────────────────────────
elapsed=0
while [ ! -f "$SECRETS_FILE" ]; do
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "[mysql-entrypoint] FATAL: ${SECRETS_FILE} not found after ${MAX_WAIT}s."
    echo "[mysql-entrypoint] The init-secrets service must run first."
    exit 1
  fi
  echo "[mysql-entrypoint] Waiting for ${SECRETS_FILE}... (${elapsed}/${MAX_WAIT}s)"
  sleep "$WAIT_INTERVAL"
  elapsed=$((elapsed + WAIT_INTERVAL))
done

echo "[mysql-entrypoint] Secrets file found after ${elapsed}s."

# Export every KEY=VALUE line into this shell's environment.
set -a
# shellcheck source=/dev/null
. "$SECRETS_FILE"
set +a

echo "[mysql-entrypoint] Secrets loaded — starting MySQL."

# Delegate to the official MySQL Docker entrypoint.
exec docker-entrypoint.sh "$@"
