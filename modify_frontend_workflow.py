import os

with open('.github/workflows/frontend-deploy.yml', 'r') as f:
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
    if line.strip() == "if: github.actor != 'dependabot[bot]'":
        deploy_live_lines[i] = "    if: github.actor != 'dependabot[bot]' && github.event_name == 'push'"
    elif line.strip() == "name: ${{ github.event_name == 'pull_request' && format('pr-{0}-frontend', github.event.pull_request.number) || 'test-frontend' }}":
        deploy_live_lines[i] = "      name: 'live-frontend'"
    elif line.strip() == "FRONTEND_BUCKET=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkFrontendBucketName'].Value\" --output text)":
        deploy_live_lines[i] = "          FRONTEND_BUCKET=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkFrontendBucketName'].Value\" --output text)"
    elif line.strip() == "CLOUDFRONT_ID=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkCloudFrontDistributionId'].Value\" --output text)":
        deploy_live_lines[i] = "          CLOUDFRONT_ID=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkCloudFrontDistributionId'].Value\" --output text)"
    elif line.strip() == "CLOUDFRONT_DOMAIN=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkCloudFrontDomainName'].Value\" --output text)":
        deploy_live_lines[i] = "          CLOUDFRONT_DOMAIN=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkCloudFrontDomainName'].Value\" --output text)"
    elif line.strip() == "IDENTITY_POOL_ID=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkViteIdentityPoolId'].Value\" --output text)":
        deploy_live_lines[i] = "          IDENTITY_POOL_ID=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkViteIdentityPoolId'].Value\" --output text)"
    elif line.strip() == "REGION=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkViteRegion'].Value\" --output text)":
        deploy_live_lines[i] = "          REGION=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkViteRegion'].Value\" --output text)"
    elif line.strip() == "IDENTITY_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkidentity-serviceFunctionUrlOut'].Value\" --output text)":
        deploy_live_lines[i] = "            IDENTITY_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkidentity-serviceFunctionUrlOut'].Value\" --output text)"
    elif line.strip() == "PRAYER_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkprayer-serviceFunctionUrlOut'].Value\" --output text)":
        deploy_live_lines[i] = "            PRAYER_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkprayer-serviceFunctionUrlOut'].Value\" --output text)"
    elif line.strip() == "GROUP_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkgroup-serviceFunctionUrlOut'].Value\" --output text)":
        deploy_live_lines[i] = "            GROUP_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkgroup-serviceFunctionUrlOut'].Value\" --output text)"
    elif line.strip() == "ADMIN_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='test-PrayerLinkadmin-serviceFunctionUrlOut'].Value\" --output text)":
        deploy_live_lines[i] = "            ADMIN_URL=$(aws cloudformation list-exports --query \"Exports[?Name=='live-PrayerLinkadmin-serviceFunctionUrlOut'].Value\" --output text)"

new_content = '\n'.join(lines[:deploy_start] + deploy_test_lines + [''] + deploy_live_lines)

with open('.github/workflows/frontend-deploy.yml', 'w') as f:
    f.write(new_content)
