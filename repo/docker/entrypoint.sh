#!/usr/bin/env bash
# Docker-native runtime secret generation.
# Generates secure credentials and encryption keys on first launch
# via /dev/urandom — no .env files are created or persisted on the host.

set -euo pipefail

# Generate a secure random string of given length (hex-encoded)
gen_secret() {
  head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
}

# ── MySQL root password ──────────────────────────────────────────
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
  export MYSQL_ROOT_PASSWORD
  MYSQL_ROOT_PASSWORD="$(gen_secret 24)"
fi

# ── Application database credentials ────────────────────────────
if [ -z "${MYSQL_USER:-}" ]; then
  export MYSQL_USER="booking_app"
fi
if [ -z "${MYSQL_PASSWORD:-}" ]; then
  export MYSQL_PASSWORD
  MYSQL_PASSWORD="$(gen_secret 24)"
fi

# ── Encryption key (≥32 chars required for AES-256 via PBKDF2) ─
if [ -z "${ENCRYPTION_KEY:-}" ]; then
  export ENCRYPTION_KEY
  ENCRYPTION_KEY="$(gen_secret 32)"  # 32 bytes = 64 hex chars
fi

# ── Forward to the original command ─────────────────────────────
exec "$@"
