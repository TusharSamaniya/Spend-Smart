#!/bin/bash
# =============================================================================
# SpendSmart Backend Health & Integration Test Script
# Run from the infrastructure/ folder: bash test-backend.sh
# =============================================================================

set -euo pipefail

GATEWAY="http://localhost:8080"
EXPENSE_SVC="http://localhost:8081"
OCR_SVC="http://localhost:8082"
UPI_SVC="http://localhost:8084"
GST_SVC="http://localhost:8085"
WORKFLOW_SVC="http://localhost:8086"
ANALYTICS_SVC="http://localhost:8087"
NOTIFICATION_SVC="http://localhost:8088"

LOGIN_EMAIL="admin@spendsmart.in"
LOGIN_PASSWORD="test123"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
ACCESS_TOKEN=""

pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASS_COUNT++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; ((FAIL_COUNT++)); }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# ----------------------------------------------------------------------------
# Helper: check that HTTP status is 200 and body contains "UP"
# ----------------------------------------------------------------------------
check_health() {
  local label="$1"
  local url="$2"
  local response
  local status

  response=$(curl -s -w "\n%{http_code}" --max-time 5 "$url/actuator/health" 2>/dev/null || true)
  status=$(echo "$response" | tail -1)
  body=$(echo "$response" | head -1)

  if [[ "$status" == "200" ]] && echo "$body" | grep -q '"UP"'; then
    pass "$label is UP (HTTP $status)"
  else
    fail "$label health check failed (HTTP $status) → $body"
  fi
}

echo ""
echo "========================================================"
echo "  SpendSmart Backend Test Suite"
echo "========================================================"
echo ""

# TEST 1: Gateway health
info "Test 1 — API Gateway health"
check_health "api-gateway (via host:8080)" "$GATEWAY"

# TEST 2: Expense-service direct health
info "Test 2 — Expense-service health (direct port 8081)"
check_health "expense-service" "$EXPENSE_SVC"

# TEST 3: Other services health
info "Test 3 — Auxiliary service health checks"
check_health "ocr-service"          "$OCR_SVC"
check_health "upi-service"          "$UPI_SVC"
check_health "gst-service"          "$GST_SVC"
check_health "workflow-service"     "$WORKFLOW_SVC"
check_health "analytics-service"    "$ANALYTICS_SVC"
check_health "notification-service" "$NOTIFICATION_SVC"

# TEST 4: Login
info "Test 4 — POST /v1/auth/login"
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" --max-time 10 \
  -X POST "$GATEWAY/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$LOGIN_EMAIL\",\"password\":\"$LOGIN_PASSWORD\"}" 2>/dev/null || true)

LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | head -1)

if [[ "$LOGIN_STATUS" == "200" ]]; then
  # Extract accessToken using grep + sed (no jq dependency)
  ACCESS_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
  if [[ -n "$ACCESS_TOKEN" ]]; then
    pass "Login returned 200 with accessToken (first 40 chars: ${ACCESS_TOKEN:0:40}...)"
  else
    fail "Login returned 200 but no accessToken found in body: $LOGIN_BODY"
  fi
else
  fail "Login failed (HTTP $LOGIN_STATUS) → $LOGIN_BODY"
fi

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo ""
  echo -e "${RED}Cannot continue without a valid token. Fix login first.${NC}"
  echo -e "Passed: ${GREEN}$PASS_COUNT${NC}  Failed: ${RED}$FAIL_COUNT${NC}"
  exit 1
fi

AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"

# TEST 5: GET /v1/expenses
info "Test 5 — GET /v1/expenses"
EXPENSES_RESPONSE=$(curl -s -w "\n%{http_code}" --max-time 10 \
  -H "$AUTH_HEADER" \
  "$GATEWAY/v1/expenses" 2>/dev/null || true)
EXPENSES_STATUS=$(echo "$EXPENSES_RESPONSE" | tail -1)
EXPENSES_BODY=$(echo "$EXPENSES_RESPONSE" | head -1)

if [[ "$EXPENSES_STATUS" == "200" ]]; then
  pass "GET /v1/expenses returned 200 → ${EXPENSES_BODY:0:100}"
else
  fail "GET /v1/expenses failed (HTTP $EXPENSES_STATUS) → $EXPENSES_BODY"
fi

# TEST 6: GET /v1/analytics/summary
info "Test 6 — GET /v1/analytics/summary"
TODAY=$(date +%Y-%m-%d)
MONTH_START=$(date +%Y-%m-01)
ANALYTICS_RESPONSE=$(curl -s -w "\n%{http_code}" --max-time 10 \
  -H "$AUTH_HEADER" \
  "$GATEWAY/v1/analytics/summary?from=$MONTH_START&to=$TODAY&groupBy=category" 2>/dev/null || true)
ANALYTICS_STATUS=$(echo "$ANALYTICS_RESPONSE" | tail -1)
ANALYTICS_BODY=$(echo "$ANALYTICS_RESPONSE" | head -1)

if [[ "$ANALYTICS_STATUS" == "200" ]]; then
  pass "GET /v1/analytics/summary returned 200 → ${ANALYTICS_BODY:0:100}"
else
  fail "GET /v1/analytics/summary failed (HTTP $ANALYTICS_STATUS) → $ANALYTICS_BODY"
fi

# TEST 7: GET /v1/approvals/pending
info "Test 7 — GET /v1/approvals/pending"
APPROVALS_RESPONSE=$(curl -s -w "\n%{http_code}" --max-time 10 \
  -H "$AUTH_HEADER" \
  "$GATEWAY/v1/approvals/pending" 2>/dev/null || true)
APPROVALS_STATUS=$(echo "$APPROVALS_RESPONSE" | tail -1)
APPROVALS_BODY=$(echo "$APPROVALS_RESPONSE" | head -1)

if [[ "$APPROVALS_STATUS" == "200" ]]; then
  pass "GET /v1/approvals/pending returned 200 → ${APPROVALS_BODY:0:100}"
else
  fail "GET /v1/approvals/pending failed (HTTP $APPROVALS_STATUS) → $APPROVALS_BODY"
fi

# TEST 8: POST /v1/gst/validate
info "Test 8 — POST /v1/gst/validate"
GST_RESPONSE=$(curl -s -w "\n%{http_code}" --max-time 10 \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -X POST "$GATEWAY/v1/gst/validate" \
  -d '{"gstin":"27AAPFU0939F1ZV"}' 2>/dev/null || true)
GST_STATUS=$(echo "$GST_RESPONSE" | tail -1)
GST_BODY=$(echo "$GST_RESPONSE" | head -1)

if [[ "$GST_STATUS" == "200" ]]; then
  pass "POST /v1/gst/validate returned 200 → ${GST_BODY:0:100}"
else
  fail "POST /v1/gst/validate failed (HTTP $GST_STATUS) → $GST_BODY"
fi

# ----------------------------------------------------------------------------
echo ""
echo "========================================================"
echo -e "  Results: ${GREEN}$PASS_COUNT PASSED${NC}  ${RED}$FAIL_COUNT FAILED${NC}"
echo "========================================================"
echo ""
if [[ "$FAIL_COUNT" -eq 0 ]]; then
  echo -e "${GREEN}All tests passed! Backend is healthy.${NC}"
else
  echo -e "${RED}Some tests failed. Check the service logs with:${NC}"
  echo "  docker compose -f infrastructure/docker-compose.yml logs --tail=50 <service-name>"
fi
echo ""
