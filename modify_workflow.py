import os

with open('.github/workflows/service-workflow.yml', 'r') as f:
    content = f.read()

lines = content.split('\n')
deploy_start = -1
for i, line in enumerate(lines):
    if line.startswith('  deploy:'):
        deploy_start = i
        break

deploy_lines = lines[deploy_start:]
deploy_test_lines = deploy_lines.copy()
deploy_test_lines[0] = '  deploy-test:'

deploy_live_lines = deploy_lines.copy()
deploy_live_lines[0] = '  deploy-live:'

for i, line in enumerate(deploy_live_lines):
    if line.strip() == "needs: build-and-test":
        deploy_live_lines[i] = "    needs: deploy-test"
    elif line.strip() == "if: github.actor != 'dependabot[bot]'":
        deploy_live_lines[i] = "    if: github.actor != 'dependabot[bot]' && github.event_name == 'push'"
    elif line.strip() == "name: ${{ github.event_name == 'pull_request' && format('pr-{0}-{1}', github.event.pull_request.number, inputs.service) || format('test-{0}', inputs.service) }}":
        deploy_live_lines[i] = "      name: ${{ format('live-{0}', inputs.service) }}"
    elif line.strip() == "ENV_PREFIX=\"test\"":
        deploy_live_lines[i] = "            ENV_PREFIX=\"live\""
    elif line.strip() == "echo \"TABLE_PREFIX=test-\" >> $GITHUB_ENV":
        deploy_live_lines[i] = "          echo \"TABLE_PREFIX=live-\" >> $GITHUB_ENV"
    elif line.strip() == "echo \"DEPLOY_ENV=test\" >> $GITHUB_ENV":
        deploy_live_lines[i] = "          echo \"DEPLOY_ENV=live\" >> $GITHUB_ENV"
    elif line.strip() == "LAMBDA_ROLE=$(aws lambda get-function --function-name \"test-${SVC}\" --query 'Configuration.Role' --output text)":
        deploy_live_lines[i] = "            LAMBDA_ROLE=$(aws lambda get-function --function-name \"live-${SVC}\" --query 'Configuration.Role' --output text)"
    elif line.strip() == "url=$(aws lambda get-function-url-config --function-name \"test-${dep}\" --query 'FunctionUrl' --output text 2>/dev/null || echo \"\")":
        deploy_live_lines[i] = "              url=$(aws lambda get-function-url-config --function-name \"live-${dep}\" --query 'FunctionUrl' --output text 2>/dev/null || echo \"\")"
    elif "test-prayer-link-bus" in line:
        deploy_live_lines[i] = line.replace("test-prayer-link-bus", "live-prayer-link-bus")
    elif "test-notification-queue" in line:
        deploy_live_lines[i] = line.replace("test-notification-queue", "live-notification-queue")
    elif "test-bounce-queue" in line:
        deploy_live_lines[i] = line.replace("test-bounce-queue", "live-bounce-queue")
    # PR block replacement (since deploy-live shouldn't try to look for PR stuff anyway)
    elif 'if [ "${{ github.event_name }}" = "pull_request" ]; then' in line:
        pass # We'll just leave the logic, since event_name == 'push' is enforced by the job `if`
        
new_content = '\n'.join(lines[:deploy_start] + deploy_test_lines + [''] + deploy_live_lines)

with open('.github/workflows/service-workflow.yml', 'w') as f:
    f.write(new_content)
