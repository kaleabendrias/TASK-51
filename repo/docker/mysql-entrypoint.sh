#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# mysql-entrypoint.sh — Loads shared secrets then delegates to the
# official MySQL entrypoint.  Refuses to start if the secrets file
# is missing, ensuring the database never boots with empty or
# default credentials.
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

SECRETS_FILE="/run/secrets/booking/secrets.env"

if [ ! -f "$SECRETS_FILE" ]; then
  echo "[mysql-entrypoint] FATAL: ${SECRETS_FILE} not found."
  echo "[mysql-entrypoint] The init-secrets service must run first."
  exit 1
fi

# Export every KEY=VALUE line into this shell's environment.
set -a
# shellcheck source=/dev/null
. "$SECRETS_FILE"
set +a

echo "[mysql-entrypoint] Secrets loaded — starting MySQL."

# Delegate to the official MySQL Docker entrypoint.
exec docker-entrypoint.sh "$@"
