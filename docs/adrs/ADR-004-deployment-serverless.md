# ADR-004: Deployment — Serverless with AWS Lambda

## Status

Accepted (Supersedes previous ECS Fargate decision)

## Date

2026-07-03

## Context

Prayer Link consists of multiple backend microservices (prayer-service, group-service, notification-service, identity-service, admin-service) and a static frontend. 
Originally, the deployment strategy specified Amazon ECS with AWS Fargate. However, analysis of the cost structure revealed that Fargate and the required Application Load Balancer (ALB) and NAT Gateways incur a baseline fixed cost of ~$100–$150/month even at 0 traffic.
Given the community nature of Prayer Link, achieving a true "scale-to-zero" ($0 baseline cost) architecture is highly desirable to minimize expenses during idle periods.

## Decision

Deploy all backend microservices using **AWS Lambda** (serverless compute) triggered by **Amazon API Gateway (HTTP APIs)**, and run them outside of a private VPC to eliminate NAT Gateway costs.

### Compute Configuration
- **AWS Lambda**: Replaces ECS Fargate. Lambda functions will run the application logic, scaling automatically per request and scaling to zero when idle.
- **GraalVM Native Images (Spring Native)**: To combat the 5-15 second cold start latency typical of Java Spring Boot on Lambda, all microservices will be compiled ahead-of-time (AOT) to native executables using GraalVM. This reduces cold starts to < 200ms.
- **VPC Placement**: Lambdas will NOT be placed in a private VPC. By running in the standard AWS public space, they avoid the need for costly NAT Gateways while still being able to securely access AWS managed services (DynamoDB, SQS, EventBridge, SES) via IAM role permissions.

### Load Balancing & API Ingress
- **Amazon API Gateway (HTTP APIs)**: Replaces the Application Load Balancer (ALB) and the Spring Cloud Gateway microservice. API Gateway HTTP APIs provide low-cost, pay-per-request ingress with built-in CORS and JWT validation support.
- **Service Discovery**: Cloud Map is no longer required. API Gateway directly maps routes (e.g., `/api/prayers/*`) to the respective Lambda function ARNs.

### IAM and Security
- **Lambda Execution Roles**: Each Lambda function will have a dedicated IAM execution role with precise permissions (e.g., read/write to specific DynamoDB tables, publish to EventBridge).
- AWS SDK clients within the Lambdas will automatically inherit these permissions.

### Container & Packaging
- **Zip / Native Binary Packaging**: Instead of building Docker images with Jib, the CI/CD pipeline will build GraalVM native binaries and package them as standard ZIP files for Lambda deployment, orchestrated seamlessly via AWS CDK.

### Infrastructure as Code
- **AWS CDK (TypeScript)**: Remains the IaC tool of choice. CDK has first-class integration for AWS Lambda, including automatic code packaging and uploading.

## Consequences

### Positive
- **True $0 Baseline Cost**: By eliminating Fargate, ALB, and NAT Gateways, the infrastructure costs drop to exactly $0.00 when there is no traffic.
- **Infinite Scalability**: Lambda scales instantly and seamlessly handles request spikes without configuring target tracking policies.
- **Simplified Networking**: Removing the VPC and NAT Gateways significantly reduces infrastructure complexity and deployment times.
- **Managed Ingress**: API Gateway natively handles CORS, rate limiting, and request routing without managing a custom Spring Cloud Gateway container.

### Negative
- **GraalVM Complexity**: Compiling Spring Boot to GraalVM native binaries requires strict configuration (reflection hints, resource inclusion) and longer CI/CD build times (often 10+ minutes per service).
- **Tooling Shift**: Developers must test using SAM local or local Maven runs instead of simple Docker Compose containers.
- **Stateless Constraints**: Lambda strictly enforces statelessness and limits execution time (maximum 15 minutes), which necessitates asynchronous processing (via SQS) for long-running tasks.

## Alternatives Considered

### Amazon ECS with Fargate
- **Pros**: Easy to run standard Java JARs. No cold start issues once running.
- **Cons**: Flat rate cost of ~$100+/month even at 0 traffic.
- **Verdict**: Rejected due to the goal of a $0 baseline cost.

### AWS App Runner
- **Pros**: Source-to-container deployment. Simpler than ECS.
- **Cons**: Still incurs a base memory fee ($0.007/GB-hour) to keep instances warm. Does not achieve true $0 at 0 traffic.
- **Verdict**: Rejected.
