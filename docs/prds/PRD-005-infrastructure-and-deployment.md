# PRD-005: Infrastructure & Deployment

## Overview

This document specifies the infrastructure, CI/CD pipeline, project structure, and local development environment for Prayer Link. The system is deployed on AWS using ECS Fargate for backend services and S3+CloudFront for the frontend, with infrastructure defined as code using AWS CDK.

## Goals & Non-Goals

### Goals
- Fully automated deployment pipeline from code push to production.
- All infrastructure defined as code (AWS CDK) and version-controlled.
- 12-Factor App compliance across all services.
- Reproducible local development environment.
- Environment parity between dev, staging, and production.

### Non-Goals
- Multi-region deployment.
- Blue-green or canary deployments (rolling updates only for MVP).
- Infrastructure cost optimisation beyond spot instances.
- Disaster recovery plan (future phase).

## Functional Requirements

### FR-1: Monorepo Structure

```
prayer-link/
├── frontend/                      # Vite frontend project
│   ├── index.html                 # Home page
│   ├── pray.html                  # Intercessor action page
│   ├── admin.html                 # Admin panel
│   ├── src/
│   │   ├── main.js               # Home page entry
│   │   ├── pray.js               # Intercessor page entry
│   │   ├── admin.js              # Admin panel entry
│   │   ├── shared/               # Shared utilities (API client, device ID, etc.)
│   │   ├── components/           # Reusable UI components (vanilla JS modules)
│   │   └── styles/
│   │       ├── tokens.css        # Design tokens (CSS custom properties)
│   │       ├── reset.css         # CSS reset
│   │       ├── base.css          # Base element styles
│   │       ├── components.css    # Component styles
│   │       ├── animations.css    # Keyframe animations
│   │       ├── layout.css        # Layout utilities
│   │       └── admin.css         # Admin-specific styles
│   ├── public/                   # Static assets
│   ├── package.json
│   └── vite.config.js
├── services/                      # Java microservices
│   ├── pom.xml                   # Parent POM (defines shared deps, plugins)
│   ├── shared-lib/               # Shared DTOs, exceptions, utilities
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/shared/
│   │       ├── dto/              # PrayerDTO, GroupDTO, MemberDTO, etc.
│   │       ├── event/            # PrayerCreatedEvent, PrayerUpdatedEvent, etc.
│   │       ├── exception/        # Custom exceptions
│   │       └── util/             # Common utilities
│   ├── api-gateway/
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/gateway/
│   ├── prayer-service/
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/prayer/
│   ├── group-service/
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/group/
│   ├── notification-service/
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/notification/
│   ├── identity-service/
│   │   ├── pom.xml
│   │   └── src/main/java/com/prayerlink/identity/
│   └── admin-service/
│       ├── pom.xml
│       └── src/main/java/com/prayerlink/admin/
├── infra/
│   ├── cdk/                      # AWS CDK (TypeScript)
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── bin/
│   │   │   └── app.ts            # CDK app entry point
│   │   └── lib/
│   │       ├── network-stack.ts
│   │       ├── database-stack.ts
│   │       ├── messaging-stack.ts
│   │       ├── ecs-stack.ts
│   │       ├── ecr-stack.ts
│   │       ├── ses-stack.ts
│   │       ├── monitoring-stack.ts
│   │       └── dns-stack.ts
│   └── ecs/                       # ECS configuration
│       └── README.md              # Note: ECS task definitions and services are defined in CDK stacks
├── docker-compose.yml             # Local development environment
├── docs/
│   ├── PLAN.md
│   ├── adrs/
│   └── prds/
├── .github/
│   └── workflows/
│       ├── ci.yml                # PR checks (lint, test, build)
│       └── deploy.yml            # Merge-to-main deployment pipeline
├── IDEA.md
├── .gitignore
└── README.md
```

### FR-2: AWS CDK Stacks

All stacks deployed via `cdk deploy --all`. Each stack is independent with explicit cross-stack references.

| Stack | Resources | Dependencies |
|-------|-----------|-------------|
| `NetworkStack` | VPC (2 AZs, public + private subnets), NAT Gateway, Security Groups | None |
| `DatabaseStack` | 6 DynamoDB tables with GSIs (see ADR-002) | None |
| `MessagingStack` | Amazon EventBridge custom bus, SQS queues (notification-queue + DLQ), and routing rules | None |
| `EcrStack` | 7 ECR repositories (1 per service + frontend) | None |
| `EcsStack` | ECS cluster (Fargate), ALB, Cloud Map namespace, ECS services + task definitions | NetworkStack |
| `SesStack` | SES domain identity, DKIM records, SNS topic for bounces | None |
| `MonitoringStack` | AMP workspace, AMG workspace, ADOT task definition sidecars | EcsStack |
| `DnsStack` | Route53 hosted zone, ACM certificate, ALB DNS record, CloudFront distribution | EcsStack, SesStack |

### FR-3: Spring Boot Parent POM

```xml
<!-- Key specifications -->
<java.version>21</java.version>
<spring-boot.version>3.3.x</spring-boot.version>
<spring-cloud.version>2024.0.x</spring-cloud.version>
<aws-sdk.version>2.25.x</aws-sdk.version>
```

**Shared Dependencies** (managed in parent POM):
- `spring-boot-starter-web` — REST controllers
- `spring-boot-starter-actuator` — Health checks, metrics endpoint
- `spring-boot-starter-validation` — Request validation
- `micrometer-registry-prometheus` — Prometheus metrics
- `io.awspring.cloud:spring-cloud-aws-starter-sqs` — SQS integration
- `software.amazon.awssdk:eventbridge` — EventBridge access
- `software.amazon.awssdk:dynamodb-enhanced` — DynamoDB access
- `spring-cloud-starter-gateway` — Gateway only
- `com.google.code.gson:gson` OR `com.fasterxml.jackson` — JSON serialisation
- `org.projectlombok:lombok` — Boilerplate reduction

**Build Plugins**:
- `com.google.cloud.tools:jib-maven-plugin` — Container image builds (no Dockerfile)
  - Base image: `eclipse-temurin:21-jre-alpine`
  - Target: `{aws-account-id}.dkr.ecr.{region}.amazonaws.com/prayer-link/{service-name}:{git-sha}`
- `maven-surefire-plugin` — Unit tests
- `maven-failsafe-plugin` — Integration tests
- `spotless-maven-plugin` — Code formatting (Google Java Style)

### FR-4: CI/CD Pipeline (GitHub Actions)

#### ci.yml (On Pull Request)
```yaml
trigger: pull_request to main
jobs:
  lint:
    - Spotless check (Java formatting)
    - ESLint (frontend JS)
  test:
    - Maven test (unit tests per service, using DynamoDB Local + LocalStack (for EventBridge/SQS))
    - Vite build check (frontend)
  build:
    - Maven package (skip tests) — verify compilation
    - Vite build — verify frontend builds
```

#### deploy.yml (On Merge to Main)
```yaml
trigger: push to main
jobs:
  detect-changes:
    - Determine which services/frontend changed (using dorny/paths-filter action)
    - Output: list of changed services

  build-and-push:
    - For each changed service: Maven build → Jib push to ECR
    - For frontend: Vite build → Upload to S3 → CloudFront invalidation
    - Tag images with git SHA

  deploy-dev:
    - aws ecs update-service --cluster prayer-link-dev --service {service} --force-new-deployment
    - Wait for service stability: aws ecs wait services-stable

  smoke-test:
    - Health check all services: curl /actuator/health
    - Basic API smoke tests (create a test prayer, verify it exists)

  deploy-prod:
    - Manual approval gate (GitHub Environments)
    - aws ecs update-service --cluster prayer-link-prod --service {service} --force-new-deployment
    - Wait for service stability
    - Post-deployment health checks
```

### FR-5: Local Development Environment

#### docker-compose.yml
```yaml
services:
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    ports: ["8000:8000"]
    command: "-jar DynamoDBLocal.jar -sharedDb"

  # Each Java service can be run via IDE or Maven.
  # Docker Compose can optionally include pre-built service containers.
```

- Each service has a `local` Spring profile (`application-local.yml`):
  - DynamoDB endpoint: `http://localhost:8000`
  - AWS endpoint overrides for LocalStack (EventBridge/SQS)
  - SES: Disabled or mocked (log emails to console).
  - HMAC secret: Hardcoded test value.
  - JWT secret: Hardcoded test value.

- **DynamoDB table creation**: A local setup script (`scripts/create-tables.sh`) uses AWS CLI to create all tables in DynamoDB Local.

- **Frontend dev server**: `npm run dev` starts Vite on port 5173. `vite.config.js` proxies `/api/*` to `http://localhost:8080` (api-gateway).

### FR-6: 12-Factor Compliance Checklist

| Factor | Implementation |
|--------|---------------|
| I. Codebase | Single monorepo, tracked in Git |
| II. Dependencies | Maven (Java), npm (frontend) — all deps declared |
| III. Config | Environment variables via ECS task definition env vars + Secrets Manager |
| IV. Backing Services | DynamoDB, EventBridge, SQS, SES — all via env var URLs |
| V. Build, Release, Run | Jib builds → ECR push → ECS deploy |
| VI. Processes | Stateless services (no in-memory session state) |
| VII. Port Binding | Each service binds to PORT (default 8080) |
| VIII. Concurrency | Scale via ECS Service Auto Scaling (target tracking) |
| IX. Disposability | Graceful shutdown (Spring Boot), fast startup |
| X. Dev/Prod Parity | Same images, different env vars |
| XI. Logs | stdout/stderr, collected by CloudWatch Logs (awslogs driver) |
| XII. Admin Processes | DynamoDB table creation via CDK, setup endpoint for first admin |

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Deployment time (dev) | < 10 minutes from merge to running in dev |
| Deployment time (prod) | < 15 minutes (excluding manual approval) |
| Zero-downtime deployments | Rolling updates with ALB health checks and ECS deployment circuit breaker |
| Local env startup | < 2 minutes for `docker-compose up` + service start |
| Image size | < 200MB per service (Alpine-based JRE) |
| Secrets management | AWS Secrets Manager (native ECS integration) |

## Data Model

No additional data model for this PRD. Infrastructure components are defined in the CDK stacks.

## API Contracts

No user-facing APIs in this PRD. Health check endpoints:
- `GET /actuator/health` — Returns `{ "status": "UP" }`. Used by ALB target group health checks.
- `GET /actuator/prometheus` — Returns Prometheus metrics. Scraped by Prometheus.

## Dependencies

| Dependency | Purpose |
|------------|---------|
| AWS Account | All cloud resources |
| GitHub Repository | Source control and CI/CD |
| Domain name | DNS, TLS certificate, email sending |
| AWS CLI, CDK CLI | Deployment tooling |
| Docker | Local development, container builds |

## Milestones & Acceptance Criteria

### Milestone 1: Project Scaffolding
- [ ] Monorepo structure matches the specification.
- [ ] Parent POM compiles with all shared dependencies.
- [ ] shared-lib module contains placeholder DTOs and events.
- [ ] Each service has a Spring Boot application class and health endpoint.
- [ ] Frontend Vite project initialised with multi-page config.
- [ ] `mvn clean verify` succeeds for all services.
- [ ] `npm run build` succeeds for frontend.

### Milestone 2: Infrastructure (CDK)
- [ ] `cdk synth` generates all CloudFormation templates.
- [ ] `cdk deploy --all` creates all AWS resources.
- [ ] DynamoDB tables are created with correct schemas and GSIs.
- [ ] EventBridge bus and SQS queues are created with correct routing rules.
- [ ] ECS cluster is running with Fargate provider.
- [ ] ECR repositories are created.
- [ ] SES domain is verified with DKIM.

### Milestone 3: ECS Services
- [ ] `cdk deploy EcsStack` creates ECS services and task definitions.
- [ ] All tasks reach RUNNING state and pass ALB health checks.
- [ ] ALB routes external traffic to api-gateway.
- [ ] Services can communicate via Cloud Map service discovery.

### Milestone 4: CI/CD Pipeline
- [ ] PR checks run lint, test, and build.
- [ ] Merge to main triggers build and deploy to dev.
- [ ] Smoke tests pass after dev deployment.
- [ ] Manual approval gates prod deployment.
- [ ] Image tags are traceable to git commits.

### Milestone 5: Local Development
- [ ] `docker-compose up` starts DynamoDB Local and LocalStack (for EventBridge/SQS).
- [ ] Services start with `local` profile and connect to local dependencies.
- [ ] Frontend `npm run dev` proxies API calls to local gateway.
- [ ] Table creation script populates DynamoDB Local.

## Open Questions

1. **Domain name**: What domain will Prayer Link use? This affects CDK DNS stack, SES verification, CORS configuration, and email sender address. Must be decided before infrastructure deployment.
2. **AWS region**: Which AWS region? **Recommendation**: `eu-west-2` (London) for UK-based communities, or `us-east-1` for lowest SES latency.
3. **Cost budget**: What's the monthly AWS budget? ECS Fargate costs are usage-based (~$50-150/month for dev).
4. **GitHub vs. other CI**: Is GitHub Actions confirmed, or should we support GitLab CI / AWS CodePipeline?
