#!/bin/bash
set -euo pipefail

# This script deploys a Lambda function code and configuration.
# Required environment variables:
# - SVC: Name of the service (e.g. prayer-service)
# - DEPLOY_ENV: Deployment environment (test or live)
# - HAS_URL: 'true' or 'false'
# - PR_NUMBER: Pull request number (optional)
# - GITHUB_TOKEN: GitHub token for API queries (optional)
# - GITHUB_REPOSITORY: Repository owner/name (optional)

# Resolve AWS Account ID dynamically
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)
AWS_REGION=${AWS_REGION:-eu-west-1}

# Resolve prefixes and environments
if [ -n "${PR_NUMBER:-}" ]; then
  ENV_PREFIX="pr-${PR_NUMBER}"
  TABLE_PREFIX="pr-${PR_NUMBER}-"
  DEP_PREFIX="pr-${PR_NUMBER}-"
  SQS_ENV="test"
  BUS_ENV="test"
else
  ENV_PREFIX="$DEPLOY_ENV"
  TABLE_PREFIX="${DEPLOY_ENV}-"
  DEP_PREFIX="${DEPLOY_ENV}-"
  SQS_ENV="$DEPLOY_ENV"
  BUS_ENV="$DEPLOY_ENV"
fi

FUNC_NAME="${ENV_PREFIX}-${SVC}"
echo "Deploying function: $FUNC_NAME"

# 1. Check if Lambda exists
if aws lambda get-function --function-name "$FUNC_NAME" >/dev/null 2>&1; then
  echo "Function $FUNC_NAME exists. Updating code..."
  aws lambda update-function-code \
    --function-name "$FUNC_NAME" \
    --zip-file "fileb://artifact-dir/${SVC}-lambda.zip" >/dev/null
  aws lambda wait function-updated --function-name "$FUNC_NAME"
else
  # Create only if it is a PR deployment
  if [ -z "${PR_NUMBER:-}" ]; then
    echo "Error: Function $FUNC_NAME does not exist and this is not a PR deployment."
    exit 1
  fi
  echo "Creating function $FUNC_NAME..."
  
  # Fetch execution role from test function
  # Note: The test IAM role is defined and exists in the AWS account
  LAMBDA_ROLE=$(aws lambda get-function --function-name "test-${SVC}" --query 'Configuration.Role' --output text)
  
  aws lambda create-function \
    --function-name "$FUNC_NAME" \
    --runtime "provided.al2023" \
    --role "$LAMBDA_ROLE" \
    --handler "bootstrap" \
    --zip-file "fileb://artifact-dir/${SVC}-lambda.zip" \
    --timeout 30 \
    --memory-size 512 >/dev/null
    
  aws lambda wait function-active --function-name "$FUNC_NAME"
  
  # Configure Function URL for PR functions
  if [ "${HAS_URL:-}" = "true" ]; then
    aws lambda create-function-url-config \
      --function-name "$FUNC_NAME" \
      --auth-type "NONE" \
      --cors "AllowOrigins=*,AllowMethods=*" >/dev/null
      
    aws lambda add-permission \
      --function-name "$FUNC_NAME" \
      --statement-id "FunctionURLAllowPublicAccess" \
      --action "lambda:InvokeFunctionUrl" \
      --principal "*" \
      --function-url-auth-type "NONE" >/dev/null
  fi
fi

# 2. Resolve Service Dependencies URLs
get_dependency_url() {
  local dep=$1
  local url=""

  if [ -n "${PR_NUMBER:-}" ] && [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
    # Query GitHub API to see if the dependency service was changed in this PR
    CHANGED_FILES=$(curl -s -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      "https://api.github.com/repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/files?per_page=100" \
      | jq -r '.[].filename' || echo "")

    if echo "$CHANGED_FILES" | grep -q "^services/${dep}/"; then
      echo "Waiting for parallel PR deployment of ${dep}..." >&2
      for i in $(seq 1 40); do
        url=$(aws lambda get-function-url-config --function-name "pr-${PR_NUMBER}-${dep}" --query 'FunctionUrl' --output text 2>/dev/null || echo "")
        [ -n "$url" ] && break
        sleep 15
      done
    else
      url=$(aws lambda get-function-url-config --function-name "pr-${PR_NUMBER}-${dep}" --query 'FunctionUrl' --output text 2>/dev/null || echo "")
    fi
  fi

  if [ -z "$url" ]; then
    # Fallback to the test environment's alias URL (using qualifier stable)
    url=$(aws lambda get-function-url-config --function-name "test-${dep}" --qualifier stable --query 'FunctionUrl' --output text 2>/dev/null || echo "")
  fi
  
  # If still empty (or it's the live env), query the environment-specific URL
  if [ -z "$url" ] && [ -z "${PR_NUMBER:-}" ]; then
    url=$(aws lambda get-function-url-config --function-name "${DEP_PREFIX}${dep}" --qualifier stable --query 'FunctionUrl' --output text 2>/dev/null || echo "")
  fi

  echo "$url"
}

URL_GROUP_SERVICE="http://localhost:8083"
URL_PRAYER_SERVICE="http://localhost:8082"

if [ "${HAS_URL:-}" = "true" ]; then
  # Group Service URL
  if [ "$SVC" != "group-service" ]; then
    URL_GROUP_SERVICE=$(get_dependency_url "group-service")
    [ -z "$URL_GROUP_SERVICE" ] && URL_GROUP_SERVICE="http://localhost"
  fi
  
  # Prayer Service URL
  if [ "$SVC" = "admin-service" ] || [ "$SVC" = "notification-service" ]; then
    URL_PRAYER_SERVICE=$(get_dependency_url "prayer-service")
    [ -z "$URL_PRAYER_SERVICE" ] && URL_PRAYER_SERVICE="http://localhost"
  fi
fi

# 3. Configure Environment Variables
ENV_VARS="SPRING_PROFILES_ACTIVE=default"
ENV_VARS="${ENV_VARS},DEPLOY_ENV=${DEPLOY_ENV}"
ENV_VARS="${ENV_VARS},APP_TABLE_PREFIX=${TABLE_PREFIX}"
[ -n "${PR_NUMBER:-}" ] && ENV_VARS="${ENV_VARS},PR_NUMBER=${PR_NUMBER}"

case "$SVC" in
  prayer-service)
    ENV_VARS="${ENV_VARS},AWS_EVENTBRIDGE_BUS_NAME=${BUS_ENV}-prayer-link-bus"
    ENV_VARS="${ENV_VARS},SERVICES_GROUP_SERVICE_URL=${URL_GROUP_SERVICE}"
    ;;
  identity-service)
    ENV_VARS="${ENV_VARS},SERVICES_GROUP_SERVICE_URL=${URL_GROUP_SERVICE}"
    ;;
  admin-service)
    ENV_VARS="${ENV_VARS},SERVICES_GROUP_SERVICE_URL=${URL_GROUP_SERVICE}"
    ENV_VARS="${ENV_VARS},SERVICES_PRAYER_SERVICE_URL=${URL_PRAYER_SERVICE}"
    ;;
  notification-service)
    ENV_VARS="${ENV_VARS},AWS_SQS_QUEUE_URL=https://sqs.${AWS_REGION}.amazonaws.com/${AWS_ACCOUNT_ID}/${SQS_ENV}-notification-queue"
    ENV_VARS="${ENV_VARS},AWS_SQS_BOUNCE_QUEUE_URL=https://sqs.${AWS_REGION}.amazonaws.com/${AWS_ACCOUNT_ID}/${SQS_ENV}-bounce-queue"
    ENV_VARS="${ENV_VARS},AWS_SQS_NOTIFICATION_QUEUE=${SQS_ENV}-notification-queue"
    ENV_VARS="${ENV_VARS},AWS_SQS_BOUNCE_QUEUE=${SQS_ENV}-bounce-queue"
    ENV_VARS="${ENV_VARS},SERVICES_GROUP_SERVICE_URL=${URL_GROUP_SERVICE}"
    ENV_VARS="${ENV_VARS},SERVICES_PRAYER_SERVICE_URL=${URL_PRAYER_SERVICE}"
    ;;
esac

aws lambda update-function-configuration \
  --function-name "$FUNC_NAME" \
  --environment "Variables={${ENV_VARS}}" >/dev/null

aws lambda wait function-updated --function-name "$FUNC_NAME"

echo "Function environment configuration updated successfully."
