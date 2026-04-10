#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# init-secrets.sh — Single source of truth for runtime credentials.
#
# Generates all secrets once and writes them to a shared named
# volume so that both the MySQL and webapp containers read
# identical values.  Runs as a short-lived init container during
# `docker compose up`.
#
# Output: /run/secrets/booking/secrets.env  (sourced by both services)
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

SECRETS_DIR="/run/secrets/booking"
SECRETS_FILE="${SECRETS_DIR}/secrets.env"

# If secrets already exist (restart without volume wipe), keep them.
if [ -f "$SECRETS_FILE" ]; then
  echo "[init-secrets] Secrets file already exists — skipping generation."
  exit 0
fi

mkdir -p "$SECRETS_DIR"

# Generate a secure random hex string of N bytes (2*N hex chars).
gen_hex() {
  head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
}

MYSQL_ROOT_PASSWORD="$(gen_hex 24)"
MYSQL_USER="booking_app"
MYSQL_PASSWORD="$(gen_hex 24)"
ENCRYPTION_KEY="$(gen_hex 32)"   # 64 hex chars — exceeds 32-char minimum

cat > "$SECRETS_FILE" <<EOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD}
ENCRYPTION_KEY=${ENCRYPTION_KEY}
EOF

# Readable by all container UIDs (mysql runs as mysql, webapp as root).
# The volume itself is not exposed outside Docker.
chmod 644 "$SECRETS_FILE"
chmod 755 "$SECRETS_DIR"

# Sync to ensure the write is flushed to the volume backing store
# before this container exits and dependents start.
sync

echo "[init-secrets] Secrets written to ${SECRETS_FILE}"
