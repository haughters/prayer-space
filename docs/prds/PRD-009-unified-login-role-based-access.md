# PRD-009: Unified Login & Role-Based Access

## Overview

Phase 5C replaces the separate `admin.html` and `intercessor.html` pages with a single consolidated application entry point: `/portal.html`. All users—including App Admins, Group Admins, and Intercessors—log in through the same form. Once authenticated, a unified authentication service (`auth-service`, evolved from `admin-service`) issues a single session cookie containing role claims, and the frontend dynamically renders a dashboard tailored to the user's role.

## Goals & Non-Goals

### Goals
- Consolidated entry point at `/portal.html` for all roles.
- Evolve `admin-service` into a unified `auth-service` that handles both username-based administrators and email-based intercessors.
- Consolidate session security into a single cookie: `pl-auth-token`.
- Enforce strict server-side Role-Based Access Control (RBAC) at the gateway and service layers.
- Redirect legacy `/admin.html` and `/intercessor.html` paths to the new unified `/portal.html`.
- Maintain the first-time setup experience for creating the initial App Admin if the system is uninitialized.

### Non-Goals
- Merging the database tables (the `Admins` and `IntercessorAccounts` tables remain separate, but are queried sequentially by the authentication service).
- Altering the request-submit flow on the landing page (`index.html`).

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-9.1 | As any user (intercessor, group admin, app admin), I want to log in through a single, clear login screen. | Must Have |
| US-9.2 | As an app admin, I want to see the full administrative panel (all groups, all prayers, admins management) after logging in. | Must Have |
| US-9.3 | As a group admin, I want to see only my assigned prayer group and its members and prayers after logging in. | Must Have |
| US-9.4 | As an intercessor, I want to see my groups, active prayers, and my prayer history after logging in. | Must Have |
| US-9.5 | As a developer, I want all api requests to be secured by a single, standard JWT session cookie containing clear role claims. | Must Have |
| US-9.6 | As a user visiting a legacy URL (e.g. `/admin.html`), I want to be redirected automatically to `/portal.html`. | Should Have |

## Functional Requirements

### FR-1: Unified Authentication Service (`auth-service`)

The existing `admin-service` is renamed/evolved into `auth-service` to act as the single source of authority for login verification.

#### Authentication Logic (`POST /api/auth/login`)
1. Accepts `{ identifier, password }`. The `identifier` can be a username or email.
2. Check the `Admins` table for a matching `username`.
   - If found: Validate password. If valid, issue JWT with role `APP_ADMIN` or `GROUP_ADMIN` (depending on the record's role attribute).
3. If not found in `Admins`: Check the `IntercessorAccounts` table for a matching `email`.
   - If found: Validate password. If valid, issue JWT with role `INTERCESSOR`.
4. If not found in either or password invalid: Return `401 Unauthorized`.

#### JWT Schema & Cookie Configuration
- **Cookie Name:** `pl-auth-token`
- **Session Duration:** 24 hours.
- **JWT Claims Payload:**
  ```json
  {
    "sub": "identifier_value",
    "role": "APP_ADMIN" | "GROUP_ADMIN" | "INTERCESSOR",
    "groupId": "associated_group_id_if_any",
    "email": "associated_email_if_intercessor"
  }
  ```

#### System Status Endpoint (`GET /api/auth/status`)
Replaces `/api/admin/status`. Returns current session details:
```json
{
  "initialized": true,
  "authenticated": true,
  "role": "GROUP_ADMIN",
  "identifier": "group_leader_1",
  "groupId": "dbb47746-86c2-4876-8099-a461c28c895c"
}
```

### FR-2: API Gateway Routing & RBAC Enforcement

The `api-gateway` secures the service endpoints based on roles passed in the JWT.

- **Gateway Cookie Decoding:** The gateway decodes the `pl-auth-token` cookie, validates the signature, and populates headers:
  - `X-User-Role`: User's role (`APP_ADMIN`, `GROUP_ADMIN`, `INTERCESSOR`).
  - `X-User-GroupId`: Group ID if scoped.
  - `X-User-Email`: Email if intercessor.
- **Access Restrictions:**
  - `/api/admin/**` paths: gateway or downstream filter blocks unless `X-User-Role` is `APP_ADMIN` or `GROUP_ADMIN`.
  - `/api/groups/**` admin modifications: require `APP_ADMIN` (or `GROUP_ADMIN` matched to the payload's `groupId`).

### FR-3: Unified Portal Frontend (`portal.html` & `portal.js`)

A single page application (SPA) shell replacing `admin.html` and `intercessor.html`.

#### Routing & Layout Flow
1. Load `/portal.html`. Call `GET /api/auth/status`.
2. **State: Uninitialized** -> Render Admin Setup Screen (first-time setup).
3. **State: Unauthenticated** -> Render Unified Login Screen (with email/username input).
4. **State: Authenticated** -> Render Dashboard Sidebar and Main Panel.
   - The navigation links and dashboard views are injected dynamically based on `role`:

| Role | Navigation Sidebar Items | Main View Canvas |
|------|-------------------------|------------------|
| **APP_ADMIN** | • Circles Dashboard <br> • Groups Management <br> • Administrators <br> • System Settings | Full list of all circles, prayers list, stats. Full CSV imports. |
| **GROUP_ADMIN** | • My Circle <br> • Circle Members <br> • Circle Prayers | Circle profile, member list, and prayers assigned to their specific group. |
| **INTERCESSOR** | • My Circles <br> • Active Prayers <br> • My Prayer History | Lists the user's groups, active prayers list, and a list of prayers they've supported. |

### FR-4: Redirect Filters

Ensure users aren't left stranded on deprecated URLs.
- **Frontend Redirects:** Add script tags inside `admin.html` and `intercessor.html` to instantly redirect window location to `/portal.html`.
- **Gateway Redirects (Optional):** Route HTTP requests for `/admin` or `/intercessor` directly to `/portal.html` at the Gateway level.

---

## API Contracts

### POST /api/auth/login
Authenticate a user and set the session cookie.

**Request:**
```http
POST /api/auth/login HTTP/1.1
Content-Type: application/json

{
  "identifier": "superadmin",
  "password": "password123"
}
```

**Success Response (200 OK):**
*Response sets `Set-Cookie: pl-auth-token=JWT_VALUE; HttpOnly; Path=/`*
```json
{
  "identifier": "superadmin",
  "role": "APP_ADMIN",
  "authenticated": true
}
```

---

### POST /api/auth/logout
Clear the authentication session.

**Request:**
```http
POST /api/auth/logout HTTP/1.1
```

**Success Response (200 OK):**
*Response sets `Set-Cookie: pl-auth-token=; HttpOnly; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT`*
```json
{
  "message": "Logged out successfully"
}
```

---

## Data Model Changes

No changes to the data schemas are needed. This phase is an integration and consolidation of existing schemas:
- Authentication queries `Admins` table first, then `IntercessorAccounts` table.

---

## UI/UX Requirements

- **Consolidated Design System:** Reuses the styling tokens, card layouts, sidebar classes, and responsive grids already defined in `docs/prds/PRD-006-frontend-design-system.md`.
- **Role Badging:** A small pill badge in the sidebar header indicates the active role (e.g. `System Administrator` in gold, `Group Lead` in green, `Intercessor` in blue).

---

## Non-Functional Requirements

- **Security Enforcement:** All routes under `/api/admin/**` must block requests with an `INTERCESSOR` role.
- **Redirection Latency:** The redirect from legacy pages must take place in less than 50ms (pure JS inline script header redirect).

---

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| `auth-service` | Backend | Unified token issuing, replacement for `admin-service`. |
| `api-gateway` | Gateway | Session verification, header injection, security filters. |
| Vite Frontend | Build | Build single `portal.html` bundle, remove deprecated pages. |

---

## Milestones & Acceptance Criteria

### Milestone 1: Unified Auth & JWT
- [ ] Rename/evolve `admin-service` to `auth-service`.
- [ ] Implement sequential table lookup login flow in `POST /api/auth/login`.
- [ ] Transition gateway configuration to use `pl-auth-token` cookie.
- [ ] Implement `GET /api/auth/status` returning role structure.

### Milestone 2: Frontend Migration & Portal Shell
- [ ] Create `portal.html` and `portal.js`.
- [ ] Integrate login forms and setup screens.
- [ ] Build conditional rendering logic to display role-appropriate views.
- [ ] Move administrative modules (Groups, Admins) from `admin.js` into the portal framework.

### Milestone 3: Intercessor Integration & Redirects
- [ ] Move intercessor dashboard views into the portal framework.
- [ ] Add redirection scripts to `admin.html` and `intercessor.html`.
- [ ] Perform E2E tests verifying an App Admin, Group Admin, and Intercessor can all successfully log in and see their expected layouts.

---

## Open Questions

1. **What happens if a user is both an Administrator and an Intercessor under the same identifier?**
   - *Recommendation:* Since Administrators use usernames (e.g. `superadmin`, `group_lead_1`) and Intercessors use email addresses (e.g. `jane@church.org`), conflict is naturally avoided. In the rare event of username-email identity collision, the service prioritizes the Administrator check first.
2. **Can a user log in to multiple sessions?**
   - *Recommendation:* Yes, sessions are stateless JWTs, so concurrent logins on different devices are fully supported.
