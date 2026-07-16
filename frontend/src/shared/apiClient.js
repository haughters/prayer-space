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
  else if (path.startsWith('/api/admin') || path.startsWith('/api/auth')) baseUrl = URLS.admin;
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

// Globally intercept fetch to automatically upgrade /api/ requests to SigV4
if (typeof window !== 'undefined' && typeof window.vitest === 'undefined' && !window.__vitest_environment__) {
  const isVitest = typeof process !== 'undefined' && process.env && process.env.NODE_ENV === 'test';
  
  if (!isVitest) {
    const originalFetch = window.fetch;
    window.fetch = async (input, init = {}) => {
      let path = '';
      if (typeof input === 'string') {
        path = input;
      } else if (input instanceof URL) {
        path = input.pathname + input.search;
      } else if (input instanceof Request) {
        path = input.url;
        try {
          const u = new URL(input.url);
          path = u.pathname + u.search;
        } catch(e) {
          path = input.url;
        }
      }

      // Only intercept relative /api/ calls. Avoid infinite recursion when aws4fetch sends signed Requests.
      let shouldIntercept = false;
      if (typeof path === 'string' && path.startsWith('/api/')) {
        // If the original input was a full absolute URL, make sure we only intercept our own relative calls
        if (typeof input === 'string') {
          shouldIntercept = input.startsWith('/api/');
        } else if (input instanceof Request) {
          try {
            // aws4fetch converts the URL to absolute AWS endpoint, we MUST NOT intercept it
            const parsedUrl = new URL(input.url);
            shouldIntercept = parsedUrl.origin === window.location.origin && parsedUrl.pathname.startsWith('/api/');
          } catch(e) {
            shouldIntercept = false;
          }
        } else if (input instanceof URL) {
          shouldIntercept = input.origin === window.location.origin && input.pathname.startsWith('/api/');
        }
      }

      if (shouldIntercept) {
        // If the input was a Request object, extract its properties
        if (input instanceof Request) {
          init = {
            method: input.method,
            headers: Object.fromEntries(input.headers.entries()),
            body: input.body,
            credentials: input.credentials,
            ...init
          };
        }
        return fetchSecureData(path, init);
      }

      return originalFetch(input, init);
    };
  }
}
