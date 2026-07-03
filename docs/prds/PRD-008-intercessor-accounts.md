# PRD-008: Intercessor Accounts

## Overview

While Phase 5A allows intercessors to browse prayers anonymously using HMAC links, Phase 5B introduces permanent accounts for intercessors. This allows intercessors to register with an email and password, log into a private portal, see all of their assigned groups, browse all active/closed prayers, and track their participation. Account registration is restricted to email addresses that have been previously added to a group by an administrator.

## Goals & Non-Goals

### Goals
- Allow intercessors to create email/password accounts linked to their group memberships.
- Restrict registration to pre-approved emails (those already added to at least one group by an administrator).
- Build a dedicated, secure intercessor portal (`intercessor.html`) to browse group prayers, filtered by status.
- Implement JWT-based session security via a secure HTTP-only cookie (`pl-intercessor-token`).
- Preserve the existing login-free HMAC action links (login is optional, providing a richer, self-service channel).

### Non-Goals
- Allowing public, unapproved registrations (self-signup without prior administrative addition).
- Sharing account credentials between admins and intercessors (these remain distinct schemas/logins in this phase).
- Allowing intercessors to manage group settings or member lists.

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-8.1 | As an intercessor, I want to create an account using the email address my church group registered, so I can access my prayer portal. | Must Have |
| US-8.2 | As an intercessor, I want to prevent strangers from signing up by ensuring registration requires pre-approval. | Must Have |
| US-8.3 | As an intercessor, I want to log in securely with my email and password. | Must Have |
| US-8.4 | As a logged-in intercessor, I want to see a list of my prayer circles and view all active requests for any circle. | Must Have |
| US-8.5 | As a logged-in intercessor, I want to mark requests as "prayed-for" without needing an HMAC token. | Must Have |
| US-8.6 | As a logged-in intercessor, I want to see which prayers I have already supported during my current session. | Should Have |
| US-8.7 | As an admin, I want to send an invitation email to a newly added member to prompt them to register. | Should Have |

## Functional Requirements

### FR-1: Backend Account Service (`identity-service` & DynamoDB)

The `identity-service` handles credentials and account registration for intercessors.

#### Database Schema: `IntercessorAccounts` Table
A new DynamoDB table is created to store intercessor credentials.
- **Table Name:** `IntercessorAccounts`
- **Primary Key:** `email` (String, Partition Key)
- **Attributes:**
  - `passwordHash` (String): BCrypt hashed password.
  - `name` (String): Display name of the intercessor.
  - `createdAt` (String): ISO-8601 timestamp.

#### Registration Verification Logic
1. When a user requests registration at `POST /api/identity/intercessor/register`:
2. Call `group-service` internally to check if the provided email exists in any `GroupMember` records.
3. If the email is NOT found in any group: reject with `400 Bad Request` ("Email is not pre-authorized. Please contact your group administrator").
4. Check if a record already exists in the `IntercessorAccounts` table for this email. If yes: reject with `409 Conflict` ("Account already exists").
5. Hash the password using BCrypt and save the new account record.
6. Automatically log the user in by setting the `pl-intercessor-token` cookie.

#### Authentication & JWT Security
- **Cookie Name:** `pl-intercessor-token`
- **Configuration:** `HttpOnly`, `Secure` (except in local development), `SameSite=Strict`, 30-day expiry.
- **JWT Claims:**
  ```json
  {
    "sub": "jane@church.org",
    "name": "Jane Doe",
    "role": "INTERCESSOR"
  }
  ```

### FR-2: API Gateway Routing (`api-gateway`)

The gateway intercepts and validates request sessions.
- **Route:** `/api/identity/intercessor/**` routed to `identity-service`.
- **Route:** `/api/intercessor/**` routed to `prayer-service` / `group-service` as appropriate.
- **Authorization:** If a route under `/api/intercessor/` is hit, the gateway validates that the `pl-intercessor-token` cookie is present, decodes it, and forwards the email claim in the `X-User-Email` HTTP header.

### FR-3: Intercessor Portal Frontend (`intercessor.html` / `intercessor.js`)

A dedicated frontend portal interface.

#### Views
1. **Login View:**
   - Email and password inputs.
   - Link to registration.
2. **Registration View:**
   - Pre-fills email if `?email=...` query param is present.
   - Name, Password, Confirm Password inputs.
3. **Portal Dashboard:**
   - **Sidebar:** Lists all groups the intercessor belongs to (fetched via `GET /api/groups/member/me`).
   - **Main Area:** Lists prayers for the selected group.
     - Toggle to filter by Status: "Active" (`OPEN`) vs "Answered/Closed" (`CLOSED`).
     - Prayer cards display text, creation date, total prayer count, and an "I have prayed" button.
     - If the user has already prayed for a prayer, the button is disabled and reads "Prayed 🙏".

### FR-4: Portal-Based Prayer Marking (prayer-service)

A new authenticated endpoint is added to record prayers from the portal using session auth instead of HMAC.

- **Endpoint:** `POST /api/prayers/{prayerId}/prayed/auth`
- **Authentication:** Relies on gateway validating the JWT and injecting the `X-User-Email` header.
- **Behavior:**
  - Extracts user's email from `X-User-Email` header.
  - Checks if email is in the prayer's `prayedByEmails` set. If yes: returns `409 Conflict`.
  - If no: Appends email to `prayedByEmails` set and atomically increments `prayedForCount`.

---

## API Contracts

### POST /api/identity/intercessor/register
Register a new intercessor account.

**Request:**
```http
POST /api/identity/intercessor/register HTTP/1.1
Content-Type: application/json

{
  "email": "jane@church.org",
  "name": "Jane Doe",
  "password": "Password123!"
}
```

**Success Response (201 Created):**
*Response sets `Set-Cookie: pl-intercessor-token=JWT_VALUE; HttpOnly; Path=/`*
```json
{
  "email": "jane@church.org",
  "name": "Jane Doe"
}
```

---

### POST /api/identity/intercessor/login
Log in to an intercessor account.

**Request:**
```http
POST /api/identity/intercessor/login HTTP/1.1
Content-Type: application/json

{
  "email": "jane@church.org",
  "password": "Password123!"
}
```

**Success Response (200 OK):**
*Response sets `Set-Cookie: pl-intercessor-token=JWT_VALUE; HttpOnly; Path=/`*
```json
{
  "email": "jane@church.org",
  "name": "Jane Doe"
}
```

---

### GET /api/groups/member/me
Fetch groups for the logged-in intercessor.

**Request:**
```http
GET /api/groups/member/me HTTP/1.1
X-User-Email: jane@church.org
```

**Success Response (200 OK):**
```json
[
  {
    "groupId": "dbb47746-86c2-4876-8099-a461c28c895c",
    "name": "Grace Church Intercessors"
  }
]
```

---

## Data Model Changes

### New Table: `IntercessorAccounts`

| Attribute | Type | Key Type | Description |
|-----------|------|----------|-------------|
| `email` | String | Hash Key (PK) | The intercessor's pre-approved email address. |
| `passwordHash` | String | Attribute | BCrypt hash of the user's password. |
| `name` | String | Attribute | The user's display name. |
| `createdAt` | String | Attribute | ISO-8601 creation timestamp. |

---

## UI/UX Requirements

- **Design Style:** Standard glassmorphism styling, clean inputs, visible password-toggle eye icons.
- **Already-Prayed Styling:** Prayers already supported by the user show a gold outline, a green checkmark, and a disabled button saying "Prayed for ✓".
- **Responsive Layout:** Sidebar slides away into a hamburger menu on screens smaller than 768px.

---

## Non-Functional Requirements

- **Session Expiry:** Session lasts for 30 days unless the cookie is explicitly cleared via logout.
- **Rate Limiting:** Registration and login endpoints are limited to 5 requests per minute per IP.

---

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| `identity-service` | Backend | Handle `IntercessorAccounts` table operations and JWT signing. |
| `group-service` | Backend | Provide validation of member email existence. |
| `prayer-service` | Backend | Provide `/prayed/auth` endpoint and JWT parsing helpers. |
| `api-gateway` | Gateway | Secure `/api/intercessor/**` paths and inject headers. |

---

## Milestones & Acceptance Criteria

### Milestone 1: Authentication API
- [ ] Create `IntercessorAccounts` DynamoDB Table.
- [ ] Implement registration and login API in `identity-service` with BCrypt hashing.
- [ ] Validate registration against group membership emails.

### Milestone 2: Secured Gateway & Portal APIs
- [ ] Setup `api-gateway` token decryption and header injection.
- [ ] Implement `GET /api/groups/member/me` in `group-service`.
- [ ] Implement `POST /api/prayers/{prayerId}/prayed/auth` in `prayer-service`.

### Milestone 3: Intercessor Portal Frontend
- [ ] Build login and registration screens.
- [ ] Build the interactive portal dashboard with sidebar group listing and status filtering.
- [ ] Verify that marking a prayer increments the server count and updates the card state.

---

## Open Questions

1. **What happens if an intercessor changes their email?**
   - *Recommendation:* Email changes must be managed by the administrator in the Group Members screen (deleting the old email and adding the new one), followed by the user registering a new account. Email updating is deferred to post-MVP.
2. **Should we support OAuth (e.g. Google Login)?**
   - *Recommendation:* Keep it password-based with local BCrypt hash for simplicity. OAuth can be added as a gateway plugin in future phases.
