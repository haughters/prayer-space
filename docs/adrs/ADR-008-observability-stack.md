# ADR-008: Observability Stack — Prometheus + Grafana (Serverless)

## Status

Accepted (Updated for Serverless Architecture)

## Date

2026-07-03

## Context

Prayer Link runs 5 microservices as AWS Lambda functions. The IDEA.md requires: "Metrics should be published for error and totals, accessible via prometheus instance with grafana querying." 
Previously, this was solved via ECS sidecars scraping `/actuator/prometheus` every 15 seconds. However, in a serverless (scale-to-zero) model, Lambdas do not run 24/7, making traditional pull-based scraping impossible. We must transition to a push-based model while preserving the $0 baseline cost at 0 traffic.

## Decision

Use **Amazon Managed Service for Prometheus (AMP)** for metrics collection and **Amazon Managed Grafana (AMG)** for dashboarding. Lambdas will push metrics directly during invocation.

### Metrics Collection

Each Spring Boot Lambda function generates metrics via **Micrometer**. Because Lambdas cannot be scraped, we will use the **AWS Distro for OpenTelemetry (ADOT) Lambda Layer**.

- **Push Mechanism**: The ADOT Lambda layer intercepts metrics and pushes them to AMP via OTLP (OpenTelemetry Protocol) during the function invocation.
- **Cost Efficiency**: AMP charges per metric sample ingested. At 0 traffic, 0 metrics are generated and pushed, keeping the cost at exactly $0.00.

#### Metrics to Capture

| Category | Metric | Labels | Source |
|----------|--------|--------|--------|
| **HTTP** | `http_server_requests_seconds_count` | method, uri, status | API Gateway / Spring Boot Actuator |
| **HTTP** | `http_server_requests_seconds_sum` | method, uri, status | API Gateway / Spring Boot Actuator |
| **AWS**  | `lambda_invocations_total` | function_name | CloudWatch Integration |
| **AWS**  | `lambda_duration_seconds` | function_name | CloudWatch Integration |
| **SES** | `ses_emails_sent_total` | template, result | Custom Micrometer counter |
| **DynamoDB** | `dynamodb_operations_total` | table, operation | Custom Micrometer counter |
| **Business** | `prayers_created_total` | group_id | Custom Micrometer counter |
| **Business** | `prayers_prayed_for_total` | group_id | Custom Micrometer counter |

### Prometheus Deployment (AMP)
- **Workspace**: A single AMP workspace is provisioned via CDK.
- **Authentication**: The ADOT Lambda layer authenticates via the Lambda Execution Role using AWS SigV4.

### Grafana Deployment (AMG)
- **Installation**: Amazon Managed Grafana (AMG) workspace provisioned via CDK.
- **Data source**: AMP workspace connected as a Prometheus data source.
- **Access**: AMG workspace URL with AWS IAM Identity Center (SSO) authentication.

### Pre-Built Dashboards

#### 1. Serverless Health Dashboard
- Request rate (API Gateway invocations/second).
- Cold start duration vs Warm start duration.
- Error rate (% of 5xx responses).

#### 2. Business Metrics Dashboard
- Prayers created per day.
- Prayers prayed-for per day.
- Active devices per day.

### Alerting Rules (Grafana Alerting)

| Alert | Condition | Severity | Notification |
|-------|-----------|----------|--------------|
| High error rate | 5xx rate > 5% for 5 minutes | Critical | Email to admin |
| DLQ accumulation | `notification-dlq` depth > 10 | Warning | Email to admin |
| SES bounce rate | Bounce rate > 10% over 1 hour | Warning | Email to admin |

## Consequences

### Positive
- **True $0 Baseline**: Metrics are only generated and pushed when traffic occurs. No running sidecars or EC2 nodes polling metrics.
- **Meets Requirements**: Delivers the Prometheus + Grafana stack explicitly requested in IDEA.md.
- **Standardized Export**: Adopts OpenTelemetry (ADOT), which is the industry standard for serverless observability.

### Negative
- **Latency Overhead**: Pushing metrics synchronously during the Lambda invocation can add minor latency to the request. (Alternatively, the ADOT layer can use the Lambda Telemetry API to push metrics post-response, but this requires specific extension configurations).
- **Setup Complexity**: Configuring the ADOT layer with Spring Boot Native Images (GraalVM) requires careful dependency management to ensure native compatibility.

## Alternatives Considered

### Amazon CloudWatch Embedded Metric Format (EMF)
- **Pros**: Zero latency overhead (writes metrics async via stdout logs). 100% serverless native.
- **Cons**: Bypasses Prometheus entirely (requires viewing metrics in CloudWatch instead of Grafana/PromQL, or paying for CloudWatch Metric Streams to bridge them to Grafana). Violates the strict requirement in IDEA.md for a "prometheus instance".
- **Verdict**: Rejected. OpenTelemetry + AMP satisfies the product requirements while still achieving the cost goals.
