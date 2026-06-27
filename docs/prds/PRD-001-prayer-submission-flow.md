# PRD-001: Prayer Submission Flow

## Overview

The prayer submission flow is the core user journey of Prayer Link. A user lands on the site, taps a button, optionally selects a prayer group, types their prayer, and submits it. The prayer is saved, an event is published for intercessor notification, and the user sees a confirmation.

## Goals & Non-Goals

### Goals
- Allow any user to submit a prayer request with zero friction (no login, no account).
- Support optional targeting of a specific prayer group via passcode or QR code.
- Provide clear feedback at every step (validation, loading, success, error).
- Ensure submitted prayers are reliably persisted and trigger the notification pipeline.

### Non-Goals
- User accounts or login.
- Rich text formatting in prayer text.
- Image or media attachments to prayers.
- Real-time collaborative editing.

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-1.1 | As a new user, I want to submit a prayer request so that intercessors can pray for me. | Must Have |
| US-1.2 | As a user, I want to optionally direct my prayer to a specific group via a passcode so that my church community receives it. | Must Have |
| US-1.3 | As a user, I want to scan a QR code to select a prayer group so I don't have to type a passcode. | Must Have |
| US-1.4 | As a user, I want confirmation that my prayer was submitted successfully. | Must Have |
| US-1.5 | As a user, I want to submit another prayer request after completing one without refreshing the page. | Should Have |
| US-1.6 | As a user, I want to see a character counter so I know how much text I can enter. | Should Have |

## Functional Requirements

### FR-1: Landing Page Submit Button
- The home page displays a prominent "Submit a Prayer" button, centred on screen.
- The button is always visible, regardless of whether the user is new or returning.
- For returning users, the button appears alongside their floating prayer pills (see PRD-002).
- Clicking the button opens the prayer submission flow as a modal overlay (desktop) or full-screen view (mobile).

### FR-2: Multi-Step Submission Flow

#### Step 1: Group Selection (Optional)
- Prompt: "Would you like to send this to a specific prayer group?"
- Two options: "Yes" and "No, send to the community".
- **If "No"**: Skip to Step 2. `groupId` is set to `null`. The backend will assign the prayer round-robin to an eligible group.
- **If "Yes"**: Show two input methods side by side:

  **Passcode Entry:**
  - Input field with placeholder: "Enter 6-character passcode".
  - Input is 6 characters, alphanumeric, case-insensitive (convert to uppercase before validation).
  - "Verify" button next to the input.
  - On click: Call `GET /api/groups/validate?passcode={CODE}`.
    - **200 response** `{ groupId, groupName }`: Show green checkmark and group name ("Sending to: {groupName}"). Store `groupId`. Enable "Continue" button.
    - **404 response**: Show red error: "Invalid passcode. Please check and try again." Clear input. Do not advance.
    - **Network error**: Show red error: "Unable to connect. Please check your connection and try again."
  - Loading spinner on the "Verify" button during API call. Button is disabled during loading.

  **QR Code Scanner:**
  - "Scan QR Code" button with camera icon.
  - On click: Request camera permission via `navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })`.
    - **Permission granted**: Open camera viewfinder overlay. Use `html5-qrcode` library to scan.
    - **Permission denied**: Show message: "Camera access is needed to scan QR codes. Please enable it in your browser settings, or enter the passcode manually."
  - QR code encodes a URL: `https://{domain}/group/{groupId}`.
  - On successful scan: Extract `groupId` from the URL path. Call `GET /api/groups/{groupId}` to validate.
    - **200 response**: Close camera. Show group name. Store `groupId`. Auto-advance to Step 2.
    - **404 response**: Show error: "This QR code doesn't match a valid prayer group."
  - Close/cancel button on the camera overlay.

  **"Continue" button**: Advances to Step 2. Disabled until a group is validated.

#### Step 2: Prayer Text Input
- Textarea with placeholder: "What would you like prayer for?"
- **Minimum length**: 10 characters. Below minimum: "Submit" button is disabled. Show helper text: "Please enter at least 10 characters."
- **Maximum length**: 2000 characters. Enforced by `maxlength` attribute on the textarea.
- **Character counter**: Bottom-right of the textarea. Format: "{current}/{max}" (e.g., "142/2000"). Counter turns amber at 1800 chars, red at 1950 chars.
- **Back button**: Returns to Step 1. Preserves entered text.
- **"Submit Prayer" button**: Enabled only when text length ≥ 10.

#### Step 3: Confirmation
- On submit button click:
  1. Show loading spinner. Disable the submit button. Set button text to "Submitting...".
  2. Ensure device ID exists (check localStorage/cookie; if missing, call `POST /api/identity/register` first).
  3. Call `POST /api/prayers` with body:
     ```json
     {
       "deviceId": "uuid-v4",
       "prayerText": "The prayer text",
       "groupId": "uuid-v4-or-null"
     }
     ```
  4. **On 201 response**: Show confirmation screen:
     - Heading: "Your prayer has been submitted"
     - Subtext: "Intercessors will be notified and will be praying for you."
     - Two buttons: "Submit Another Prayer" (returns to Step 1, clears form) and "Close" (closes modal, returns to home).
  5. **On 4xx/5xx error**: Show error: "Something went wrong. Please try again." Keep the form populated so the user doesn't lose their text. Re-enable the submit button.
  6. **On network error**: Show error: "Unable to connect. Please check your connection and try again."

### FR-3: Round-Robin Assignment (Backend)
When `groupId` is null in the prayer creation request:
1. prayer-service queries all groups from group-service: `GET /api/groups`.
2. Filter out groups with `optOutGeneral: true`.
3. If no eligible groups remain: Save the prayer with `assignedGroupId: null`. Log a WARNING: "No eligible groups for round-robin assignment. Prayer {prayerId} is unassigned." Publish a `PRAYER_CREATED` event with `assignedGroupId: null` (notification-service will skip sending if no group).
4. If eligible groups exist: Select the next group using a round-robin counter. The counter is stored as an atomic counter in a DynamoDB table (or a simple `RoundRobinState` item with an `AtomicLong` that wraps around).
5. Set `assignedGroupId` on the prayer. Publish the event.

### FR-4: Event Publishing
After successfully saving a prayer to DynamoDB, prayer-service publishes a `PRAYER_CREATED` event to EventBridge:
```json
{
  "eventType": "PRAYER_CREATED",
  "prayerId": "uuid",
  "prayerText": "The prayer text",
  "groupId": "uuid-of-originally-requested-group-or-null",
  "assignedGroupId": "uuid-of-actual-group",
  "deviceId": "uuid",
  "timestamp": "2026-06-26T14:00:00Z"
}
```
Exchange: `prayer-events`. Routing key: `prayer.created`.

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| API response time (P95) | < 500ms for `POST /api/prayers` |
| API response time (P95) | < 200ms for `GET /api/groups/validate` |
| Availability | 99.9% uptime for prayer submission |
| Max concurrent submissions | 100 simultaneous users |
| Prayer text storage | UTF-8, supports emoji and non-Latin scripts |
| Accessibility | WCAG 2.1 AA compliant form elements |

## Data Model

### Prayers Table (DynamoDB)
See ADR-002 for full schema. Key fields for this PRD:
- `prayerId` (PK): UUID v4, generated by prayer-service.
- `deviceId`: From request body.
- `prayerText`: From request body, max 2000 chars.
- `groupId`: From request body (nullable).
- `assignedGroupId`: Set by prayer-service (round-robin or same as `groupId`).
- `status`: Set to `OPEN` on creation.
- `prayedForCount`: Set to `0` on creation.
- `createdAt`: ISO 8601 timestamp, set by prayer-service.
- `updatedAt`: Same as `createdAt` on creation.

## API Contracts

### POST /api/prayers
Create a new prayer request.

**Request:**
```
POST /api/prayers
Content-Type: application/json
X-Device-ID: {deviceId}

{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "prayerText": "Please pray for my family...",
  "groupId": "optional-uuid-or-null"
}
```

**Success Response (201 Created):**
```json
{
  "prayerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "createdAt": "2026-06-26T14:00:00Z"
}
```

**Error Responses:**
| Status | Body | Condition |
|--------|------|-----------|
| 400 | `{ "error": "Prayer text must be between 10 and 2000 characters" }` | Text too short or too long |
| 400 | `{ "error": "Invalid deviceId format" }` | Device ID is not a valid UUID |
| 404 | `{ "error": "Group not found" }` | Provided groupId doesn't exist |
| 500 | `{ "error": "Internal server error" }` | Unexpected failure |

### GET /api/groups/validate
Validate a passcode and return group info.

**Request:**
```
GET /api/groups/validate?passcode=ABC123
```

**Success Response (200 OK):**
```json
{
  "groupId": "550e8400-e29b-41d4-a716-446655440000",
  "groupName": "St Mary's Prayer Group"
}
```

**Error Response:**
| Status | Body | Condition |
|--------|------|-----------|
| 404 | `{ "error": "Invalid passcode" }` | No group matches |

### GET /api/groups/{groupId}
Get group info by ID (for QR code validation).

**Request:**
```
GET /api/groups/550e8400-e29b-41d4-a716-446655440000
```

**Success Response (200 OK):**
```json
{
  "groupId": "550e8400-e29b-41d4-a716-446655440000",
  "groupName": "St Mary's Prayer Group"
}
```

**Error Response:**
| Status | Body | Condition |
|--------|------|-----------|
| 404 | `{ "error": "Group not found" }` | No group with this ID |

## UI/UX Requirements

- **Transitions**: Smooth slide-left animation between steps (300ms ease-in-out). Back button slides right.
- **Mobile-first**: Full-width layout on mobile (< 768px). Centred card (max-width 600px) on desktop.
- **Loading states**: Spinner replaces button text during API calls. All inputs disabled during submission.
- **Error styling**: Red border on invalid inputs. Error message text below the input in red. Error messages fade in (200ms).
- **Textarea**: Auto-grows with content up to a max height of 300px, then scrolls.
- **Step indicator**: Small dots or progress bar showing "Step 1 of 3", "Step 2 of 3", etc.
- **QR camera overlay**: Full-screen on mobile with a square viewfinder frame. Dim area outside the frame. Cancel button at bottom.

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| group-service | Backend | Passcode validation, group lookup |
| identity-service | Backend | Device ID registration |
| EventBridge | Infrastructure | Event publishing |
| DynamoDB | Infrastructure | Prayer storage |
| html5-qrcode | Frontend (npm) | QR code scanning |

## Milestones & Acceptance Criteria

### Milestone 1: Basic Submission (No Group Selection)
- [ ] User can click "Submit a Prayer" and enter prayer text.
- [ ] Character counter updates in real-time.
- [ ] Prayer is saved to DynamoDB with status OPEN.
- [ ] PRAYER_CREATED event is published to EventBridge.
- [ ] Confirmation screen shows after successful submission.
- [ ] "Submit Another" returns to a fresh form.
- [ ] Error states display correctly for network and validation errors.

### Milestone 2: Group Selection via Passcode
- [ ] User can choose to send to a specific group.
- [ ] Passcode input validates against group-service API.
- [ ] Valid passcodes show the group name with a green checkmark.
- [ ] Invalid passcodes show an error message.
- [ ] Prayer is saved with the correct `groupId`.

### Milestone 3: Group Selection via QR Code
- [ ] "Scan QR Code" button opens the device camera.
- [ ] Camera permission denial shows a helpful message.
- [ ] Scanning a valid QR code auto-selects the group and advances.
- [ ] Scanning an invalid QR code shows an error.

### Milestone 4: Round-Robin Assignment
- [ ] Prayers without a groupId are assigned to an eligible group via round-robin.
- [ ] Groups with optOutGeneral=true are excluded.
- [ ] If no eligible groups exist, prayer is saved as unassigned and a warning is logged.

## Open Questions

1. **QR code format**: Should the QR encode just the groupId, or a full URL (`https://{domain}/group/{groupId}`)? Full URL is more user-friendly if scanned outside the app, but the groupId alone is simpler. **Recommendation**: Full URL — scanning with a regular QR scanner takes the user to the site.
2. **Prayer text moderation**: Should there be any content filtering or moderation of prayer text? For MVP, no — trust the community. Consider adding keyword-based flagging for admin review in a future phase.
3. **Offline support**: Should the frontend queue prayers for submission when offline? For MVP, no — show an error and ask the user to retry.
