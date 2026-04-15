#!/usr/bin/env bash
#
# run_tests.sh — Automated quality gate for the Booking Portal
#
# Runs unit tests and API integration tests inside a Docker container,
# enforces ≥90% line coverage for both suites, and prints a summary.
#
# Usage:
#   ./run_tests.sh              # full suite
#   ./run_tests.sh unit         # unit tests only
#   ./run_tests.sh api          # api tests only (includes unit)
#
# Requirements: Docker (no host-installed Java/Maven needed)
#
set -euo pipefail

COVERAGE_THRESHOLD=90
IMAGE_NAME="booking-portal-tests"
CONTAINER_NAME="booking-tests-$$"
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'; BOLD='\033[1m'

cd "$(dirname "$0")"

# ─── Determine which suites to run ────────────────────────────
MODE="${1:-all}"
case "$MODE" in
  unit) MVN_GOAL="test"   ;;
  api)  MVN_GOAL="verify" ;;
  all)  MVN_GOAL="verify" ;;
  *)    echo "Usage: $0 [unit|api|all]"; exit 1 ;;
esac

echo -e "${BOLD}══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  Booking Portal — Quality Gate (mode: ${MODE})${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════════${NC}"
echo ""

# ─── Build test image ─────────────────────────────────────────
echo -e "${YELLOW}[1/4] Building test container...${NC}"
docker build -f Dockerfile.test -t "$IMAGE_NAME" . --quiet

# ─── Run tests ────────────────────────────────────────────────
echo -e "${YELLOW}[2/4] Running ${MODE} tests inside container...${NC}"
echo ""

PASS=true
docker run --rm --name "$CONTAINER_NAME" \
  -v "$(pwd)/test-results:/build/target/site" \
  "$IMAGE_NAME" \
  "$MVN_GOAL" -B 2>&1 | tee /tmp/booking-test-output.log || PASS=false

echo ""

# ─── Parse results ────────────────────────────────────────────
echo -e "${YELLOW}[3/4] Parsing test results...${NC}"
echo ""

# Extract totals from summary lines (lines without " -- in " are totals)
# Surefire prints its total first, Failsafe prints its total second
UNIT_SUMMARY=$(grep 'Tests run:' /tmp/booking-test-output.log | grep -v ' -- in ' | head -1 || echo "Tests run: 0, Failures: 0, Errors: 0, Skipped: 0")
UNIT_RUN=$(echo "$UNIT_SUMMARY" | grep -oP 'Tests run: \K[0-9]+')
UNIT_FAIL=$(echo "$UNIT_SUMMARY" | grep -oP 'Failures: \K[0-9]+')
UNIT_ERR=$(echo "$UNIT_SUMMARY" | grep -oP 'Errors: \K[0-9]+')
UNIT_SKIP=$(echo "$UNIT_SUMMARY" | grep -oP 'Skipped: \K[0-9]+')

if [[ "$MODE" != "unit" ]]; then
  API_SUMMARY=$(grep 'Tests run:' /tmp/booking-test-output.log | grep -v ' -- in ' | tail -1 || echo "Tests run: 0, Failures: 0, Errors: 0, Skipped: 0")
  API_RUN=$(echo "$API_SUMMARY" | grep -oP 'Tests run: \K[0-9]+')
  API_FAIL=$(echo "$API_SUMMARY" | grep -oP 'Failures: \K[0-9]+')
  API_ERR=$(echo "$API_SUMMARY" | grep -oP 'Errors: \K[0-9]+')
  API_SKIP=$(echo "$API_SUMMARY" | grep -oP 'Skipped: \K[0-9]+')
else
  API_RUN=0; API_FAIL=0; API_ERR=0; API_SKIP=0
fi

# ─── Coverage extraction ─────────────────────────────────────
# pom.xml enforces a single merged (unit + API) threshold via check-merged.
# JaCoCo prints "All coverage checks have been met." on success, or
# "Rule violated ... lines covered ratio is X" on failure.
# Do NOT parse per-suite ratios — pom.xml does not enforce them separately.
COVERAGE_MET=false
COVERAGE_RATIO=""
if grep -q "All coverage checks have been met\." /tmp/booking-test-output.log 2>/dev/null; then
  COVERAGE_MET=true
else
  # Extract the actual ratio from a violation line (if present)
  COVERAGE_RATIO=$(grep -oP 'lines covered ratio is \K[0-9.]+' /tmp/booking-test-output.log | tail -1 || echo "")
fi

# ─── Summary ──────────────────────────────────────────────────
echo -e "${BOLD}[4/4] Quality Gate Summary${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BOLD}Unit Tests (Surefire):${NC}"
echo -e "    Run: ${UNIT_RUN}  Failures: ${UNIT_FAIL}  Errors: ${UNIT_ERR}  Skipped: ${UNIT_SKIP}"
echo ""

if [[ "$MODE" != "unit" ]]; then
  echo -e "  ${BOLD}API + E2E Tests (Failsafe):${NC}"
  echo -e "    Run: ${API_RUN}  Failures: ${API_FAIL}  Errors: ${API_ERR}  Skipped: ${API_SKIP}"
  echo ""
fi

echo -e "  ${BOLD}Coverage Gate (merged unit + API, threshold ≥${COVERAGE_THRESHOLD}%):${NC}"
if [[ "$COVERAGE_MET" == "true" ]]; then
  echo -e "    ${GREEN}✓ All coverage checks met (≥${COVERAGE_THRESHOLD}% line coverage)${NC}"
elif [[ -n "$COVERAGE_RATIO" ]]; then
  COVERAGE_PCT=$(echo "$COVERAGE_RATIO" | awk '{printf "%.1f", $1 * 100}')
  echo -e "    ${RED}✗ Coverage below threshold: ${COVERAGE_PCT}% (required ≥${COVERAGE_THRESHOLD}%)${NC}"
else
  echo -e "    ${YELLOW}(Coverage gate not yet evaluated — build failed before verify phase)${NC}"
fi
echo ""

echo -e "  ${BOLD}Coverage Reports:${NC}"
echo -e "    Unit:   test-results/jacoco-unit/index.html"
echo -e "    API:    test-results/jacoco-api/index.html"
echo -e "    Merged: test-results/jacoco-merged/index.html"
echo ""

# ─── Verdict ──────────────────────────────────────────────────
TOTAL_FAIL=$((UNIT_FAIL + UNIT_ERR + API_FAIL + API_ERR))

if [[ "$PASS" == "true" && "$TOTAL_FAIL" -eq 0 ]]; then
  echo -e "${GREEN}${BOLD}  ✓ QUALITY GATE PASSED${NC}"
  echo -e "${GREEN}    All tests passed. Coverage thresholds met (≥${COVERAGE_THRESHOLD}%).${NC}"
  echo ""
  exit 0
else
  echo -e "${RED}${BOLD}  ✗ QUALITY GATE FAILED${NC}"
  if [[ "$TOTAL_FAIL" -gt 0 ]]; then
    echo -e "${RED}    ${TOTAL_FAIL} test(s) failed or errored.${NC}"
  fi
  if [[ "$PASS" != "true" ]]; then
    echo -e "${RED}    Maven build failed (test failures or coverage below ${COVERAGE_THRESHOLD}%).${NC}"
    echo -e "${RED}    Check /tmp/booking-test-output.log for details.${NC}"
  fi
  echo ""
  exit 1
fi
