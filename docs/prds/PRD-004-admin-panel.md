# PRD-004: Admin Panel

## Overview

The admin panel is a hidden section of the frontend that allows application administrators to manage prayer groups, group members, and view prayer dashboards. It supports two roles: APP_ADMIN (full access) and GROUP_ADMIN (access limited to their assigned group). The admin panel is accessed via `/admin` and has no links from the main site.

## Goals & Non-Goals

### Goals
- Provide full CRUD management for prayer groups and their members.
- Offer a prayer dashboard with filtering and pagination.
- Support two admin roles with appropriate access controls.
- Enable first-time setup to create the initial administrator.
- Allow bulk member import via CSV.
- Generate downloadable QR codes for each group.

### Non-Goals
- Real-time analytics or advanced reporting.
- Admin mobile app.
- Audit logging of admin actions (future enhancement).
- Email template customisation by admins.

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-4.1 | As an app admin, I want to create prayer groups so churches can organise their intercessors. | Must Have |
| US-4.2 | As an app admin, I want to add and remove members from any group. | Must Have |
| US-4.3 | As an app admin, I want to view all prayers with filtering by status, group, and date. | Must Have |
| US-4.4 | As an app admin, I want to create and manage other admin accounts. | Must Have |
| US-4.5 | As an app admin, I want to set whether a group receives unassigned (round-robin) prayers. | Must Have |
| US-4.6 | As a group admin, I want to manage only my group's members and passcode. | Must Have |
| US-4.7 | As an admin, I want to log in with a username and password. | Must Have |
| US-4.8 | As an admin, I want to bulk-add members by pasting CSV data. | Should Have |
| US-4.9 | As an admin, I want to download a QR code for my group to share with prayer requesters. | Should Have |
| US-4.10 | As the first user, I want to set up the initial admin account on first visit. | Must Have |

## Functional Requirements

### FR-1: Authentication

#### First-Time Setup
- On navigating to `/admin`, the frontend calls `GET /api/admin/status`.
- Response: `{ "initialized": true|false, "authenticated": true|false, "role": "APP_ADMIN"|"GROUP_ADMIN"|null, "groupId": "uuid"|null }`.
- If `initialized: false` (no admins exist): Show the **Setup Form**:
  - Username input (required, 3-50 chars, alphanumeric + underscore).
  - Password input (required, min 8 chars). Show/hide toggle.
  - Password confirmation input.
  - "Create Admin Account" button.
  - On submit: `POST /api/admin/setup` with `{ "username": "...", "password": "..." }`.
    - **201 response**: Auto-login (set JWT cookie). Redirect to dashboard.
    - **403 response**: "An administrator already exists. Please log in." (race condition).
    - **400 response**: Show validation errors.

#### Login
- If `initialized: true` and `authenticated: false`: Show the **Login Form**:
  - Username input.
  - Password input with show/hide toggle.
  - "Log In" button.
  - On submit: `POST /api/admin/login` with `{ "username": "...", "password": "..." }`.
    - **200 response**: JWT cookie is set by the server. Frontend stores `role` and `groupId` in memory (not localStorage). Redirect to dashboard.
    - **401 response**: "Invalid username or password."
    - **429 response**: "Too many login attempts. Please try again later." (rate-limited to 5 attempts per IP per minute).

#### Session Management
- JWT cookie: `pl-admin-token`, HTTP-only, Secure, SameSite=Strict, 24-hour expiry.
- On every admin page load, the frontend calls `GET /api/admin/status` to validate the session.
- If the session is expired or invalid, redirect to the login form.
- **Logout**: Button in the sidebar. Calls `POST /api/admin/logout` (clears the cookie). Redirects to login.

### FR-2: Dashboard Layout

#### Sidebar Navigation
- **APP_ADMIN**:
  - Dashboard (prayer overview)
  - Groups
  - Admins
  - Logout
- **GROUP_ADMIN**:
  - My Group (group detail + members)
  - Group Prayers
  - Logout

#### Content Area
- Takes up the remaining width after the sidebar.
- Responsive: On mobile (< 768px), sidebar collapses to a hamburger menu.

### FR-3: Prayer Dashboard (APP_ADMIN)

- **Table Columns**: Prayer Text (truncated to 80 chars), Group Name, Status, Prayed Count, Created Date.
- **Filters** (above the table):
  - Status dropdown: All, Open, Closed.
  - Group dropdown: All, {list of groups}.
  - Date range: From date picker, To date picker.
  - "Apply Filters" button.
- **Sorting**: Click column headers to sort. Default: Created Date descending.
- **Pagination**: 20 items per page. "Previous" / "Next" buttons. "Page X of Y" indicator.
- **API**: `GET /api/admin/prayers?page=0&size=20&status=OPEN&groupId=uuid&fromDate=2026-06-01&toDate=2026-06-30`
  - Response: `{ "items": [...], "totalCount": 150, "page": 0, "size": 20 }`
- **Row click**: Opens a read-only prayer detail modal showing full text, update text (if closed), and metadata.

### FR-4: Group Management (APP_ADMIN)

#### Group List
- **Table Columns**: Group Name, Member Count, Passcode, Opt-Out General, Created Date.
- **Actions** per row (icon buttons or dropdown):
  - **Edit**: Opens edit modal (name, passcode, opt-out toggle).
  - **Delete**: Confirmation modal: "Deleting '{groupName}' will remove all its members. Prayers already assigned to this group will remain. This cannot be undone." Buttons: "Delete Group" (red) and "Cancel".
  - **View Members**: Navigates to member management for this group.
  - **Regenerate Passcode**: Confirmation modal: "This will generate a new passcode. The old one will stop working immediately." On confirm: `POST /api/admin/groups/{groupId}/regenerate-passcode`. Response: `{ "passcode": "ABC123" }`. Update the table row.
  - **Download QR Code**: Generates a QR code client-side (using `qrcode` npm package) that encodes `https://{domain}/group/{groupId}`. Downloads as PNG file named `{groupName}-qr.png`.

#### Create Group
- "Create Group" button above the table.
- Modal form:
  - Group Name (required, max 100 chars).
  - Passcode (auto-generated 6-char alphanumeric, displayed, editable). "Regenerate" button for a new random passcode.
  - Opt-Out General Prayers (checkbox, default unchecked). Label: "Exclude this group from receiving unassigned prayers."
- On submit: `POST /api/admin/groups` with `{ "name": "...", "passcode": "...", "optOutGeneral": false }`.
  - **201 response**: Close modal. Add group to the table. Show success toast.
  - **409 response**: "This passcode is already in use. Please choose a different one."
  - **400 response**: Show validation errors.

#### Edit Group
- Modal form pre-populated with current values.
- On submit: `PUT /api/admin/groups/{groupId}` with changed fields.
  - **200 response**: Update table row. Show success toast.
  - **409 response**: Passcode conflict.

### FR-5: Member Management

#### Member List (within a group)
- **Breadcrumb**: Groups > {Group Name} > Members.
- **Table Columns**: Name, Email, Bounced (🔴/🟢 icon), Added Date.
- **Actions** per row:
  - **Remove**: Confirmation modal: "Remove {name} ({email}) from {groupName}?" On confirm: `DELETE /api/admin/groups/{groupId}/members/{memberId}`. Response: 204. Remove row. Show success toast.

#### Add Single Member
- "Add Member" button above the table.
- Inline form or modal:
  - Name (required, max 100 chars).
  - Email (required, max 254 chars, validated with email regex).
- On submit: `POST /api/admin/groups/{groupId}/members` with `{ "name": "...", "email": "..." }`.
  - **201 response**: Add to table. Show success toast.
  - **400 response**: Validation error (invalid email format, etc.).

#### Bulk Add Members
- "Bulk Add" button next to "Add Member".
- Modal with:
  - Instructions: "Paste CSV data below. Each line should be: Name,Email"
  - Textarea (large, monospace font).
  - Example: "John Smith,john@example.com"
- On submit: Parse CSV client-side. Validate email format for each row.
  - If any rows are invalid: Show a preview table with valid rows (✅) and invalid rows (❌ with reason).
  - "Submit Valid Rows" button (disabled if no valid rows).
- API: `POST /api/admin/groups/{groupId}/members/bulk` with `{ "members": [{ "name": "...", "email": "..." }] }`.
  - Response: `{ "added": 15, "errors": [{ "name": "Bad Name", "email": "not-an-email", "reason": "Invalid email format" }] }`.
  - Show results: "15 members added. 2 failed." with error details.

### FR-6: Admin Management (APP_ADMIN Only)

- **Table Columns**: Username, Role, Group (if GROUP_ADMIN), Created Date.
- **Create Admin**:
  - Modal form: Username, Password, Role dropdown (APP_ADMIN, GROUP_ADMIN), Group dropdown (required if GROUP_ADMIN, hidden if APP_ADMIN).
  - On submit: `POST /api/admin/admins` with `{ "username": "...", "password": "...", "role": "...", "groupId": "..." }`.
  - **201 response**: Add to table. Show success toast.
  - **409 response**: "Username already exists."
- **Delete Admin**:
  - Confirmation modal: "Delete admin '{username}'?"
  - Cannot delete yourself: Button is disabled with tooltip "You cannot delete your own account."
  - Cannot delete the last APP_ADMIN: API returns `400` with `{ "error": "Cannot delete the last app administrator" }`.
  - On confirm: `DELETE /api/admin/admins/{adminId}`. Response: 204.

### FR-7: Group Admin View

GROUP_ADMIN sees a subset of the admin panel:
- **My Group**: Shows their group's details (name, passcode, opt-out status, QR code).
  - Can edit: passcode, opt-out toggle. Cannot edit: group name (requires APP_ADMIN).
  - Can regenerate passcode and download QR.
- **Members**: Shows their group's member list. Can add, remove, and bulk-add members.
- **Group Prayers**: Filtered prayer dashboard showing only prayers assigned to their group. Same table as FR-3 but pre-filtered by groupId and without the group filter dropdown.
- **No access to**: Other groups, admin management, unfiltered prayer list.
- **API enforcement**: All admin-service endpoints check the JWT role claim. GROUP_ADMIN requests for other groups return `403 Forbidden`.

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Admin page load time | < 1 second |
| Table pagination response | < 300ms |
| Concurrent admin users | Up to 10 simultaneous |
| Password hashing | bcrypt, cost factor 12 |
| JWT signing | HMAC-SHA256 |
| Session duration | 24 hours |
| Rate limiting (login) | 5 attempts per IP per minute |

## Data Model

### Admins Table (DynamoDB)
See ADR-002. Key fields:
- `adminId` (PK), `username`, `passwordHash`, `role`, `groupId`, `createdAt`.
- `UsernameIndex` GSI for login lookup.

### Groups Table, GroupMembers Table
See ADR-002. Managed via admin-service → group-service API calls.

## API Contracts

### Authentication

| Method | Path | Body | Response | Notes |
|--------|------|------|----------|-------|
| GET | `/api/admin/status` | — | `{ initialized, authenticated, role, groupId }` | Check session |
| POST | `/api/admin/setup` | `{ username, password }` | 201 + Set-Cookie | First-time only |
| POST | `/api/admin/login` | `{ username, password }` | `{ role, groupId }` + Set-Cookie | Login |
| POST | `/api/admin/logout` | — | 204 + Clear-Cookie | Logout |

### Prayers

| Method | Path | Query Params | Response | Notes |
|--------|------|-------------|----------|-------|
| GET | `/api/admin/prayers` | page, size, status, groupId, fromDate, toDate | `{ items, totalCount, page, size }` | Paginated list |

### Groups

| Method | Path | Body | Response | Notes |
|--------|------|------|----------|-------|
| GET | `/api/admin/groups` | — | `[{ groupId, name, memberCount, passcode, optOutGeneral, createdAt }]` | List all |
| POST | `/api/admin/groups` | `{ name, passcode?, optOutGeneral? }` | `{ groupId, name, passcode, ... }` | Create |
| PUT | `/api/admin/groups/{groupId}` | `{ name?, passcode?, optOutGeneral? }` | 200 | Update |
| DELETE | `/api/admin/groups/{groupId}` | — | 204 | Delete (cascades members) |
| POST | `/api/admin/groups/{groupId}/regenerate-passcode` | — | `{ passcode }` | New passcode |

### Members

| Method | Path | Body | Response | Notes |
|--------|------|------|----------|-------|
| GET | `/api/admin/groups/{groupId}/members` | — | `[{ memberId, name, email, bounced, addedAt }]` | List |
| POST | `/api/admin/groups/{groupId}/members` | `{ name, email }` | `{ memberId, ... }` | Add one |
| POST | `/api/admin/groups/{groupId}/members/bulk` | `{ members: [{ name, email }] }` | `{ added, errors }` | Bulk add |
| DELETE | `/api/admin/groups/{groupId}/members/{memberId}` | — | 204 | Remove |

### Admins

| Method | Path | Body | Response | Notes |
|--------|------|------|----------|-------|
| GET | `/api/admin/admins` | — | `[{ adminId, username, role, groupId, createdAt }]` | APP_ADMIN only |
| POST | `/api/admin/admins` | `{ username, password, role, groupId? }` | `{ adminId, ... }` | Create |
| DELETE | `/api/admin/admins/{adminId}` | — | 204 | Cannot self-delete |

## UI/UX Requirements

- **Design**: Clean, functional. Standard admin dashboard aesthetic. Does NOT need the ethereal animated background of the main site. Use a white/light grey background with the accent colour for interactive elements.
- **Sidebar**: Fixed-width (250px desktop), dark background (#1a1a2e), white text. Active item highlighted with accent colour.
- **Tables**: Alternating row colours for readability. Sticky header. Sortable columns (click to sort, indicator arrow).
- **Modals**: Centred overlay with darkened backdrop. Card-style content with clear heading, form fields, and action buttons.
- **Confirmation modals**: Destructive actions use red/amber button. Always include a "Cancel" option.
- **Toast notifications**: Top-right corner. Auto-dismiss 5 seconds. Success (green), Error (red), Info (blue).
- **Loading**: Skeleton loading placeholders for tables during data fetch. Spinner on buttons during API calls.
- **Form validation**: Inline validation. Error messages appear below the input in red. Required fields marked with asterisk (*).
- **Responsive**: Table columns hide on mobile (show Name and key action only). Sidebar collapses to hamburger.
- **QR Code display**: In the group detail view, show the QR code inline (200x200px). "Download as PNG" button below.

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| admin-service | Backend | All admin API endpoints |
| group-service | Backend | Group and member CRUD (called by admin-service) |
| prayer-service | Backend | Prayer listing (called by admin-service) |
| qrcode (npm) | Frontend | Client-side QR code generation |

## Milestones & Acceptance Criteria

### Milestone 1: Authentication
- [ ] First-time setup creates the initial APP_ADMIN.
- [ ] Login form authenticates and sets JWT cookie.
- [ ] Expired sessions redirect to login.
- [ ] Logout clears the session.
- [ ] Rate limiting prevents brute-force login attempts.

### Milestone 2: Group Management
- [ ] APP_ADMIN can create, edit, and delete groups.
- [ ] Passcodes are auto-generated and can be regenerated.
- [ ] Opt-out toggle works and persists.
- [ ] QR codes are generated and downloadable.
- [ ] Delete confirmation prevents accidental deletion.
- [ ] Passcode uniqueness is enforced (409 on conflict).

### Milestone 3: Member Management
- [ ] Admin can add individual members with name and email.
- [ ] Admin can remove members with confirmation.
- [ ] Bulk add via CSV works with validation feedback.
- [ ] Bounced email status is displayed.

### Milestone 4: Prayer Dashboard
- [ ] APP_ADMIN sees all prayers in a paginated table.
- [ ] Filters by status, group, and date range work correctly.
- [ ] Sorting by column headers works.

### Milestone 5: Admin Management
- [ ] APP_ADMIN can create GROUP_ADMIN and APP_ADMIN accounts.
- [ ] APP_ADMIN can delete admins (except themselves and the last APP_ADMIN).
- [ ] Username uniqueness is enforced.

### Milestone 6: Group Admin Restrictions
- [ ] GROUP_ADMIN sees only their group's data.
- [ ] GROUP_ADMIN cannot access other groups, admins, or unfiltered prayers.
- [ ] API returns 403 for unauthorised GROUP_ADMIN requests.

## Open Questions

1. **Admin password reset**: What happens if an admin forgets their password? For MVP, another APP_ADMIN must delete and re-create the account. Consider adding a password reset flow (via email to the admin) in a future phase.
2. **Admin activity logging**: Should admin actions (create/delete group, add/remove member) be logged for audit purposes? **Recommendation**: Not for MVP, but design the admin-service to emit audit events to a future audit log.
3. **Group deletion cascade**: When a group is deleted, should prayers assigned to that group be reassigned? **Recommendation**: No — prayers retain the `assignedGroupId` even if the group is deleted. They simply won't trigger further notifications.
