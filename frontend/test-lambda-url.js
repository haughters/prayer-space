import { AwsClient } from 'aws4fetch';
import { execSync } from 'child_process';

async function test() {
  const getExport = (name) => execSync(`aws cloudformation list-exports --query "Exports[?Name=='${name}'].Value" --output text`).toString().trim();
  
  const poolId = getExport('test-PrayerLinkViteIdentityPoolId');
  const lambdaUrl = 'https://2gihjqcve6tixkehibukrg7awq0thzrz.lambda-url.eu-west-1.on.aws/';
  const region = 'eu-west-1';
  
  console.log("Pool ID:", poolId);
  console.log("Lambda URL:", lambdaUrl);

  const cognitoEndpoint = `https://cognito-identity.${region}.amazonaws.com/`;

  const idResponse = await fetch(cognitoEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityService.GetId'
    },
    body: JSON.stringify({ IdentityPoolId: poolId })
  });
  const { IdentityId } = await idResponse.json();

  const credResponse = await fetch(cognitoEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityService.GetCredentialsForIdentity'
    },
    body: JSON.stringify({ IdentityId })
  });
  const data = await credResponse.json();
  const credentials = data.Credentials;

  const aws = new AwsClient({
    accessKeyId: credentials.AccessKeyId,
    secretAccessKey: credentials.SecretKey,
    sessionToken: credentials.SessionToken,
    region,
    service: 'lambda' 
  });

  const url = new URL('/api/identity/register', lambdaUrl).toString();
  console.log("Fetching:", url);
  
  const res = await aws.fetch(url, {
    method: 'POST',
    body: JSON.stringify({ deviceId: 'test-node-script' })
  });
  
  console.log("Status:", res.status);
  console.log("Body:", await res.text());
}

test().catch(console.error);
