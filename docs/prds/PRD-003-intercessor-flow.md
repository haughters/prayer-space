# PRD-003: Intercessor Flow

## Overview

Intercessors are notified of prayer requests via email. Each email contains the prayer text and a unique link that opens a simple web page where the intercessor can indicate they have prayed. When a prayer requester provides an update, intercessors receive a follow-up email. This PRD covers the full intercessor journey: receiving emails, marking prayers as prayed-for, and receiving updates.

## Goals & Non-Goals

### Goals
- Deliver prayer requests to intercessors via email reliably.
- Provide a frictionless one-click "I've Prayed" action via a web page link in the email.
- Send follow-up emails when a prayer requester provides an update.
- Handle email bounces and prevent future sends to invalid addresses.
- Ensure idempotent prayer marking (duplicate clicks don't inflate counts).

### Non-Goals
- WhatsApp or SMS notifications (future enhancement — see ADR-006).
- Intercessor accounts or login.
- Intercessor-to-requester direct messaging.
- Intercessor opt-out/unsubscribe (managed by group admin removing them).

## User Stories

| ID | Story | Priority |
|----|-------|----------|
| US-3.1 | As an intercessor, I want to receive prayer requests via email so I can pray for people in need. | Must Have |
| US-3.2 | As an intercessor, I want to click a button in the email to open a page where I can mark that I've prayed. | Must Have |
| US-3.3 | As an intercessor, I want to see confirmation that my prayer has been recorded. | Must Have |
| US-3.4 | As an intercessor, I want to receive an update email when the prayer requester shares news. | Must Have |
| US-3.5 | As an intercessor, I want duplicate clicks to be handled gracefully so I don't worry about clicking twice. | Should Have |
| US-3.6 | As an intercessor, I want to see the prayer text on the action page so I can pray right there. | Should Have |

## Functional Requirements

### FR-1: Prayer Request Email

Triggered when notification-service consumes a `PRAYER_CREATED` event from the `notification-queue`.

#### Email Sending Flow
1. notification-service receives the event containing `assignedGroupId`.
2. If `assignedGroupId` is null, log a warning and skip (no group to notify).
3. Call group-service: `GET /api/groups/{assignedGroupId}/members`.
4. Filter out members with `bounced: true`.
5. If no eligible members remain, log a warning and acknowledge the message.
6. For each eligible member:
   a. Generate an intercessor token (see FR-4).
   b. Construct the action URL: `https://{domain}/pray/{prayerId}/{token}`.
   c. Render the email from template (see Email Template below).
   d. Send via AWS SES: `ses.sendEmail()` with both HTML and plain text bodies.
7. If SES returns a throttling error: Back off and retry (handled by Spring AMQP retry policy).
8. If SES returns a permanent error for a specific recipient: Log the error. Do NOT mark the member as bounced here — bounces come asynchronously via SNS.
9. After all emails are sent (or attempted), acknowledge the SQS message.

#### Email Template: Prayer Request

**Subject:** `Someone Needs Your Prayers`

**From:** `Prayer Link <prayers@{domain}>`

**HTML Body:**
```html
<div style="max-width: 600px; margin: 0 auto; font-family: Inter, sans-serif; color: #1a1a2e;">
  <h2 style="color: #d4a574;">A Prayer Request for Your Group</h2>
  <p>A prayer request has been shared with <strong>{groupName}</strong>.</p>
  <div style="background: #f8f8fc; border-left: 4px solid #d4a574; padding: 16px 20px; margin: 24px 0; border-radius: 0 8px 8px 0;">
    <p style="margin: 0; line-height: 1.6; white-space: pre-wrap;">{prayerText}</p>
  </div>
  <div style="text-align: center; margin: 32px 0;">
    <a href="{actionUrl}" style="display: inline-block; background: #d4a574; color: #1a1a2e; padding: 16px 40px; border-radius: 9999px; text-decoration: none; font-weight: 600; font-size: 16px;">
      I've Prayed For This 🙏
    </a>
  </div>
  <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
  <p style="font-size: 12px; color: #888;">
    You're receiving this because you're a member of {groupName}.<br>
    To unsubscribe, contact your group administrator.
  </p>
</div>
```

**Plain Text Body:**
```
A Prayer Request for Your Group

A prayer request has been shared with {groupName}:

"{prayerText}"

To mark that you've prayed, visit: {actionUrl}

---
You're receiving this because you're a member of {groupName}.
To unsubscribe, contact your group administrator.
```

### FR-2: Intercessor Action Page

**URL:** `https://{domain}/pray/{prayerId}/{intercessorToken}`

This is a frontend page (not a backend endpoint). The page is part of the Vite frontend project, served as `pray.html` or handled via client-side routing.

#### Page Load Flow
1. Extract `prayerId` and `intercessorToken` from the URL path.
2. Call `GET /api/prayers/{prayerId}` to fetch the prayer details.
3. Validate the token locally is not obviously malformed (contains a `|` separator, expiry timestamp is in the future). Full HMAC validation happens server-side on the POST.
4. Render the page based on the prayer state.

#### Page States

**State: OPEN prayer, valid token**
- Display the prayer text in a card.
- Large "I've Prayed For This 🙏" button (full-width on mobile, centred on desktop).
- On button click:
  1. Disable button. Show loading spinner.
  2. Call `POST /api/prayers/{prayerId}/prayed` with body: `{ "intercessorToken": "{token}" }`.
  3. **On 200 response**: Change button to "Thank You for Praying 🙏" with a green checkmark animation. Button remains disabled. Show text: "Your prayer has been recorded. You can close this page."
  4. **On 409 response** (already prayed): Show "You've already recorded your prayer for this request. Thank you! 🙏". No button.
  5. **On 401 response** (invalid token): Show "This link is invalid or has expired."
  6. **On 404 response** (prayer not found): Show "This prayer request could not be found."
  7. **On network error**: Show "Unable to connect. Please try again."

**State: CLOSED prayer**
- Display the prayer text.
- If an update exists: Display "The requester shared this update:" followed by the update text.
- Show "This prayer request has been closed."
- No action button.

**State: Expired token (expiry timestamp in the past)**
- Show "This link has expired. Prayer links are valid for 30 days."
- Still display the prayer text (via API call) if possible.

**State: Invalid token or prayer not found**
- Show "This link is invalid or the prayer could not be found."

### FR-3: Prayer Update Email

Triggered when notification-service consumes a `PRAYER_UPDATED` event.

**Subject:** `Prayer Update: An Answer to Share`

**From:** `Prayer Link <prayers@{domain}>`

**HTML Body:**
```html
<div style="max-width: 600px; margin: 0 auto; font-family: Inter, sans-serif; color: #1a1a2e;">
  <h2 style="color: #d4a574;">A Prayer Update</h2>
  <p>An update has been shared for a prayer request in <strong>{groupName}</strong>.</p>
  <h3>Original Prayer</h3>
  <div style="background: #f8f8fc; border-left: 4px solid #ccc; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0;">
    <p style="margin: 0; line-height: 1.6; white-space: pre-wrap;">{prayerText}</p>
  </div>
  <h3>Update</h3>
  <div style="background: #f0fdf4; border-left: 4px solid #4ade80; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0;">
    <p style="margin: 0; line-height: 1.6; white-space: pre-wrap;">{updateText}</p>
  </div>
  <p><em>This prayer request has been closed.</em></p>
  <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
  <p style="font-size: 12px; color: #888;">
    You're receiving this because you're a member of {groupName}.<br>
    To unsubscribe, contact your group administrator.
  </p>
</div>
```

**No action button** — this email is informational only.

### FR-4: Intercessor Token Generation & Validation

#### Generation (notification-service, at email send time)
```
payload = "{prayerId}:{intercessorEmail}:{expiryTimestamp}"
signature = HMAC-SHA256(secretKey, payload)
token = Base64URL(signature) + "|" + expiryTimestamp
```
- `expiryTimestamp`: Unix timestamp (seconds), 30 days from now.
- `secretKey`: Loaded from AWS Secrets Manager (env var `HMAC_SECRET_KEY`).

#### Validation (prayer-service or notification-service, on POST /prayed)
1. Split token on `|` to get `encodedSignature` and `expiryTimestamp`.
2. Check `expiryTimestamp` > current Unix timestamp. If expired, return 401.
3. Look up which group was assigned to this prayer (`assignedGroupId`).
4. Fetch group members from group-service.
5. For each member email, compute: `expected = HMAC-SHA256(secretKey, "{prayerId}:{email}:{expiryTimestamp}")`.
6. If `Base64URL(expected)` matches `encodedSignature`, the intercessor is identified. Proceed.
7. If no match found across all members, return 401 (invalid token).

**Note**: This approach iterates over group members to find the matching email. For groups with < 100 members (typical), this is performant. For larger groups, consider including the email (encoded) in the token to avoid iteration.

#### Idempotency
- prayer-service maintains a `prayedByEmails` attribute on the Prayers table (String Set type in DynamoDB).
- On valid "prayed" action: Check if the email is already in the set.
  - If yes: Return 409 (already prayed). Do not increment `prayedForCount`.
  - If no: Add email to the set. Atomically increment `prayedForCount` using DynamoDB `UpdateExpression: SET prayedForCount = prayedForCount + :one ADD prayedByEmails :email`.

### FR-5: Bounce Handling

1. SES publishes bounce notifications to an SNS topic (`prayer-link-ses-bounces`).
2. notification-service subscribes to this topic (via SQS queue for reliability).
3. On receiving a bounce notification:
   - Extract the bounced email address.
   - Call group-service: `PUT /api/groups/members/bounce` with `{ "email": "{bouncedEmail}" }`.
   - group-service sets `bounced: true` on all GroupMember records matching that email (using the `EmailIndex` GSI).
4. Future email sends skip members with `bounced: true`.
5. Admin panel shows bounced status per member (see PRD-004).

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Email delivery time | < 30 seconds from prayer creation to email delivery |
| Action page load time | < 2 seconds on 3G connection |
| Action page JS size | < 20KB (minimal — just the API call and UI state changes) |
| Token validation time | < 100ms |
| SES sending rate | Respect SES account limits (default 14 emails/second in production) |

## Data Model

### Additions to Prayers Table
- `prayedByEmails` (String Set): Set of intercessor email addresses that have marked this prayer as prayed-for. Used for idempotency.

### No New Tables
Intercessor tokens are stateless (HMAC-validated). No token storage table needed.

## API Contracts

### POST /api/prayers/{prayerId}/prayed
Record that an intercessor has prayed.

**Request:**
```
POST /api/prayers/7c9e6679-7425-40de-944b-e07fc1f90ae7/prayed
Content-Type: application/json

{
  "intercessorToken": "dGhpc2lzYXRva2Vu...|1753545600"
}
```

**Note:** This endpoint does NOT require an `X-Device-ID` header (intercessors are not device-identified users).

**Success Response (200 OK):**
```json
{
  "message": "Thank you for praying",
  "prayedForCount": 6
}
```

**Error Responses:**
| Status | Body | Condition |
|--------|------|-----------|
| 401 | `{ "error": "Invalid or expired token" }` | HMAC mismatch or token expired |
| 404 | `{ "error": "Prayer not found" }` | Invalid prayerId |
| 409 | `{ "error": "You have already prayed for this request" }` | Email already in prayedByEmails set |

## UI/UX Requirements

### Action Page Design
- **Background**: Same animated gradient background as the main site (ethereal, airy).
- **Card**: Single centred glassmorphic card (max-width 500px) with the prayer text and button.
- **Button**: Full-width on mobile. Soft gold background (#d4a574). Large touch target (min 56px height). Rounded (border-radius: 9999px).
- **Success animation**: On successful prayer recording:
  1. Button background transitions from gold to soft green (#4ade80) over 300ms.
  2. Button text changes to "Thank You for Praying 🙏".
  3. A subtle checkmark icon fades in.
  4. Confetti-like particles (optional) emanate from the button briefly.
- **Error state**: Red-bordered card with error message. "Try Again" button if applicable.
- **Page weight**: Minimal. Load only the essential CSS and a small JS file for the API call. Consider making the page work without JavaScript via a `<form>` POST fallback (progressive enhancement).
- **No navigation chrome**: No header, no footer, no links to the main site. Just the card and background.

### Email Design
- **Responsive**: Single-column layout. Renders correctly in Gmail, Outlook, Apple Mail, Yahoo Mail.
- **Inline styles**: All CSS must be inline (email client compatibility).
- **Image-free**: No images in the email body. Text and styled HTML only. This improves deliverability and avoids image-blocking issues.
- **Button**: Use the bulletproof button technique (VML fallback for Outlook).
- **Dark mode**: Test that the email is readable in dark mode email clients.

## Dependencies

| Dependency | Service | Purpose |
|------------|---------|---------|
| prayer-service | Backend | Prayer retrieval, prayed-for recording |
| group-service | Backend | Group member lookup, bounce marking |
| notification-service | Backend | Email sending, token generation |
| AWS SES | Infrastructure | Email delivery |
| AWS SNS | Infrastructure | Bounce notifications |
| SQS | Infrastructure | Event consumption |

## Milestones & Acceptance Criteria

### Milestone 1: Prayer Request Email
- [ ] notification-service consumes PRAYER_CREATED events.
- [ ] Email is sent to all non-bounced members of the assigned group.
- [ ] Email contains the prayer text and a clickable "I've Prayed" link.
- [ ] Email renders correctly in Gmail, Outlook, and Apple Mail.
- [ ] Email has both HTML and plain text versions.

### Milestone 2: Intercessor Action Page
- [ ] Action page loads from the email link.
- [ ] Prayer text is displayed.
- [ ] "I've Prayed" button works and records the prayer.
- [ ] prayedForCount increments on the prayer.
- [ ] Success state shows thank-you message.
- [ ] Duplicate clicks return 409 and show "already prayed" message.
- [ ] Expired tokens show expiry message.
- [ ] Closed prayers show the update text.

### Milestone 3: Prayer Update Email
- [ ] notification-service consumes PRAYER_UPDATED events.
- [ ] Follow-up email is sent with original prayer text and update text.
- [ ] No action button in the update email.

### Milestone 4: Bounce Handling
- [ ] SES bounces are received via SNS.
- [ ] Bounced email addresses are marked in GroupMembers.
- [ ] Future sends skip bounced addresses.
- [ ] Admin panel shows bounce status.

## Open Questions

1. **Token email privacy**: The current token design requires iterating group members to find the matching email. An alternative is to include the email (Base64URL-encoded) directly in the token URL. This reveals the email in the URL but simplifies validation. **Recommendation**: Include encoded email in the token for simplicity and performance.
2. **Unsubscribe**: CAN-SPAM requires an unsubscribe mechanism. The current plan says "contact your group administrator." Should we add a one-click unsubscribe link? **Recommendation**: Add a `List-Unsubscribe` header pointing to a removal endpoint. Implement in a future phase.
3. **Email sending parallelism**: Should notification-service send emails sequentially or in parallel? **Recommendation**: Use a thread pool (size 5) to send in parallel, respecting SES rate limits.
