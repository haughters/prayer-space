#!/bin/bash
set -euo pipefail

# This script smoke tests a deployed Lambda using its Function URL.

if [ -z "${SERVICE_URL:-}" ] || [ "$SERVICE_URL" = "None" ]; then
  echo "No Function URL configured, skipping."
  exit 0
fi

curl_and_check() {
  local path="$1"
  local method="$2"
  local expected="$3"
  local body="${4:-}"
  echo "Testing URL: $method ${SERVICE_URL}${path}"

  CURL_ARGS=(
    -s -o /tmp/curl.txt -w "%{http_code}"
    --aws-sigv4 "aws:amz:${AWS_REGION}:lambda"
    --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY"
    -H "x-amz-security-token:$AWS_SESSION_TOKEN"
    -X "$method"
  )
  if [ -n "$body" ]; then
    CURL_ARGS+=(-H "Content-Type: application/json" -d "$body")
  fi

  STATUS=$(curl "${CURL_ARGS[@]}" "${SERVICE_URL}${path#/}")
  if [ "$STATUS" = "$expected" ]; then
    echo "  ✅ Passed (HTTP $STATUS)"
  else
    echo "  ❌ FAILED — expected $expected, got $STATUS"
    return 1
  fi
}

FAILED=0
curl_and_check "actuator/health" "GET" "200" "" || FAILED=1

if [ -n "${SMOKE_TEST_PATH:-}" ]; then
  curl_and_check "$SMOKE_TEST_PATH" "${SMOKE_TEST_METHOD:-GET}" "${SMOKE_TEST_EXPECTED_STATUS:-200}" "${SMOKE_TEST_BODY:-}" || FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
