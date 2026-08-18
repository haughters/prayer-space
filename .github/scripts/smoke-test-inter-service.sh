#!/bin/bash
set -euo pipefail

# This script performs end-to-end smoke tests verifying that deployed microservices
# can communicate with each other via AWS SigV4 signed Lambda Function URLs.
#
# Environment variables:
# - ENV_PREFIX: Prefix for environments (default: "test", e.g. "test", "live", "pr-123")
# - AWS_REGION: AWS Region (default: "eu-west-1")

ENV_PREFIX="${ENV_PREFIX:-test}"
AWS_REGION="${AWS_REGION:-eu-west-1}"

# Export credentials if running from AWS CLI SSO / session profile
if [ -z "${AWS_ACCESS_KEY_ID:-}" ]; then
  eval "$(aws configure export-credentials --format env 2>/dev/null || true)"
fi

echo "=========================================================="
echo "🔍 Running Inter-Service Smoke Tests for Environment: $ENV_PREFIX"
echo "=========================================================="

get_service_url() {
  local svc="$1"
  local url=""

  if [[ "$ENV_PREFIX" =~ ^pr- ]]; then
    url=$(aws lambda get-function-url-config --function-name "${ENV_PREFIX}-${svc}" --query 'FunctionUrl' --output text 2>/dev/null || echo "")
  else
    url=$(aws lambda get-function-url-config --function-name "${ENV_PREFIX}-${svc}" --qualifier stable --query 'FunctionUrl' --output text 2>/dev/null || echo "")
    if [ -z "$url" ] || [ "$url" = "None" ]; then
      url=$(aws lambda get-function-url-config --function-name "${ENV_PREFIX}-${svc}" --query 'FunctionUrl' --output text 2>/dev/null || echo "")
    fi
  fi

  echo "$url"
}

curl_sigv4() {
  local url="$1"
  local path="$2"
  local method="$3"
  local expected="$4"
  local body="${5:-}"
  local description="$6"

  echo "  👉 Testing $description: $method ${url}${path#/}"

  local CURL_ARGS=(
    -s -o /tmp/smoke_out.txt -w "%{http_code}"
    -X "$method"
    --aws-sigv4 "aws:amz:${AWS_REGION}:lambda"
    --user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}"
  )

  if [ -n "${AWS_SESSION_TOKEN:-}" ]; then
    CURL_ARGS+=(-H "x-amz-security-token:${AWS_SESSION_TOKEN}")
  fi

  if [ -n "$body" ]; then
    CURL_ARGS+=(-H "Content-Type: application/json" -d "$body")
  fi

  local STATUS
  STATUS=$(curl "${CURL_ARGS[@]}" "${url}${path#/}")

  if [ "$STATUS" = "$expected" ]; then
    echo "     ✅ Passed (HTTP $STATUS)"
    return 0
  else
    echo "     ❌ FAILED — expected $expected, got $STATUS"
    echo "     Response: $(cat /tmp/smoke_out.txt 2>/dev/null || echo 'No response body')"
    return 1
  fi
}

ADMIN_URL=$(get_service_url "admin-service")
GROUP_URL=$(get_service_url "group-service")
IDENTITY_URL=$(get_service_url "identity-service")
PRAYER_URL=$(get_service_url "prayer-service")

FAILED=0

# 1. Test Group Service Direct Endpoint
if [ -n "$GROUP_URL" ] && [ "$GROUP_URL" != "None" ]; then
  echo ""
  echo "--- Testing group-service ---"
  curl_sigv4 "$GROUP_URL" "/actuator/health" "GET" "200" "" "group-service health" || FAILED=1
  curl_sigv4 "$GROUP_URL" "/api/groups/validate?passcode=INVALID" "GET" "404" "" "group-service passcode validation" || FAILED=1
fi

# 2. Test Admin Service Direct & Status Endpoint
if [ -n "$ADMIN_URL" ] && [ "$ADMIN_URL" != "None" ]; then
  echo ""
  echo "--- Testing admin-service ---"
  curl_sigv4 "$ADMIN_URL" "/actuator/health" "GET" "200" "" "admin-service health" || FAILED=1
  curl_sigv4 "$ADMIN_URL" "/api/auth/status" "GET" "200" "" "admin-service auth status" || FAILED=1
fi

# 3. Test Identity Service Direct Endpoint
if [ -n "$IDENTITY_URL" ] && [ "$IDENTITY_URL" != "None" ]; then
  echo ""
  echo "--- Testing identity-service ---"
  curl_sigv4 "$IDENTITY_URL" "/actuator/health" "GET" "200" "" "identity-service health" || FAILED=1
  curl_sigv4 "$IDENTITY_URL" "/api/identity/register" "POST" "400" "{}" "identity-service validation check" || FAILED=1
fi

# 4. Test Prayer Service Direct Endpoint
if [ -n "$PRAYER_URL" ] && [ "$PRAYER_URL" != "None" ]; then
  echo ""
  echo "--- Testing prayer-service ---"
  curl_sigv4 "$PRAYER_URL" "/actuator/health" "GET" "200" "" "prayer-service health" || FAILED=1
  curl_sigv4 "$PRAYER_URL" "/api/prayers?deviceId=00000000-0000-0000-0000-000000000000" "GET" "200" "" "prayer-service list prayers" || FAILED=1
fi

echo ""
if [ "$FAILED" -ne 0 ]; then
  echo "❌ Inter-Service Smoke Tests FAILED!"
  exit 1
else
  echo "✅ All Inter-Service Smoke Tests PASSED successfully!"
fi
