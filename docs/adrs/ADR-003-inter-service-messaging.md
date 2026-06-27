# ADR-003: Inter-Service Messaging — Amazon EventBridge & SQS

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link is composed of multiple microservices. Certain operations are inherently asynchronous — notably, when a prayer is created, the notification-service must send emails to all intercessors in the target group. This should not block the prayer creation API response.

Additionally, when a prayer is updated (closed with a user update), intercessors need a follow-up email. And when an intercessor marks a prayer as "prayed for", the prayer count needs to be incremented.

These event-driven flows require a reliable messaging system between services. The system must:
- Guarantee at-least-once delivery of events.
- Support topic-based routing (different consumers for different event types).
- Handle failures gracefully with dead-letter queues.
- Be manageable within an AWS-hosted infrastructure.

## Decision

Use **Amazon EventBridge** as a central event bus and **Amazon SQS** for reliable queuing for consumers.

### Event Bus & Queue Design

#### Event Bus
- **Central Event Bus**: `prayer-link-bus`
- Services publish JSON events to the Event Bus (using the AWS SDK).

#### Queues
- `notification-service` subscribes via an EventBridge Rule that forwards matching events to an SQS queue named `notification-queue`.
- A Dead Letter Queue (DLQ) named `notification-dlq` catches failed message processing.

### Event Format

- Standard EventBridge envelope (`Source`, `DetailType`, `Detail`).
- `Source`: `com.prayerlink.prayer-service`
- `DetailType`: `PrayerCreated`, `PrayerUpdated`, `PrayerPrayed`
- `Detail`: Contains the JSON payload from the original ADR.

Example EventBridge envelope structure:
```json
{
  "Source": "com.prayerlink.prayer-service",
  "DetailType": "PrayerCreated",
  "Detail": {
    "eventType": "PRAYER_CREATED",
    "prayerId": "uuid-string",
    "prayerText": "Prayer text content",
    "groupId": "uuid-string",
    "assignedGroupId": "uuid-string",
    "deviceId": "uuid-string",
    "timestamp": "2026-06-26T14:00:00Z"
  }
}
```

For `PrayerUpdated` events, the `Detail` includes:
```json
{
  "eventType": "PRAYER_UPDATED",
  "updateText": "The update text from the requester"
}
```

### Integration Details
- **Spring Cloud AWS SQS**: Services use `@SqsListener` to consume messages from SQS.
- **Publishing**: `software.amazon.awssdk:eventbridge` SDK is used to put events on the bus.
- **Consumer acknowledgement**: Automatic or manual via `@SqsListener` acknowledgment modes.
- **Retry policy**: Configured via SQS Redrive Policy (e.g., 3 retries) and visibility timeout.

### Configuration
- Event Bus and SQS Queues provisioned via AWS CDK (`messaging-stack.ts`).
- Fully serverless — no instances, no VPC endpoints required (AWS API endpoints), no passwords (IAM task roles used).

## Consequences

### Positive
- **100% serverless**: True pay-per-use (~$1.00/million events, free for low traffic).
- **Security**: Native IAM integration (no passwords).
- **Decoupled services**: Extremely decoupled routing via content filtering.
- **Reliability**: At-least-once delivery with DLQ integration ensures no events are lost.

### Negative
- **Latency**: Slightly higher latency (~500ms).
- **Message ordering**: EventBridge/SQS do not guarantee strict ordering.
- **Event size limits**: Potential event size limits (256KB).

## Alternatives Considered

### RabbitMQ (Amazon MQ)
- **Pros**: Low latency (~20ms), flexible routing, publisher confirms, and built-in management UI.
- **Cons**: Fixed hourly cost regardless of message volume (~$0.13/hr for `mq.m5.large`).
- **Verdict**: Rejected. This was previously chosen but superseded due to idle costs.

### Amazon SQS + SNS
- **Pros**: Fully serverless. No broker to manage. Pay-per-message pricing. Native AWS integration.
- **Cons**: No topic-based routing (requires SNS+SQS combination for fan-out). Less flexible message routing than EventBridge.
- **Verdict**: Rejected in favor of EventBridge's superior content-based routing and centralized bus model.

### Apache Kafka (Amazon MSK)
- **Pros**: High throughput. Event log semantics (replay capability). Strong ordering guarantees per partition.
- **Cons**: Significantly more complex to operate. MSK minimum cost is high. Overkill for low message volume.
- **Verdict**: Rejected. The message volume does not justify Kafka's complexity and cost.

### Synchronous REST Calls
- **Pros**: Simplest architecture. No message broker infrastructure.
- **Cons**: Tight coupling between services. Blocking calls for operations like email sending.
- **Verdict**: Rejected. Prayer creation should not be blocked by email delivery.
