# ADR-006: Notification Strategy — AWS SES Email with WhatsApp Roadmap

## Status

Proposed

## Date

2026-06-26

## Context

When a prayer request is submitted, intercessors in the target group must be notified so they can pray. The IDEA.md states: "Ideally, intercessors are messaged through an app like WhatsApp." However, WhatsApp Business API integration requires Meta business verification, approved message templates, and ongoing per-conversation costs. For MVP, a simpler channel is needed.

The notification must include:
- The prayer request text (anonymised — no requester identity).
- A way for the intercessor to indicate they have prayed (a clickable action).
- Follow-up notifications when the requester provides an update.

## Decision

Use **AWS SES (Simple Email Service)** for intercessor email notifications in the MVP. Plan **WhatsApp Business API** integration as a future milestone.

### Notification Flow

1. **prayer-service** saves the prayer to DynamoDB and publishes a `PRAYER_CREATED` event to SQS (topic exchange `prayer-events`, routing key `prayer.created`).
2. **notification-service** consumes the event from `notification-queue`.
3. notification-service calls **group-service** API (`GET /api/groups/{assignedGroupId}/members`) to retrieve the list of intercessors for the target group.
4. notification-service filters out members with `bounced: true`.
5. For each remaining member, notification-service:
   a. Generates an **intercessor token** (see Token section below).
   b. Constructs the action URL: `https://{domain}/pray/{prayerId}/{intercessorToken}`.
   c. Renders the HTML email from a template.
   d. Sends the email via AWS SES.
6. On failure (SES API error), the message is retried per the SQS retry policy (3 retries, exponential backoff). After max retries, the message goes to `notification-dlq`.

### Email Templates

#### Prayer Request Email
- **Subject**: `Someone Needs Your Prayers`
- **From**: `prayers@{domain}` (verified SES sender)
- **HTML Body**:
  ```
  A prayer request has been shared with your group.

  ---
  {prayerText}
  ---

  [I've Prayed For This]  ← Button linking to action URL

  You're receiving this because you're a member of {groupName}.
  To unsubscribe, contact your group administrator.
  ```
- **Plain Text Alternative**: Same content without HTML formatting. Action URL as a plain hyperlink.

#### Prayer Update Email
- **Subject**: `Prayer Update: An Answer to Share`
- **From**: `prayers@{domain}`
- **HTML Body**:
  ```
  An update has been shared for a prayer request in your group.

  Original Prayer:
  ---
  {prayerText}
  ---

  Update:
  ---
  {updateText}
  ---

  This prayer request has been closed.

  You're receiving this because you're a member of {groupName}.
  ```
- **No action button** — this is informational only.

### Intercessor Token

The token authenticates the intercessor's "I've Prayed" action without requiring login.

- **Format**: HMAC-SHA256 signature, Base64URL-encoded.
- **Payload components**: `{prayerId}:{intercessorEmail}:{expiryTimestamp}`
  - `prayerId`: UUID of the prayer.
  - `intercessorEmail`: Email address of the intercessor.
  - `expiryTimestamp`: Unix timestamp, 30 days from email send time.
- **Generation**: `Base64URL(HMAC-SHA256(secret, "{prayerId}:{email}:{expiry}"))` + `|{expiry}` appended in plain text for validation.
- **Full token format**: `{base64url-hmac}|{expiry-timestamp}`
- **Validation**: notification-service (or prayer-service) recomputes the HMAC using the same secret key and compares. No database lookup needed.
- **Secret key**: Stored in AWS Secrets Manager, injected via External Secrets Operator. Rotation: manual, with old keys accepted for a grace period.
- **Expiry**: 30 days. After expiry, the action page shows "This link has expired."

### SES Configuration
- **Region**: Same as ECS cluster (e.g., `eu-west-2`).
- **Sending identity**: Domain-level verification (`{domain}`). Requires DNS records: MX, DKIM (3 CNAME records), SPF (TXT record), DMARC (TXT record).
- **Sending mode**: Production (must request removal from SES sandbox via AWS support).
- **Sending limits**: SES sandbox allows 200 emails/day. Production allows higher limits (request increase based on expected volume).
- **Bounce handling**:
  - Configure SES to publish bounce/complaint notifications to an **SNS topic**.
  - notification-service subscribes to this SNS topic (via HTTPS endpoint or SQS queue).
  - On bounce: Mark the member's `bounced` attribute as `true` in GroupMembers table (via group-service API).
  - On complaint (spam report): Same as bounce — mark and skip.
- **Suppression list**: SES automatically maintains a global suppression list. notification-service should also maintain its own via the `bounced` flag.

## Consequences

### Positive
- **Low cost**: SES pricing is $0.10 per 1,000 emails. At medium scale (< 1,000 intercessors, ~50 prayers/month), monthly email cost is < $5.
- **AWS-native**: Integrates with IAM (IRSA), CloudWatch metrics, and SNS for bounce handling.
- **Reliable delivery**: SES has high deliverability rates when properly configured (DKIM, SPF, DMARC).
- **Stateless tokens**: Intercessor tokens are validated cryptographically, not stored in the database. Scales without additional storage.

### Negative
- **Spam risk**: Prayer emails may be classified as spam by recipient email providers, especially if the domain is new. Mitigation: Proper DNS records, gradual sending ramp-up, monitor SES reputation dashboard.
- **Email is not instant**: Delivery may take seconds to minutes. Not as immediate as WhatsApp/SMS.
- **Limited interactivity**: Email is one-way. The intercessor must click a link and load a web page to mark their prayer. Cannot reply to the email to interact.
- **SES sandbox limitation**: New SES accounts are sandboxed (200 emails/day, verified recipients only). Must request production access before launch.

### WhatsApp Future Roadmap
When WhatsApp integration is prioritised:
1. Register for **WhatsApp Business API** via a Business Solution Provider (BSP) or Meta's Cloud API.
2. Verify the business with Meta.
3. Create **message templates** (required for outbound messages): prayer notification template, prayer update template.
4. Implement WhatsApp webhook endpoint in notification-service for delivery receipts and button interactions.
5. Store intercessor phone numbers in GroupMembers table (new attribute: `phone`).
6. notification-service sends WhatsApp messages via the WhatsApp Cloud API, with the "I've Prayed" button as an interactive button (no web page needed).

## Alternatives Considered

### SMS via AWS SNS or Twilio
- **Pros**: Near-instant delivery. High open rates. Works on any phone.
- **Cons**: Cost: $0.01-0.05 per SMS (10x-50x email cost). No rich formatting — prayer text limited to 160-1600 characters. Requires collecting phone numbers (more personal than email).
- **Verdict**: Rejected for MVP. Cost is significantly higher, and email is sufficient for the initial launch.

### Push Notifications (Web or Mobile)
- **Pros**: Instant delivery. No email/phone needed.
- **Cons**: Requires a service worker (web push) or native app (mobile push). Web push support is inconsistent across browsers. Requires user opt-in. No native app is planned.
- **Verdict**: Rejected. No mobile app is planned, and web push has poor reach and reliability.
