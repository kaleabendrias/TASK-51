#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# entrypoint.sh (webapp) — Loads shared secrets from the init
# container's volume, then launches the Spring Boot application.
# Waits up to 30 seconds for the secrets file to appear (handling
# volume propagation delay), then refuses to start if the file is
# still missing or if the ENCRYPTION_KEY does not meet the
# 32-character minimum.
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

SECRETS_FILE="/run/secrets/booking/secrets.env"
MAX_WAIT=30
WAIT_INTERVAL=1

# ── Wait for secrets file with retry loop ────────────────────────
elapsed=0
while [ ! -f "$SECRETS_FILE" ]; do
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "[webapp-entrypoint] FATAL: ${SECRETS_FILE} not found after ${MAX_WAIT}s."
    echo "[webapp-entrypoint] The init-secrets service must run first."
    exit 1
  fi
  echo "[webapp-entrypoint] Waiting for ${SECRETS_FILE}... (${elapsed}/${MAX_WAIT}s)"
  sleep "$WAIT_INTERVAL"
  elapsed=$((elapsed + WAIT_INTERVAL))
done

echo "[webapp-entrypoint] Secrets file found after ${elapsed}s."

# Export every KEY=VALUE line into this shell's environment.
set -a
# shellcheck source=/dev/null
. "$SECRETS_FILE"
set +a

# ── Pre-flight: validate secret strength before JVM startup ──────
if [ "${#ENCRYPTION_KEY}" -lt 32 ]; then
  echo "[webapp-entrypoint] FATAL: ENCRYPTION_KEY is ${#ENCRYPTION_KEY} chars (minimum 32)."
  exit 1
fi

if [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "[webapp-entrypoint] FATAL: MYSQL_PASSWORD is empty."
  exit 1
fi

# Map shared var names to Spring-expected env vars.
export SPRING_DATASOURCE_USERNAME="${MYSQL_USER}"
export SPRING_DATASOURCE_PASSWORD="${MYSQL_PASSWORD}"

echo "[webapp-entrypoint] Secrets loaded — starting application."
exec "$@"
