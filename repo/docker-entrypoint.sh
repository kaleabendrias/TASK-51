#!/usr/bin/env bash
# docker-entrypoint.sh
# Runs frontend Jest tests first, then delegates to Maven.
set -euo pipefail

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Step 1/2 — Frontend unit tests (Jest)"
echo "══════════════════════════════════════════════════════════════"
cd /build/frontend_tests
npm test
FRONTEND_EXIT=$?

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Step 2/2 — Backend tests (Maven: $*)"
echo "══════════════════════════════════════════════════════════════"
cd /build
exec mvn "$@"
