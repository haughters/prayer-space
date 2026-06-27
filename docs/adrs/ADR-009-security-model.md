# ADR-009: Security Model — Layered Authentication & Authorisation

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link has three distinct user types with different trust levels and access requirements:
1. **Regular users** (prayer requesters): No accounts, identified by device ID only.
2. **Intercessors**: Receive email links, interact via time-limited tokens.
3. **Administrators**: Manage groups, members, and view prayer dashboards. Require real authentication.

The system must balance frictionless access for prayer requesters with adequate security for admin operations and intercessor actions.

## Decision

Implement a **layered security model** with different authentication mechanisms for each user type.

### Layer 1: Regular Users (No Authentication)

- **Identity**: Opaque device ID (UUID v4) stored in localStorage/cookie (see ADR-005).
- **Authentication**: None. The device ID is not a credential.
- **Authorisation**: Data is scoped by device ID. prayer-service returns only prayers matching the requesting device ID. The `X-Device-ID` header is validated for format (UUID) but not authenticated.
- **Trust model**: We trust that only the device's browser knows the device ID. UUID v4 has 2^122 possible values, making brute-force guessing infeasible.
- **What a malicious actor could do with a stolen device ID**: View and close someone else's prayers. This is low-severity — prayers contain no personal identifying information.

### Layer 2: Intercessors (Token-Based, Stateless)

- **Identity**: Email address embedded in the token payload.
- **Authentication**: HMAC-signed tokens in email links.
- **Token structure**:
  - Payload: `{prayerId}:{intercessorEmail}:{expiryTimestamp}`
  - Signature: `HMAC-SHA256(secret_key, payload)`
  - Full token: `{Base64URL(signature)}|{expiryTimestamp}`
  - The email is NOT included in the URL token itself (for privacy). Instead, the prayerId + email combination is used during HMAC computation. The token validation endpoint receives the token and looks up the expected email from the original notification record or recomputes against all group members.
- **Alternative token approach (simpler)**: Include all payload components in the token:
  - Token: `{Base64URL(prayerId)}|{Base64URL(email)}|{expiryTimestamp}|{Base64URL(signature)}`
  - Validation: Decode components, recompute HMAC, compare signatures, check expiry.
  - **This simpler approach is recommended** for implementation clarity.
- **Expiry**: 30 days from email send time.
- **Idempotency**: The "I've Prayed" action is idempotent. Re-clicking the button does not increment the count again. prayer-service tracks which intercessor emails have prayed for each prayer (stored in a `prayedByEmails` set attribute on the Prayers table — or a separate `IntercessorPrayers` table if the set grows large).
- **Validation endpoint**: `POST /api/prayers/{prayerId}/prayed` with body `{ "intercessorToken": "..." }`. notification-service (or prayer-service) validates the token by:
  1. Decoding the Base64URL components.
  2. Checking that `expiryTimestamp` > now.
  3. Recomputing the HMAC with the secret key and comparing to the provided signature.
  4. If valid, incrementing `prayedForCount` on the prayer.

### Layer 3: Administrators (JWT-Based)

- **Identity**: Username stored in the `Admins` DynamoDB table.
- **Authentication**: Username + password login. Password is hashed with **bcrypt** (cost factor 12).
- **Session management**:
  - On successful login (`POST /api/admin/login`), admin-service issues a **JWT** containing:
    ```json
    {
      "sub": "admin-uuid",
      "username": "admin-username",
      "role": "APP_ADMIN",
      "groupId": "uuid-or-null",
      "iat": 1719403200,
      "exp": 1719489600
    }
    ```
  - JWT is signed with **HMAC-SHA256** using a secret key stored in AWS Secrets Manager.
  - JWT is set as an **HTTP-only, Secure, SameSite=Strict** cookie named `pl-admin-token`.
  - Expiry: 24 hours.
- **Authorisation**:
  - admin-service validates the JWT on every request to `/api/admin/**`.
  - `APP_ADMIN` role: Full access to all admin endpoints.
  - `GROUP_ADMIN` role: Access restricted to their assigned group. API returns `403 Forbidden` if they attempt to access another group's data. Enforced at the service layer, not just the UI.
- **First-time setup**:
  - `POST /api/admin/setup` with `{ username, password }`.
  - Only works if the `Admins` table is empty (no admins exist).
  - Creates the first `APP_ADMIN`.
  - This endpoint returns `403 Forbidden` if any admin already exists.

### API Security

| Concern | Implementation |
|---------|---------------|
| **HTTPS** | TLS termination at the AWS ALB. All traffic between ALB and gateway is within the VPC (unencrypted but private). |
| **CORS** | Configured at the api-gateway level (see ADR-007). Restricted to the frontend domain. |
| **Rate limiting** | 10 req/s per device ID for regular users, 30 req/s for admin routes (see ADR-007). |
| **Input validation** | All services validate input: prayer text max 2000 chars, update text max 1000 chars, passcode max 6 chars, names max 100 chars, emails max 254 chars. |
| **XSS prevention** | All user-generated text (prayer text, update text, group names) is HTML-escaped on output (frontend rendering). Backend stores raw text. |
| **CSRF protection** | Admin JWT cookie uses `SameSite=Strict`. Device ID cookie uses `SameSite=Lax`. Both prevent CSRF attacks. |
| **Content Security Policy** | Frontend sets CSP headers: `default-src 'self'; script-src 'self'; style-src 'self' fonts.googleapis.com; font-src fonts.gstatic.com; img-src 'self' data:;` |
| **Password requirements** | Admin passwords: minimum 8 characters, no other restrictions (NIST 800-63B guidance). |

## Consequences

### Positive
- **Zero friction for users**: No login required to submit prayers. Maximally accessible.
- **Stateless intercessor auth**: No session storage needed. Tokens are self-validating via HMAC.
- **Simple admin auth**: JWT cookies are well-understood and widely supported. No OAuth provider needed.
- **Defence in depth**: Each layer has its own appropriate security level.

### Negative
- **Device ID spoofing**: If an attacker obtains a device ID, they can view that user's prayers. Risk is low (UUIDs are unguessable) and impact is low (prayers contain no PII).
- **Token forwarding**: An intercessor could forward their email (with token) to someone else. That person could mark the prayer as "prayed for." This is a feature, not a bug — anyone who prays is welcome.
- **Admin password management**: No password reset flow in MVP. If an admin forgets their password, another APP_ADMIN must delete and re-create their account. If the last APP_ADMIN loses access, manual database intervention is required.
- **JWT secret rotation**: Rotating the JWT signing key invalidates all active admin sessions. Must coordinate rotation with a brief admin re-login.

## Alternatives Considered

### OAuth2 / OIDC (e.g., AWS Cognito, Auth0)
- **Pros**: Industry standard. Supports social login. Refresh tokens. Well-tested.
- **Cons**: Massively overkill for a system where regular users have no accounts. Adds external service dependency and cost. Configuration complexity.
- **Verdict**: Rejected. The no-account model for regular users makes OAuth irrelevant for 95% of the user base.

### API Keys for Intercessors
- **Pros**: Simple. Long-lived.
- **Cons**: Must be stored in the database. Must be managed (creation, revocation). Intercessors would need to "register" for a key.
- **Verdict**: Rejected. Email-based HMAC tokens are simpler and require no registration.
