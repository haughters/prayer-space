# ADR-008: Observability Stack — Prometheus + Grafana

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link runs 6 microservices on ECS Fargate. The IDEA.md requires: "Metrics should be published for error and totals, accessible via prometheus instance with grafana querying." The team needs visibility into service health, business metrics, and infrastructure performance.

## Decision

Use **Prometheus** for metrics collection and **Grafana** for dashboarding and alerting, using Amazon Managed Service for Prometheus (AMP) and Amazon Managed Grafana (AMG).

### Metrics Collection

Each Spring Boot service exposes metrics via the `/actuator/prometheus` endpoint using **Micrometer** (included via `micrometer-registry-prometheus` dependency).

#### Metrics to Capture

| Category | Metric | Labels | Source |
|----------|--------|--------|--------|
| **HTTP** | `http_server_requests_seconds_count` | method, uri, status | Spring Boot Actuator |
| **HTTP** | `http_server_requests_seconds_sum` | method, uri, status | Spring Boot Actuator |
| **HTTP** | `http_server_requests_seconds_max` | method, uri, status | Spring Boot Actuator |
| **SES** | `ses_emails_sent_total` | template, result (success/failure) | Custom Micrometer counter |
| **SES** | `ses_emails_bounced_total` | bounce_type | Custom Micrometer counter |
| **DynamoDB** | `dynamodb_operations_total` | table, operation (get/put/query/scan) | Custom Micrometer counter |
| **DynamoDB** | `dynamodb_operation_duration_seconds` | table, operation | Custom Micrometer timer |
| **Business** | `prayers_created_total` | group_id | Custom Micrometer counter |
| **Business** | `prayers_prayed_for_total` | group_id | Custom Micrometer counter |
| **Business** | `prayers_closed_total` | group_id | Custom Micrometer counter |
| **Business** | `devices_registered_total` | — | Custom Micrometer counter |
| **JVM** | `jvm_memory_used_bytes`, `jvm_gc_*`, `jvm_threads_*` | — | Micrometer JVM metrics (auto-configured) |

### Prometheus Deployment
- **Collection**: **AWS Distro for OpenTelemetry (ADOT) sidecar containers** run alongside each ECS task, scraping `/actuator/prometheus` on port 8080 every 15 seconds and forwarding metrics to AMP via remote write.
- **Storage**: AMP provides fully managed storage with 150-day retention. No persistent volumes to manage.
- **Workspace**: A single AMP workspace is provisioned via CDK. ADOT sidecars authenticate via ECS task roles.

### Grafana Deployment
- **Installation**: Amazon Managed Grafana (AMG) workspace provisioned via CDK.
- **Data source**: AMP workspace connected as a Prometheus data source.
- **Access**: AMG workspace URL with AWS IAM Identity Center (SSO) authentication.
- **Dashboards**: Provisioned via Grafana API or Terraform/CDK. Dashboard JSON definitions stored in the infrastructure repository.

### Pre-Built Dashboards

#### 1. Service Health Dashboard
- **Per service** (6 panels, one per service):
  - Request rate (requests/second).
  - Error rate (% of 5xx responses).
  - Latency percentiles (p50, p95, p99).
  - Active pod count.

#### 2. Business Metrics Dashboard
- Prayers created per day (bar chart).
- Prayers prayed-for per day (bar chart).
- Prayers closed per day (bar chart).
- Active devices per day (line chart).
- Top 5 groups by prayer volume (table).

#### 3. Infrastructure Dashboard
- SQS queue depth (`notification-queue` and `notification-dlq`).
- SES email send rate and bounce rate.
- DynamoDB read/write consumed capacity.
- Task CPU and memory usage per service.

### Alerting Rules (Grafana Alerting)

| Alert | Condition | Severity | Notification |
|-------|-----------|----------|--------------|
| High error rate | 5xx rate > 5% for 5 minutes | Critical | Email to admin |
| DLQ accumulation | `notification-dlq` depth > 10 for 10 minutes | Warning | Email to admin |
| SES bounce rate | Bounce rate > 10% over 1 hour | Warning | Email to admin |
| Task restart loop | Task restarts > 3 in 10 minutes | Critical | Email to admin |
| High latency | p99 latency > 5s for 5 minutes | Warning | Email to admin |
| AMP ingestion rate | AMP ingestion rate approaching account limits | Warning | Email to admin |

- **Notification channel**: Email (via SES) to the app administrator email configured in Grafana.

## Consequences

### Positive
- **Full visibility**: Every API call, message, email, and database operation is measured.
- **Business insights**: Prayer volume, engagement (prayed-for counts), and group activity are tracked.
- **Proactive alerting**: Issues are detected before users report them.
- **Fully managed**: No Prometheus/Grafana infrastructure to maintain. AMP and AMG handle availability, scaling, and upgrades.
- **Open source compatible**: Uses standard PromQL and Grafana dashboards despite being managed services.

### Negative
- **AMP/AMG usage-based pricing**: AMP charges per metric sample ingested and stored; AMG charges per active user. Costs scale with metric volume.
- **Vendor lock-in**: AMP and AMG are AWS-specific services. Migration to another cloud would require re-deploying self-hosted Prometheus and Grafana.
- **Custom metrics require code**: Business metrics (prayers created, etc.) must be instrumented with Micrometer counters in each service's code.

## Alternatives Considered

### Amazon CloudWatch
- **Pros**: Fully managed. No infrastructure to deploy. Native AWS integration. Container Insights for ECS.
- **Cons**: Higher cost at scale (per-metric pricing). Custom metrics are expensive. Less flexible querying than PromQL. Dashboard UX is less polished than Grafana.
- **Verdict**: Rejected. Prometheus + Grafana is explicitly required by the IDEA.md and offers more flexibility at lower cost.

### Datadog
- **Pros**: Best-in-class UX. APM, logging, and metrics in one platform. Easy setup.
- **Cons**: Expensive ($15-23/host/month + per-metric costs). Vendor lock-in. Pricing scales with infrastructure, not usage.
- **Verdict**: Rejected. Cost is prohibitive for a community/non-profit project at this scale.

### ELK Stack (Elasticsearch, Logstash, Kibana)
- **Pros**: Excellent for log aggregation and search. Kibana dashboards for logs.
- **Cons**: Focused on logs, not metrics. Heavy resource requirements (Elasticsearch). Complementary to Prometheus, not a replacement.
- **Verdict**: Not applicable as a replacement. Could be added alongside Prometheus for log aggregation in future.
