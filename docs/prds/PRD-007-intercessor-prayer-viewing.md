# PRD-007: Intercessor Prayer Viewing

## Overview

In the initial implementation, when an intercessor received an email, they could only view the single prayer they were requested to pray for, and mark it as prayed via a simple button. This PRD details the requirements for Phase 5A, which evolves the intercessor action page into a group prayer browsing experience. Clicking a link in a prayer request email will now load the specific prayer request *and* dynamically present other active/open prayer requests from the same prayer group, allowing intercessors to pray for multiple needs in one session without logging in.

## Goals & Non-Goals

### Goals
- Allow intercessors to view other active prayer requests in their assigned group directly from the email-triggered landing page.
- Maintain a login-free experience using stateless HMAC security tokens.
- Expand token format/scope to securely carry group context.
- Support inline "I've Prayed" actions for any prayer listed in the group's active list.
- Keep the design clean, responsive, and visually consistent with the main Prayer Link frontend.

### Non-Goals
- Requiring intercessor accounts or passwords in this phase (addressed in Phase 5B).
- Allowing intercessors to view groups they are not members of.
- Allowing intercessors to edit, delete, or create prayers.

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-7.1 | As an intercessor clicking a prayer request link, I want to see the specific prayer I was alerted about prominently featured. | Must Have |
| US-7.2 | As an intercessor, I want to see a list of other active prayer requests in my group below or alongside the main prayer. | Must Have |
| US-7.3 | As an intercessor, I want to be able to mark any of these other prayers as "prayed-for" without leaving the page. | Must Have |
| US-7.4 | As an intercessor, I want duplicate clicks on the same session to be prevented and handled gracefully. | Should Have |
| US-7.5 | As an intercessor, I want the system to accept my old email links during the transition period. | Must Have |

## Functional Requirements

### FR-1: Evolved HMAC Token Scope

To fetch other active prayers for a group without authentication, the token sent in the email must securely identify the group. The token format must evolve to include the `groupId` in its signed payload.

#### Token Structure Evolvement
- **Legacy Format:** `Base64URL(HMAC-SHA256(secretKey, "{prayerId}:{email}:{expiryTimestamp}")) | {expiryTimestamp}`
- **New Format:** `Base64URL(HMAC-SHA256(secretKey, "{prayerId}:{groupId}:{email}:{expiryTimestamp}")) | {groupId} | {expiryTimestamp}`

#### Validation Logic
1. Split the token on the `|` character.
2. If the token split yields 3 parts:
   - Extract `encodedSignature`, `groupId`, and `expiryTimestamp`.
   - Verify `expiryTimestamp` is in the future.
   - Retrieve all member emails for the given `groupId` from `group-service`.
   - Recompute expected signature for each member email: `HMAC-SHA256(secretKey, "{prayerId}:{groupId}:{email}:{expiryTimestamp}")`.
   - If a match is found, validation succeeds.
3. If the token split yields 2 parts (Backwards Compatibility):
   - Fall back to the legacy verification flow (requires fetching the prayer from `prayer-service` first to discover `assignedGroupId`).

### FR-2: Group Prayers Endpoint (prayer-service)

A new API endpoint is required to fetch all open prayers in a group, validated by an intercessor token.

- **Endpoint:** `GET /api/prayers/group/{groupId}`
- **Query Parameter:** `token` (the full intercessor token string)
- **Authorization:** Stateless validation of the token (verifies the signature matches the group and one of the group's member emails). Does *not* require the `X-Device-ID` header.
- **Payload:** Returns an array of active/open prayers assigned to that group.

### FR-3: Evolved Intercessor UI (`pray.html` / `pray.js`)

The intercessor action page is upgraded to display the group context.

#### Page Initialization
1. Parse `prayerId` and `token` from the URL.
2. Decode the token. Extract `groupId` from the middle section of the 3-part token (or retrieve it from the target prayer's metadata if using a legacy 2-part token).
3. Fetch the target prayer details: `GET /api/prayers/{prayerId}`.
4. Render the primary target prayer card at the top/left of the page.
5. Fetch the group's active prayers: `GET /api/prayers/group/{groupId}?token={token}`.
6. Render the list of other active prayers below or adjacent to the primary card.

#### Interactive Elements
- The primary prayer has a large, prominent "I have prayed" button.
- Other active prayers in the list are rendered as smaller cards, each containing:
  - The prayer text.
  - An inline "Pray" button.
  - A badge indicating the current prayer count.
- Clicking any inline "Pray" button triggers a POST request to `/api/prayers/{otherPrayerId}/prayed` with the token.
- On success:
  - Transition the button to a green checkmark state.
  - Increment the local counter badge.
  - Trigger emoji burst animations.

## API Contracts

### GET /api/prayers/group/{groupId}
Retrieve all active prayers in a group, authorized by an intercessor token.

**Request:**
```http
GET /api/prayers/group/dbb47746-86c2-4876-8099-a461c28c895c?token=signature_string%7Cdbb47746-86c2-4876-8099-a461c28c895c%7C1753545600 HTTP/1.1
Host: localhost:8080
```

**Success Response (200 OK):**
```json
[
  {
    "prayerId": "a90df2c0-53ab-41c3-bb66-cf7bf703ea32",
    "prayerText": "Please pray for my mother recovering from surgery.",
    "status": "OPEN",
    "prayedForCount": 4,
    "createdAt": "2026-06-27T18:00:00Z"
  },
  {
    "prayerId": "cc1df8e9-d9f2-4411-bdcc-46bdf70c2941",
    "prayerText": "Strength and peace during job transitions.",
    "status": "OPEN",
    "prayedForCount": 2,
    "createdAt": "2026-06-27T19:30:00Z"
  }
]
```

**Error Responses:**
- `401 Unauthorized`: If the token is invalid or expired.
- `400 Bad Request`: If the token or groupId is malformed.

---

## Data Model Changes

No changes to the DynamoDB tables are required. The state changes are handled purely in memory through the token serialization format.

---

## UI/UX Requirements

- **Layout Structure:**
  - **Desktop:** Split screen layout. Left column (40% width) features the primary prayer card. Right column (60% width) lists "Other prayers in this group" with scrollability.
  - **Mobile:** Single column. Primary prayer card at the top. Followed by a divider and the header "Other prayers in your group", followed by a list of cards.
- **Visual Design:**
  - Glassmorphic card styling matching the main app.
  - Soft typography hierarchy.
  - Inline "Pray" buttons: Clean, secondary pill buttons that transform into checkmarks with micro-animations.

---

## Non-Functional Requirements

- **Performance:**
  - Token validation and group member lookup must complete under 100ms.
  - Gateway latency overhead for the new endpoint must be less than 15ms.
- **Security:**
  - The token must continue to be secure against tamper and forgery using HMAC-SHA256.
  - Expiry is strictly enforced.

---

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| `prayer-service` | Backend | Host new `/group/{groupId}` endpoint and updated token verification logic. |
| `notification-service` | Backend | Evolve token generation parameters to include `groupId`. |
| `group-service` | Backend | Fetch members to validate HMAC signatures. |
| `api-gateway` | Gateway | Route requests to the new endpoint without requiring device ID verification. |

---

## Milestones & Acceptance Criteria

### Milestone 1: Token Scope and Verification
- [ ] Token generator in `notification-service` includes `groupId` in signature.
- [ ] Token parser in `prayer-service` supports both 3-part and legacy 2-part tokens.
- [ ] End-to-end unit tests verify signature validation succeeds for group members.

### Milestone 2: Group Prayers Endpoint
- [ ] Endpoint `GET /api/prayers/group/{groupId}` implemented and secured by token validation.
- [ ] Returns only active (`OPEN`) prayers.

### Milestone 3: Evolved Action Page Frontend
- [ ] `pray.js` fetches and renders other group prayers.
- [ ] Intercessor can click and log prayers inline for any item in the list.
- [ ] UI shows appropriate visual feedback (checkmarks, incremented counts).

---

## Open Questions

1. **How should we handle large lists of group prayers?**
   - *Recommendation:* Limit the result size to 50 active prayers. Most groups have fewer than 20 active prayers at any given time. If necessary, introduce cursor-based pagination in a later phase.
2. **What if the target prayer has been closed by the time the user clicks?**
   - *Recommendation:* Display the closed prayer and its update as the primary card, but still display the active group prayers list below so the intercessor can still find other needs to pray for.
