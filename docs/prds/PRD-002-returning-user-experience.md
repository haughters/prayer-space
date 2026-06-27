# PRD-002: Returning User Experience

## Overview

Returning users — those whose device ID is stored in localStorage/cookie — should see their previous prayer requests displayed on the home page alongside the "Submit a Prayer" button. Each prayer shows a badge indicating how many intercessors have prayed for it. Users can tap a prayer to view its details and optionally provide an update that closes the prayer and notifies intercessors.

## Goals & Non-Goals

### Goals
- Show returning users their previous prayers with prayed-for counts.
- Create a visually engaging floating/drifting animation for prayer elements.
- Allow users to view prayer details and submit an update that closes the prayer.
- Ensure a smooth, performant experience on mobile devices (60fps animations).

### Non-Goals
- Real-time WebSocket updates for prayer counts (future enhancement).
- Cross-device prayer synchronisation.
- Prayer search or filtering for regular users.
- Editing or deleting a submitted prayer.

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-2.1 | As a returning user, I want to see my previous prayer requests on the home page so I can track them. | Must Have |
| US-2.2 | As a returning user, I want to see how many people have prayed for each request so I feel supported. | Must Have |
| US-2.3 | As a returning user, I want to tap a prayer to see its full text and details. | Must Have |
| US-2.4 | As a returning user, I want to provide an update on an answered prayer so intercessors know the outcome. | Must Have |
| US-2.5 | As a user, I want to understand that providing an update will close the prayer and notify intercessors. | Must Have |
| US-2.6 | As a returning user, I want to visually distinguish open prayers from closed ones. | Should Have |

## Functional Requirements

### FR-1: Prayer Retrieval on Page Load

1. On page load, the frontend checks for a device ID in localStorage (key: `prayer-link-device-id`).
2. If a device ID exists, call `GET /api/prayers?deviceId={id}`.
3. **Response**: Array of prayers, ordered by `createdAt` descending (newest first):
   ```json
   [
     {
       "prayerId": "uuid",
       "prayerText": "Full prayer text...",
       "status": "OPEN",
       "prayedForCount": 5,
       "createdAt": "2026-06-20T10:00:00Z"
     }
   ]
   ```
4. If no device ID exists, or the API returns an empty array, show only the "Submit a Prayer" button (new user experience).
5. If the API returns an error, show only the submit button. Do not show an error to the user — the absence of prayers is a graceful fallback.

### FR-2: Floating Prayer Display

- Prayers are rendered as **pill-shaped elements** that float/drift around the central "Submit a Prayer" button.
- **Layout algorithm**:
  - Position prayers in a loose orbit around the button.
  - Use CSS `animation` with `@keyframes` for smooth floating motion. Each pill has a unique animation delay and slight variations in movement path to avoid uniformity.
  - On **desktop** (≥ 768px): Prayers orbit the button in a scattered circular layout. The radius scales with the number of prayers (min radius 150px, max 350px).
  - On **mobile** (< 768px): Prayers are arranged in a scrollable horizontal strip below the button, with a gentle vertical bobbing animation. This prevents overlap issues on small screens.
- **Maximum visible prayers**: Show the most recent 20 prayers. If the user has more than 20:
  - Show a "View all prayers" link below the floating prayers.
  - Clicking it opens a simple scrollable list view (no floating animation) with all prayers paginated (20 per page).
- **Prayer pill content**:
  - Truncated prayer text: First 50 characters + "..." if longer.
  - 🙏 emoji badge with the `prayedForCount` number (e.g., "🙏 5"). If count is 0, show "🙏" with no number.
  - Visual styles:
    - **OPEN prayers**: Full opacity. White/60% glassmorphic background. Accent border on hover.
    - **CLOSED prayers**: 50% opacity. Greyed-out background. Italic text. No hover accent.
- **Interaction**:
  - Hover (desktop): Pill scales up slightly (1.05), shadow increases, border highlights.
  - Tap/click: Navigates to the Prayer Detail view.

### FR-3: Prayer Detail View

Opened when a user taps a prayer pill. Displayed as a **modal overlay** (desktop) or **full-screen slide-in from right** (mobile).

#### Content:
- **Full prayer text** (untruncated).
- **Prayed-for count**: "{count} people have prayed for this" (or "No one has prayed for this yet" if 0).
- **Creation date**: Formatted as relative time ("3 days ago") with full date on hover/long-press ("20 June 2026").
- **Status indicator**: "Open" badge (green) or "Closed" badge (grey).

#### If prayer status is OPEN:
- Show an **"Update & Close Prayer"** section below the prayer details:
  - Warning text (amber background, ⚠️ icon): "Providing an update will close this prayer request and notify your intercessors. This action cannot be undone."
  - Textarea for update text:
    - Placeholder: "Share an update about this prayer..."
    - Minimum: 10 characters. Maximum: 1000 characters.
    - Character counter (same style as submission flow).
  - **"Send Update & Close"** button:
    - Disabled until update text ≥ 10 characters.
    - On click: Show a **confirmation dialog**:
      - Title: "Close this prayer?"
      - Body: "This will send your update to the intercessors who prayed for you and permanently close this prayer request."
      - Buttons: "Yes, Close Prayer" (primary) and "Cancel" (secondary).
    - On confirmation:
      1. Show loading spinner. Disable button.
      2. Call `POST /api/prayers/{prayerId}/updates` with body:
         ```json
         {
           "deviceId": "uuid",
           "updateText": "God answered this prayer..."
         }
         ```
      3. **On 200 response**: Close the detail view. Update the prayer pill to show CLOSED state. Show a toast: "Your update has been sent to your intercessors."
      4. **On 409 response** (prayer already closed): Show error: "This prayer has already been closed."
      5. **On 403 response** (device ID mismatch): Show error: "You don't have permission to update this prayer."
      6. **On network/server error**: Show error: "Something went wrong. Please try again."

#### If prayer status is CLOSED:
- Show "This prayer has been closed" with a grey badge.
- If an update was provided: Show the update text under a "Your Update" heading.
- No action buttons.

#### Navigation:
- **Back button** (top-left) or swipe-right (mobile) returns to the home page.
- **Close button** (X) in the top-right corner.

### FR-4: Prayer Update Backend Logic

When `POST /api/prayers/{prayerId}/updates` is received:
1. Validate `deviceId` matches the prayer's `deviceId`. If not, return `403 Forbidden`.
2. Check prayer status. If `CLOSED`, return `409 Conflict` with `{ "error": "Prayer is already closed" }`.
3. Save the update to the `PrayerUpdates` table:
   ```json
   {
     "prayerId": "uuid",
     "updatedAt": "2026-06-26T14:00:00Z",
     "updateText": "The update text",
     "updatedByDeviceId": "uuid"
   }
   ```
4. Update the prayer in the `Prayers` table: Set `status` to `CLOSED`, set `updatedAt` to now.
5. Publish a `PRAYER_UPDATED` event to EventBridge:
   ```json
   {
     "eventType": "PRAYER_UPDATED",
     "prayerId": "uuid",
     "prayerText": "Original prayer text",
     "updateText": "The update text",
     "assignedGroupId": "uuid",
     "deviceId": "uuid",
     "timestamp": "2026-06-26T14:00:00Z"
   }
   ```
   Exchange: `prayer-events`. Routing key: `prayer.updated`.

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Animation frame rate | 60fps on modern mobile devices (iOS Safari, Chrome Android) |
| Prayer list load time | < 300ms for `GET /api/prayers?deviceId=` |
| Prayer detail load time | < 200ms for `GET /api/prayers/{prayerId}` |
| Animation technique | CSS transforms and opacity only (GPU-accelerated). No `top`/`left` animations. |
| Reduced motion | Respect `prefers-reduced-motion`: pause all floating animations, show prayers in a static grid |

## Data Model

See ADR-002 for full schemas. Key tables for this PRD:
- **Prayers**: Queried via `DeviceIdIndex` GSI (PK=deviceId, SK=createdAt desc).
- **PrayerUpdates**: Queried by prayerId to get update history.

## API Contracts

### GET /api/prayers?deviceId={id}
List prayers for a device.

**Request:**
```
GET /api/prayers?deviceId=550e8400-e29b-41d4-a716-446655440000
X-Device-ID: 550e8400-e29b-41d4-a716-446655440000
```

**Success Response (200 OK):**
```json
[
  {
    "prayerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "prayerText": "Please pray for my family during this difficult time...",
    "status": "OPEN",
    "prayedForCount": 5,
    "createdAt": "2026-06-20T10:00:00Z"
  },
  {
    "prayerId": "another-uuid",
    "prayerText": "Pray for healing...",
    "status": "CLOSED",
    "prayedForCount": 12,
    "createdAt": "2026-06-15T08:30:00Z"
  }
]
```

**Note**: Returns empty array `[]` if no prayers exist for this device. Does NOT return 404.

### GET /api/prayers/{prayerId}
Get full prayer details including updates.

**Request:**
```
GET /api/prayers/7c9e6679-7425-40de-944b-e07fc1f90ae7
X-Device-ID: 550e8400-e29b-41d4-a716-446655440000
```

**Success Response (200 OK):**
```json
{
  "prayerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "prayerText": "Please pray for my family during this difficult time...",
  "status": "OPEN",
  "prayedForCount": 5,
  "createdAt": "2026-06-20T10:00:00Z",
  "updates": [
    {
      "updateText": "Things are getting better, thank you!",
      "updatedAt": "2026-06-25T12:00:00Z"
    }
  ]
}
```

**Error Responses:**
| Status | Condition |
|--------|-----------|
| 404 | Prayer not found |

### POST /api/prayers/{prayerId}/updates
Close a prayer with an update.

**Request:**
```
POST /api/prayers/7c9e6679-7425-40de-944b-e07fc1f90ae7/updates
Content-Type: application/json
X-Device-ID: 550e8400-e29b-41d4-a716-446655440000

{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "updateText": "God answered this prayer. My family is doing well now."
}
```

**Success Response (200 OK):**
```json
{
  "message": "Prayer updated and closed successfully"
}
```

**Error Responses:**
| Status | Body | Condition |
|--------|------|-----------|
| 400 | `{ "error": "Update text must be between 10 and 1000 characters" }` | Validation error |
| 403 | `{ "error": "Device ID does not match prayer owner" }` | Wrong device |
| 404 | `{ "error": "Prayer not found" }` | Invalid prayerId |
| 409 | `{ "error": "Prayer is already closed" }` | Prayer status is CLOSED |

## UI/UX Requirements

- **Floating animation**: Pure CSS. Each prayer pill gets a unique animation using `animation-delay` and slight variations in `translateX`/`translateY` keyframes. Animations loop infinitely with `ease-in-out` timing.
- **Prayer pill hover**: `transform: scale(1.05)` with `box-shadow` increase. Transition: 200ms ease.
- **Detail view transition**: Slide-in from right (mobile) — `transform: translateX(100%)` to `translateX(0)` over 300ms. Fade-in overlay (desktop).
- **Confirmation dialog**: Modal with darkened backdrop. Card-style content. Two buttons (primary destructive in amber, secondary in grey).
- **Toast notifications**: Slide in from top-right. Auto-dismiss after 5 seconds. Success: green left border. Error: red left border.
- **Empty state**: When no prayers exist, show only the submit button with subtle animation. No "you have no prayers" message.
- **Relative time formatting**: Use a small utility function — do not add a library (moment.js, date-fns) for this alone. Handle: "just now", "X minutes ago", "X hours ago", "X days ago", "X weeks ago", then full date.

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| prayer-service | Backend | Prayer retrieval, prayer updates |
| identity-service | Backend | Device ID resolution |
| DynamoDB | Infrastructure | Prayer and PrayerUpdate tables |
| EventBridge | Infrastructure | PRAYER_UPDATED event publishing |

## Milestones & Acceptance Criteria

### Milestone 1: Prayer List Display
- [ ] Returning user sees their prayers on the home page.
- [ ] Prayers show truncated text and prayed-for badge count.
- [ ] OPEN and CLOSED prayers are visually distinct.
- [ ] Floating animation runs at 60fps on mobile.
- [ ] `prefers-reduced-motion` disables animations, shows static layout.
- [ ] New users (no device ID) see only the submit button.

### Milestone 2: Prayer Detail View
- [ ] Tapping a prayer opens the detail view with full text.
- [ ] Detail view shows prayed-for count and creation date.
- [ ] Back/close navigation works correctly.

### Milestone 3: Prayer Update & Close
- [ ] User can enter an update on an OPEN prayer.
- [ ] Warning about closing is clearly displayed.
- [ ] Confirmation dialog appears before closing.
- [ ] Successful update closes the prayer and shows a toast.
- [ ] Closed prayers update to the muted visual state.
- [ ] PRAYER_UPDATED event is published to EventBridge.
- [ ] 409 conflict is handled gracefully.

### Milestone 4: Pagination
- [ ] Users with > 20 prayers see "View all prayers" link.
- [ ] All-prayers view shows a paginated list.

## Open Questions

1. **Prayer ordering**: Should OPEN prayers always appear before CLOSED ones, regardless of date? **Recommendation**: Yes — OPEN prayers first, then CLOSED, each sorted by date descending.
2. **Prayer deletion**: Should users be able to delete a prayer entirely? For MVP, no — prayers are permanent. Consider adding soft-delete in a future phase.
3. **Refresh mechanism**: Should the prayer list auto-refresh periodically to show updated prayedForCount? For MVP, no — user must refresh the page. WebSocket updates are a Phase 7 enhancement.
