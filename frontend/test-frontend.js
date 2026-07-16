import { AwsClient } from 'aws4fetch';

const REGION = 'eu-west-1';
const IDENTITY_POOL_ID = 'eu-west-1:7aa0c4ca-fa67-44fd-b922-e2df91fb6807';

async function getCredentials() {
  const cognitoEndpoint = `https://cognito-identity.${REGION}.amazonaws.com/`;

  const idResponse = await fetch(cognitoEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityService.GetId'
    },
    body: JSON.stringify({ IdentityPoolId: IDENTITY_POOL_ID })
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
  return data.Credentials;
}

async function run() {
  const creds = await getCredentials();
  const aws = new AwsClient({
    accessKeyId: creds.AccessKeyId,
    secretAccessKey: creds.SecretKey,
    sessionToken: creds.SessionToken,
    region: REGION,
    service: 'lambda'
  });

  const url = 'https://uebeagfehht3so3mhwse6ihk340ouowq.lambda-url.eu-west-1.on.aws/api/prayers?deviceId=test-device-id';
  const res = await aws.fetch(url, {
    headers: {
      'Content-Type': 'application/json'
    }
  });
  
  console.log('Status:', res.status);
  console.log('Body:', await res.text());
}
run();
