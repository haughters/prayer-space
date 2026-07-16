import { AwsClient } from 'aws4fetch';

const REGION = import.meta.env.VITE_AWS_REGION;
const IDENTITY_POOL_ID = import.meta.env.VITE_COGNITO_IDENTITY_POOL_ID;

const URLS = {
  identity: import.meta.env.VITE_IDENTITY_LAMBDA_URL,
  prayers: import.meta.env.VITE_PRAYER_LAMBDA_URL,
  groups: import.meta.env.VITE_GROUP_LAMBDA_URL,
  admin: import.meta.env.VITE_ADMIN_LAMBDA_URL,
};

let cachedCredentials = null;
let credentialsExpiration = 0;

async function getCredentials() {
  const now = Date.now();
  if (cachedCredentials && credentialsExpiration > now + 5 * 60 * 1000) {
    return cachedCredentials;
  }

  const cognitoEndpoint = `https://cognito-identity.${REGION}.amazonaws.com/`;

  const idResponse = await fetch(cognitoEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityService.GetId'
    },
    body: JSON.stringify({ IdentityPoolId: IDENTITY_POOL_ID })
  });
  
  if (!idResponse.ok) throw new Error('Failed to fetch IdentityId');
  const { IdentityId } = await idResponse.json();

  const credResponse = await fetch(cognitoEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityService.GetCredentialsForIdentity'
    },
    body: JSON.stringify({ IdentityId })
  });

  if (!credResponse.ok) throw new Error('Failed to fetch Credentials');
  const data = await credResponse.json();
  
  cachedCredentials = data.Credentials;
  credentialsExpiration = data.Credentials.Expiration * 1000;
  
  return cachedCredentials;
}

export async function fetchSecureData(path, options = {}) {
  // We determine the correct base URL based on the path (e.g. /api/identity/...)
  // Wait, the API routes are like /api/identity/..., /api/prayers/...
  // The Lambda function URLs don't have the /api/identity prefix mapped in Spring Boot?
  // Actually, they DO have the prefix in Spring Boot!
  // Spring Boot controller has @RequestMapping("/api/identity")
  // So the request must be to `https://<lambda-url>/api/identity/...`
  
  const credentials = await getCredentials();

  const aws = new AwsClient({
    accessKeyId: credentials.AccessKeyId,
    secretAccessKey: credentials.SecretKey,
    sessionToken: credentials.SessionToken,
    region: REGION,
    service: 'lambda' 
  });

  let baseUrl = '';
  if (path.startsWith('/api/identity')) baseUrl = URLS.identity;
  else if (path.startsWith('/api/prayers')) baseUrl = URLS.prayers;
  else if (path.startsWith('/api/groups')) baseUrl = URLS.groups;
  else if (path.startsWith('/api/admin')) baseUrl = URLS.admin;
  else throw new Error(`Unknown API path: ${path}`);

  // Base URL from CDK already has a trailing slash usually, but URL constructor handles it
  const url = new URL(path, baseUrl).toString();

  const response = await aws.fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers
    }
  });

  return response;
}
