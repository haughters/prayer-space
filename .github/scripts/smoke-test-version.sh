#!/bin/bash
set -euo pipefail

# This script smoke tests a specific Lambda version using direct invocation.

invoke_and_check() {
  local path="$1"
  local method="$2"
  local expected_status="$3"
  local body="${4:-}"
  local description="$5"
  echo "Testing: $description ($method $path)"

  local raw_path="$path"
  local query_string=""
  if [[ "$path" == *"?"* ]]; then
    query_string="${path#*\?}"
    raw_path="${path%%\?*}"
  fi

  PAYLOAD=$(jq -n \
    --arg path "$raw_path" \
    --arg query "$query_string" \
    --arg method "$method" \
    --arg body "$body" \
    '{
      version: "2.0",
      rawPath: $path,
      rawQueryString: $query,
      queryStringParameters: ([ $query | select(. != "") | split("&")[] | split("=") ] | map({(.[0]): .[1]}) | add // {}),
      headers: { "host": "localhost", "content-type": "application/json" },
      requestContext: {
        accountId: "123456789012",
        apiId: "api-id",
        domainName: "localhost",
        domainPrefix: "localhost",
        http: { method: $method, path: $path },
        requestId: "id",
        routeKey: "$default",
        stage: "$default"
      },
      isBase64Encoded: false
    } + (if $body != "" then { body: $body } else {} end)')

  aws lambda invoke --function-name "$FUNC_NAME" --qualifier "$PUBLISHED_VERSION" --payload "$PAYLOAD" --cli-binary-format raw-in-base64-out /tmp/smoke-response.json > /dev/null
  STATUS_CODE=$(jq -r '.statusCode // empty' /tmp/smoke-response.json)

  if [ "$STATUS_CODE" = "$expected_status" ]; then
    echo "  ✅ Passed (HTTP $STATUS_CODE)"
  else
    local body_out
    body_out=$(cat /tmp/smoke-response.json 2>/dev/null || echo "No response body")
    echo "  ❌ FAILED — expected $expected_status, got $STATUS_CODE"
    echo "  Response body: $body_out"
    return 1
  fi
}

FAILED=0
invoke_and_check "/actuator/health" "GET" "200" "" "Health endpoint" || FAILED=1

if [ -n "${SMOKE_TEST_PATH:-}" ]; then
  invoke_and_check "$SMOKE_TEST_PATH" "${SMOKE_TEST_METHOD:-GET}" "${SMOKE_TEST_EXPECTED_STATUS:-200}" "${SMOKE_TEST_BODY:-}" "Custom service smoke test" || FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
  echo "❌ Smoke tests FAILED for version $PUBLISHED_VERSION. Alias NOT updated."
  exit 1
fi
