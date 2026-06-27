# ADR-005: User Identity — Best-Effort Device Fingerprinting

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link deliberately avoids user accounts and login flows. The IDEA.md specifies: "The frontend should never ask for a login, instead device fingerprinting used to identify returning users." This means returning users should see their previous prayers without creating an account, but there is no strict identity guarantee — losing access to the device (or clearing browser data) is acceptable data loss for the MVP.

The system needs a way to:
- Associate prayers with a specific device/browser.
- Retrieve a user's prayers on subsequent visits.
- Identify the same device across page reloads and browser sessions.

## Decision

Use **best-effort device identification** via a combination of `localStorage` and HTTP-only cookies. A randomly generated UUID v4 serves as the device identifier.

### Implementation Flow

1. **First visit**:
   - Frontend checks `localStorage` for key `prayer-link-device-id`.
   - If not found, checks for an HTTP-only cookie named `pl-device-id` (set by identity-service).
   - If neither exists, generate a new UUID v4 client-side.
   - Call `POST /api/identity/register` with `{ deviceId: "generated-uuid" }`.
   - identity-service stores the device in the `Devices` table and returns a `Set-Cookie: pl-device-id={uuid}; HttpOnly; Secure; SameSite=Lax; Max-Age=31536000` header (1 year expiry).
   - Frontend stores the same UUID in `localStorage` under key `prayer-link-device-id`.

2. **Subsequent visits**:
   - Frontend checks `localStorage` first (faster, no server round-trip).
   - If found, use it. Also call `PUT /api/identity/{deviceId}/seen` to update `lastSeenAt`.
   - If `localStorage` is cleared but cookie exists, the cookie value is sent automatically with API requests. The frontend can recover the device ID from a `GET /api/identity/me` endpoint that reads the cookie.
   - If both are cleared, the user appears as a new user. A new UUID is generated.

3. **API requests**:
   - All API requests from the frontend include the header `X-Device-ID: {uuid}`.
   - The api-gateway validates that this header is present and contains a valid UUID v4 format (regex: `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`).
   - Backend services use this header to scope data access (e.g., prayer-service returns only prayers matching the device ID).

### Security Considerations
- The device ID is **NOT a security credential**. It is an opaque identifier used for data scoping, not authentication.
- A user who guesses another user's device ID (a UUID v4 — 2^122 possibilities) could theoretically access their prayers. This risk is accepted as negligible.
- The device ID is not used for any privileged operations (admin functions have separate authentication).
- The `HttpOnly` cookie prevents JavaScript-based theft (XSS). The `Secure` flag ensures HTTPS-only transmission. `SameSite=Lax` prevents CSRF.

### API Contracts

- `POST /api/identity/register` — Register a new device.
  - Body: `{ "deviceId": "uuid-v4" }`
  - Response: `201 Created` with `Set-Cookie` header.
  - If deviceId already exists: `200 OK` (idempotent).

- `PUT /api/identity/{deviceId}/seen` — Update last seen timestamp.
  - No body. Response: `204 No Content`.

- `GET /api/identity/me` — Get device ID from cookie.
  - Reads `pl-device-id` cookie. Response: `{ "deviceId": "uuid-v4" }` or `404` if no cookie.

## Consequences

### Positive
- **No accounts, no friction**: Users submit prayers immediately without registration. Zero barrier to entry.
- **Simple implementation**: UUID generation + localStorage + cookie. No external identity provider, no OAuth flow, no password management.
- **Privacy-friendly**: No personal data is collected. The device ID is a random UUID with no inherent meaning.
- **Works across browser tabs/windows**: Cookie and localStorage are shared across tabs in the same browser.

### Negative
- **No cross-device sync**: A user's prayers are tied to a single browser on a single device. Switching devices means starting fresh.
- **Data loss on clear**: Clearing cookies and localStorage loses the device ID. The user appears as new, and old prayers are orphaned.
- **Incognito/private browsing**: Each incognito session generates a new device ID. Prayers submitted in incognito are inaccessible in a normal session.
- **No account recovery**: If a user loses their device ID, there is no way to recover their prayer history. This is acceptable for MVP — prayers are ephemeral by nature.

### Future Enhancement: Robust Fingerprinting
When the best-effort approach proves insufficient (e.g., users frequently lose prayer history), consider upgrading to a fingerprinting library like **FingerprintJS**:
- Generates a device fingerprint from browser attributes (canvas, WebGL, fonts, etc.).
- Survives cookie and localStorage clears.
- **Trade-offs**: Privacy/legal implications (GDPR fingerprinting regulations), accuracy varies by browser/OS, adds a third-party dependency.
- **Implementation**: The fingerprint would serve as a secondary lookup key. If the UUID is lost, the fingerprint can attempt to re-associate the device.

## Alternatives Considered

### FingerprintJS from Day 1
- **Pros**: More robust identification. Survives cookie/storage clears. Open-source version available.
- **Cons**: Privacy and legal concerns — browser fingerprinting may require GDPR consent. Accuracy is not 100% (false positives/negatives). Adds complexity and a third-party dependency. Overkill for MVP.
- **Verdict**: Deferred to a future enhancement. The MVP's best-effort approach is sufficient.

### Session-Only Identification (No Persistence)
- **Pros**: Simplest possible approach. No cookies, no localStorage. Each session is independent.
- **Cons**: Users never see their previous prayers. Completely defeats the "returning user" experience described in the IDEA.md.
- **Verdict**: Rejected. The returning user experience (seeing previous prayers with badge counts) is a core feature.

### Optional Email-Based Accounts
- **Pros**: Cross-device sync. Account recovery. Familiar pattern.
- **Cons**: Directly contradicts the IDEA.md requirement of "never ask for a login." Adds friction to prayer submission. Requires email verification, password management, or OAuth integration.
- **Verdict**: Rejected. The IDEA.md is explicit that no login should be required.
