# ADR-002: Database — Amazon DynamoDB

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link requires persistent storage for prayer requests, prayer updates, prayer groups, group members, device identities, and administrator accounts. The application is deployed on AWS (ECS Fargate) and follows the 12-Factor App methodology. Traffic is variable — it may spike during church events or outreach campaigns — and the initial scale target is medium (< 1,000 intercessors across multiple churches/communities).

Each microservice owns its data domain. Cross-service data access is achieved via REST APIs, not shared database access. The data model is predominantly key-value with well-defined access patterns (e.g., "get all prayers by device ID", "get all members of a group").

## Decision

Use **Amazon DynamoDB** as the primary database for all microservices, with each service owning its own table(s).

### Table Design

#### `Prayers` Table (owned by prayer-service)
| Attribute       | Type   | Description |
|-----------------|--------|-------------|
| `prayerId` (PK) | String (UUID v4) | Unique prayer identifier |
| `deviceId`       | String (UUID v4) | Device that submitted the prayer |
| `prayerText`     | String (max 2000 chars) | The prayer request text |
| `groupId`        | String (UUID v4, nullable) | Target prayer group (null if round-robin assigned) |
| `assignedGroupId`| String (UUID v4) | The group that actually received the prayer (set by round-robin if groupId was null) |
| `status`         | String (enum: `OPEN`, `CLOSED`) | Current prayer status |
| `prayedForCount` | Number (default 0) | Number of intercessors who have prayed |
| `createdAt`      | String (ISO 8601) | Creation timestamp |
| `updatedAt`      | String (ISO 8601) | Last update timestamp |

**GSIs:**
- `DeviceIdIndex`: PK = `deviceId`, SK = `createdAt` (descending). Used for: "Get all prayers by a device, newest first."
- `GroupIdIndex`: PK = `assignedGroupId`, SK = `createdAt`. Used for: "Get all prayers assigned to a group."

#### `PrayerUpdates` Table (owned by prayer-service)
| Attribute          | Type   | Description |
|--------------------|--------|-------------|
| `prayerId` (PK)    | String (UUID v4) | Prayer this update belongs to |
| `updatedAt` (SK)   | String (ISO 8601) | Timestamp of the update |
| `updateText`        | String (max 1000 chars) | The update text |
| `updatedByDeviceId` | String (UUID v4) | Device that submitted the update |

#### `Groups` Table (owned by group-service)
| Attribute        | Type   | Description |
|------------------|--------|-------------|
| `groupId` (PK)   | String (UUID v4) | Unique group identifier |
| `name`            | String (max 100 chars) | Group display name |
| `passcode`        | String (6 chars, alphanumeric) | Group passcode for direct prayer submission |
| `optOutGeneral`   | Boolean (default false) | If true, group does not receive round-robin unassigned prayers |
| `createdAt`       | String (ISO 8601) | Creation timestamp |

**GSIs:**
- `PasscodeIndex`: PK = `passcode`. Used for: "Validate a passcode and retrieve the group." Passcodes must be unique.

#### `GroupMembers` Table (owned by group-service)
| Attribute        | Type   | Description |
|------------------|--------|-------------|
| `groupId` (PK)   | String (UUID v4) | Group this member belongs to |
| `memberId` (SK)  | String (UUID v4) | Unique member identifier |
| `name`            | String (max 100 chars) | Member's display name |
| `email`           | String (max 254 chars) | Member's email address |
| `bounced`         | Boolean (default false) | Whether emails to this address have bounced |
| `addedAt`         | String (ISO 8601) | When the member was added |

**GSIs:**
- `EmailIndex`: PK = `email`. Used for: "Check if an email exists across any group."

#### `Devices` Table (owned by identity-service)
| Attribute        | Type   | Description |
|------------------|--------|-------------|
| `deviceId` (PK)  | String (UUID v4) | Device identifier |
| `createdAt`       | String (ISO 8601) | First seen timestamp |
| `lastSeenAt`      | String (ISO 8601) | Last activity timestamp |

#### `Admins` Table (owned by admin-service)
| Attribute        | Type   | Description |
|------------------|--------|-------------|
| `adminId` (PK)   | String (UUID v4) | Admin identifier |
| `username`        | String (max 50 chars) | Unique admin username |
| `passwordHash`    | String | bcrypt hash of admin password |
| `role`            | String (enum: `APP_ADMIN`, `GROUP_ADMIN`) | Admin role |
| `groupId`         | String (UUID v4, nullable) | Assigned group (required for GROUP_ADMIN, null for APP_ADMIN) |
| `createdAt`       | String (ISO 8601) | Creation timestamp |

**GSIs:**
- `UsernameIndex`: PK = `username`. Used for: "Look up admin by username for login." Usernames must be unique.

### DynamoDB Configuration
- **Billing mode**: Pay-per-request (on-demand) for all tables. Suits variable traffic without capacity planning.
- **Encryption**: AWS-managed encryption at rest (default).
- **Point-in-time recovery**: Enabled on `Prayers` and `Groups` tables.
- **TTL**: Not enabled initially. Consider for expired/closed prayers in future (e.g., auto-delete after 1 year).

## Consequences

### Positive
- **Fully managed**: No database server to provision, patch, or scale. Reduces operational burden.
- **AWS-native**: Integrates seamlessly with IAM (ECS task roles), CloudWatch, and CDK.
- **Pay-per-request**: Cost scales with actual usage. Ideal for variable traffic.
- **Single-digit millisecond latency**: Meets performance requirements for real-time prayer badge updates.
- **Auto-scaling**: Handles traffic spikes (e.g., church events) without manual intervention.

### Negative
- **No joins**: Related data must be fetched via multiple API calls or denormalized. For example, fetching a prayer with its group name requires calling both prayer-service and group-service.
- **Access patterns must be designed upfront**: Adding a new query pattern may require a new GSI (max 20 per table). Schema changes require careful migration.
- **Limited query flexibility**: No ad-hoc SQL queries. Complex admin dashboard filters (e.g., "prayers from last week for group X with status OPEN") require careful GSI design or Scan operations.
- **No transactions across tables**: Cross-table operations (e.g., deleting a group and all its members) require application-level consistency. DynamoDB transactions work within a single account/region but are limited to 100 items.
- **Local development**: Requires DynamoDB Local (Docker container) for local development, adding to the Docker Compose setup.

## Alternatives Considered

### PostgreSQL (Amazon RDS or Aurora Serverless)
- **Pros**: Full relational model with joins. SQL querying for ad-hoc analytics. Mature ecosystem with Spring Data JPA. Aurora Serverless offers auto-scaling.
- **Cons**: Requires connection management (connection pooling). VPC-bound, more networking setup. Aurora Serverless has cold-start latency. More expensive at low traffic due to minimum instance costs.
- **Verdict**: Rejected. The data model is key-value oriented with well-known access patterns. The operational overhead of managing a relational database is not justified.

### MongoDB (Amazon DocumentDB or self-hosted)
- **Pros**: Flexible document model. Rich query language. DocumentDB is managed.
- **Cons**: DocumentDB is MongoDB-compatible but not identical (feature gaps). Self-hosted MongoDB requires operational expertise. More expensive than DynamoDB at this scale.
- **Verdict**: Rejected. DynamoDB offers better AWS integration and lower operational burden for key-value workloads.
