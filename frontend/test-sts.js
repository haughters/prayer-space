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

  const aws = new AwsClient({ accessKeyId: credentials.AccessKeyId, secretAccessKey: credentials.SecretKey, sessionToken: credentials.SessionToken, region, service: 'sts' });
  const res = await aws.fetch('https://sts.eu-west-1.amazonaws.com/?Action=GetCallerIdentity&Version=2011-06-15', { method: 'POST' });
  console.log(await res.text());
}
test().catch(console.error);
