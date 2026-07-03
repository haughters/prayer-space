# PRD-005: Infrastructure & Deployment (Serverless)

## Overview

This document specifies the infrastructure, CI/CD pipeline, project structure, and local development environment for Prayer Link. To achieve a **$0 baseline cost at 0 traffic**, the system is deployed using a fully serverless architecture on AWS. Backend services are deployed as AWS Lambda functions triggered by Amazon API Gateway, and the frontend is hosted statically via S3 + CloudFront. All infrastructure is defined as code using AWS CDK.

## Goals & Non-Goals

### Goals
- Fully automated deployment pipeline from code push to production.
- All infrastructure defined as code (AWS CDK) and version-controlled.
- **$0 baseline cost** (scale-to-zero) for compute and networking.
- Sub-200ms cold starts for Java microservices via GraalVM native compilation.
- Environment parity between dev, staging, and production.

### Non-Goals
- Multi-region deployment.
- Container-based orchestration (ECS/Kubernetes).
- Provisioned concurrency (unless strict SLAs emerge that justify the cost).

## Functional Requirements

### FR-1: Monorepo Structure

```
prayer-link/
├── frontend/                      # Vite frontend project
│   └── (Static HTML, JS, CSS)
├── services/                      # Java Spring Boot Microservices
│   ├── pom.xml                   # Parent POM (defines GraalVM plugins)
│   ├── shared-lib/               # Shared DTOs, exceptions, utilities
│   ├── prayer-service/
│   ├── group-service/
│   ├── notification-service/
│   ├── identity-service/
│   └── admin-service/
├── infra/
│   └── cdk/                      # AWS CDK (TypeScript)
│       └── lib/
│           ├── database-stack.ts # DynamoDB Tables
│           ├── messaging-stack.ts# EventBridge & SQS
│           ├── compute-stack.ts  # Lambda Functions & IAM Roles
│           ├── api-stack.ts      # API Gateway HTTP APIs
│           ├── ses-stack.ts      # SES Domain & Bounce Handling
│           ├── monitoring-stack.ts# CloudWatch Logs & Metrics
│           └── dns-stack.ts      # CloudFront & S3
├── docs/
│   ├── adrs/
│   └── prds/
├── .github/
│   └── workflows/
│       ├── ci.yml                # PR checks (lint, test)
│       └── deploy.yml            # Build GraalVM native images & deploy
└── README.md
```

*(Note: The `api-gateway` Java microservice is removed; routing is now handled by Amazon API Gateway).*

### FR-2: AWS CDK Stacks

All stacks deployed via `cdk deploy --all`. Each stack is independent with explicit cross-stack references.

| Stack | Resources | Dependencies |
|-------|-----------|-------------|
| `DatabaseStack` | 6 DynamoDB tables with GSIs (On-Demand billing) | None |
| `MessagingStack` | Amazon EventBridge custom bus, SQS queues and routing rules | None |
| `ComputeStack` | AWS Lambda functions, execution roles | DatabaseStack, MessagingStack |
| `ApiStack` | Amazon API Gateway (HTTP APIs), integrations to Lambdas | ComputeStack |
| `SesStack` | SES domain identity, DKIM records, SNS topic for bounces | None |
| `MonitoringStack` | CloudWatch alarms, dashboards | ComputeStack, ApiStack |
| `DnsStack` | Route53 hosted zone, ACM certificate, CloudFront distribution | ApiStack |

*(Note: `NetworkStack` (VPC/NAT) and `EcsStack` (Fargate/ALB) have been completely removed to prevent fixed hourly charges).*

### FR-3: Spring Boot Parent POM & GraalVM

To eliminate the 5–15 second Java cold start, all Spring Boot services are compiled into native executables using GraalVM.

```xml
<!-- Key specifications -->
<java.version>21</java.version>
<spring-boot.version>3.3.x</spring-boot.version>
<aws-serverless-java.version>2.0.x</aws-serverless-java.version>
```

**Build Plugins**:
- `spring-boot-maven-plugin` — Native image build support (`mvn -Pnative native:compile`).
- `maven-surefire-plugin` — Unit tests.
- `spotless-maven-plugin` — Code formatting.

*(Note: Jib and Dockerfile configs are removed. Deployments rely on uploading standard ZIP artifacts containing the GraalVM native binary to Lambda).*

### FR-4: CI/CD Pipeline (GitHub Actions)

#### ci.yml (On Pull Request)
```yaml
trigger: pull_request to main
jobs:
  lint:
    - Spotless check (Java) and ESLint (JS)
  test:
    - Maven test (unit tests per service)
```

#### deploy.yml (On Merge to Main)
```yaml
trigger: push to main
jobs:
  build-native:
    - Set up GraalVM JDK 21.
    - For each service: `mvn -Pnative clean package` to produce the native Linux executable.
    - Zip the native executable as `function.zip`.
  
  deploy-cdk:
    - Install AWS CDK.
    - Pass ZIP artifact paths to CDK as environment variables.
    - `cdk deploy --all --require-approval never`
```

### FR-5: Local Development Environment

Since we are using AWS Lambda, local development shifts from `docker-compose` full-cluster emulation to **local proxying and unit testing**.

1. **Backend Development**: Developers run the specific Spring Boot service they are working on locally via `mvn spring-boot:run`. The service connects to DynamoDB Local and LocalStack (for EventBridge/SQS) running via Docker Compose.
2. **Frontend Development**: `npm run dev` starts Vite. The `vite.config.js` is configured to proxy API requests to either the local running Java service or a deployed dev API Gateway.
3. **Gateway Mocking**: To test routing across multiple services locally, developers can run AWS SAM Local (`sam local start-api`).

### FR-6: 12-Factor Compliance Checklist

| Factor | Implementation |
|--------|---------------|
| I. Codebase | Single monorepo, tracked in Git |
| II. Dependencies | Maven (Java), npm (frontend) — all deps declared |
| III. Config | Environment variables via Lambda configuration |
| IV. Backing Services | DynamoDB, EventBridge, SQS, SES |
| V. Build, Release, Run | GraalVM build → Zip artifact → CDK deploy |
| VI. Processes | Stateless Lambda executions |
| VII. Port Binding | AWS Lambda RequestStreamHandler interface |
| VIII. Concurrency | AWS Lambda native concurrent executions |
| IX. Disposability | Lambda handles fast startup (native) and instant teardown |
| X. Dev/Prod Parity | Same native binaries deployed via CDK |
| XI. Logs | Captured by AWS CloudWatch natively |
| XII. Admin Processes | DynamoDB table creation via CDK |

## Dependencies

| Dependency | Purpose |
|------------|---------|
| GraalVM JDK 21 | AOT compilation for fast Lambda cold starts |
| AWS Account | Serverless compute, databases, queues |
| Domain name | DNS, CloudFront distribution, SES |
| GitHub Actions | CI/CD runners (needs sufficient memory for GraalVM builds) |

## Milestones & Acceptance Criteria

### Milestone 1: Serverless Restructuring
- [ ] Remove `api-gateway` service.
- [ ] Implement AWS Serverless Java Container wrappers on Spring Boot controllers.
- [ ] Configure GraalVM native build profiles in parent POM.
- [ ] Successfully compile a native image locally.

### Milestone 2: Serverless Infrastructure (CDK)
- [ ] Create `ComputeStack` defining Lambdas pointing to ZIP artifacts.
- [ ] Create `ApiStack` mapping HTTP API routes to Lambdas.
- [ ] Remove VPC, NAT Gateway, ALB, and ECS Fargate constructs.

### Milestone 3: CI/CD & Deployment
- [ ] GitHub Actions workflow successfully compiles GraalVM binaries (Linux x86_64 or ARM64).
- [ ] CDK successfully deploys the binaries to AWS Lambda.
- [ ] End-to-end API tests confirm sub-200ms cold start latency.
