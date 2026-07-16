import { AwsClient } from 'aws4fetch';
import { execSync } from 'child_process';

async function test() {
  const getExport = (name) => execSync(`aws cloudformation list-exports --query "Exports[?Name=='${name}'].Value" --output text`).toString().trim();
  const poolId = getExport('test-PrayerLinkViteIdentityPoolId');
  const region = 'eu-west-1';
  
  const cognitoEndpoint = `https://cognito-identity.${region}.amazonaws.com/`;
  const idResponse = await fetch(cognitoEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-amz-json-1.1', 'X-Amz-Target': 'AWSCognitoIdentityService.GetId' }, body: JSON.stringify({ IdentityPoolId: poolId }) });
  const { IdentityId } = await idResponse.json();
  const credResponse = await fetch(cognitoEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-amz-json-1.1', 'X-Amz-Target': 'AWSCognitoIdentityService.GetCredentialsForIdentity' }, body: JSON.stringify({ IdentityId }) });
  const data = await credResponse.json();
  const credentials = data.Credentials;

  const aws = new AwsClient({ accessKeyId: credentials.AccessKeyId, secretAccessKey: credentials.SecretKey, sessionToken: credentials.SessionToken, region, service: 'lambda' });
  const lambdaUrl = 'https://74fkulpmzewyjpsq5x2oeb4ajy0qctbv.lambda-url.eu-west-1.on.aws/';
  const url = new URL('/api/prayers?deviceId=test1234', lambdaUrl).toString();
  console.log("Fetching:", url);
  const res = await aws.fetch(url, { method: 'GET' });
  console.log("Status:", res.status);
  console.log("Body:", await res.text());
}
test().catch(console.error);
