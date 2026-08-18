#!/bin/bash
set -euo pipefail

# This script smoke tests a deployed Lambda using its Function URL.

# Export credentials if running from AWS CLI SSO / session profile
if [ -z "${AWS_ACCESS_KEY_ID:-}" ]; then
  eval "$(aws configure export-credentials --format env 2>/dev/null || true)"
fi

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
    -X "$method"
  )

  CURL_ARGS+=(
    --aws-sigv4 "aws:amz:${AWS_REGION:-eu-west-1}:lambda"
    --user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}"
  )
  if [ -n "${AWS_SESSION_TOKEN:-}" ]; then
    CURL_ARGS+=(-H "x-amz-security-token:${AWS_SESSION_TOKEN}")
  fi
  if [ -n "$body" ]; then
    CURL_ARGS+=(-H "Content-Type: application/json" -d "$body")
  fi

  STATUS=$(curl "${CURL_ARGS[@]}" "${SERVICE_URL}${path#/}")
  if [ "$STATUS" = "$expected" ]; then
    echo "  ✅ Passed (HTTP $STATUS)"
  else
    echo "  ❌ FAILED — expected $expected, got $STATUS"
    echo "  Response body: $(cat /tmp/curl.txt 2>/dev/null || echo 'No response body')"
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
