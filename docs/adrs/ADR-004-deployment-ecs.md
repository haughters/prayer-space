# ADR-004: Deployment — Amazon ECS with Fargate

## Status

Accepted

## Date

2026-06-26

## Context

Prayer Link consists of 6 backend microservices (api-gateway, prayer-service, group-service, notification-service, identity-service, admin-service) and a static frontend. All services are Java Spring Boot applications that need to be containerised, deployed, scaled, and monitored in AWS.

The deployment strategy must support:
- Running multiple services with independent scaling.
- Environment-specific configuration (dev, staging, production) following 12-Factor principles.
- Health checks and automatic restart of failed services.
- Zero-downtime rolling updates.
- Integration with AWS networking (ALB, VPC) and IAM.

## Decision

Deploy all backend microservices on **Amazon ECS** (Elastic Container Service) with **AWS Fargate** — serverless containers with no EC2 nodes to manage.

### Cluster Configuration
- **ECS Cluster**: Single cluster with **Fargate launch type** (no node groups, no EC2 instances).
- **Service discovery**: AWS Cloud Map with private DNS namespace `prayer-link.local`. Each service registers automatically (e.g., `prayer-service.prayer-link.local`).
- **IAM roles**:
  - **Task execution role**: Grants ECS agent permissions to pull images from ECR and write logs to CloudWatch.
  - **Task role**: Per-service role granting the running container access to AWS services (DynamoDB, SES, Secrets Manager) — replaces the IRSA/OIDC approach used.

### Per-Service ECS Resources
Each microservice gets:
- **Task Definition**: Container spec, Fargate CPU/memory allocation, health checks, environment variables, secrets references, log configuration (awslogs driver).
- **ECS Service**: Desired count, deployment configuration (rolling update), load balancer target group association, Cloud Map service registration.
- **Target Group**: ALB target group (IP target type) for routing traffic to the service's Fargate tasks.
- **Application Auto Scaling**: Target tracking policies on CPU and/or memory utilisation (production only).

| Service              | Desired Count (Dev) | Desired Count (Prod) | Auto Scaling (Prod)              | Fargate CPU | Fargate Memory |
|----------------------|---------------------|----------------------|----------------------------------|-------------|----------------|
| api-gateway          | 1                   | 2                    | min 2, max 4, 70% CPU target    | 1 vCPU      | 2 GB           |
| prayer-service       | 1                   | 2                    | min 2, max 5, 70% CPU target    | 1 vCPU      | 2 GB           |
| group-service        | 1                   | 2                    | min 2, max 3, 70% CPU target    | 1 vCPU      | 2 GB           |
| notification-service | 1                   | 2                    | min 2, max 4, 70% CPU target    | 1 vCPU      | 2 GB           |
| identity-service     | 1                   | 1                    | min 1, max 2, 70% CPU target    | 0.5 vCPU    | 1 GB           |
| admin-service        | 1                   | 1                    | min 1, max 2, 70% CPU target    | 0.5 vCPU    | 1 GB           |

### Load Balancing
- **Application Load Balancer (ALB)** with target groups per service.
- **Path-based routing**: The ALB routes requests to the api-gateway ECS service, which handles internal routing to downstream services via Cloud Map service discovery.
- **TLS termination** at the ALB using an ACM (AWS Certificate Manager) certificate.
- **Single ALB** serves both the frontend (via CloudFront) and the API (via `/api/*` path routing to the gateway).

### Container Images
- **Built with Jib** (Maven plugin): Produces optimised, layered container images without a Dockerfile.
- **Base image**: `eclipse-temurin:21-jre-alpine` (smallest JRE image).
- **Stored in Amazon ECR**: One repository per service (e.g., `prayer-link/prayer-service`).
- **Image tagging**: `{service-name}:{git-sha-short}` for traceability.

### Configuration Management
- **Environment variables**: All configuration via env vars in the task definition (12-Factor compliance). Service discovery URLs, feature flags, and non-sensitive defaults are set directly as environment key-value pairs.
- **Secrets**: Managed via **AWS Secrets Manager** and injected into containers using the native ECS `secrets` field in the task definition container spec. ECS resolves secret ARNs at task launch — no External Secrets Operator or sidecar needed. Secrets include:
  - JWT signing key (for admin authentication)
  - HMAC secret (for intercessor tokens)
  - SES SMTP credentials (if not using SDK with task role)

### Infrastructure as Code
ECS task definitions, services, and supporting infrastructure are managed via **AWS CDK** (TypeScript). Environment-specific configuration (dev, staging, prod) is handled through CDK stack props and context variables — no Kustomize or Helm required.

### Frontend Deployment
- The frontend is NOT deployed to ECS. It is a static Vite build deployed to **S3 + CloudFront**.
- CloudFront routes `/api/*` requests to the ALB (ECS api-gateway).
- CloudFront serves all other requests from the S3 bucket (static frontend assets).

## Consequences

### Positive
- **No cluster or node management**: Fargate is serverless — no EC2 instances to provision, patch, or scale. AWS manages the underlying compute.
- **Pay-per-use pricing**: Billed per vCPU-second and GB-second of running tasks. No idle EC2 nodes burning cost overnight in dev/staging.
- **Simpler operations**: No cluster upgrades, no node group management, no addon lifecycle. Fewer moving parts to monitor and maintain.
- **Native AWS secrets integration**: ECS resolves secrets from Secrets Manager at task launch via the `secrets` field — no External Secrets Operator, no CSI driver, no sidecar required.
- **Built-in service discovery**: AWS Cloud Map provides DNS-based service discovery out of the box, with automatic registration/deregistration as tasks start and stop.
- **Rolling updates**: ECS services support rolling deployments with configurable minimum healthy percent and maximum percent, providing zero-downtime updates.
- **Cost savings**: No AWS control plane fee (~$73/month saved). No EC2 node costs. Pay only for actual task runtime.

### Negative
- **Less ecosystem flexibility**: Alternative orchestrators have a richer ecosystem of third-party tools (Prometheus operator, cert-manager, service meshes). ECS relies more heavily on AWS-native equivalents (CloudWatch, ACM, App Mesh).
- **Vendor lock-in**: ECS is AWS-specific. Migrating to another cloud provider would require rearchitecting the deployment layer (whereas standard manifests are portable).
- **No external templating ecosystem**: Environment management is handled through CDK rather than the external manifest ecosystem. Less community tooling for ECS task definition templating.
- **Cold start latency**: Fargate tasks may experience slightly longer startup times compared to pods on warm EC2 nodes, as Fargate must provision the underlying infrastructure on demand.

## Alternatives Considered

### Amazon EKS (Elastic Kubernetes Service)
- **Pros**: Industry-standard orchestration. Rich ecosystem (Prometheus, Grafana, Helm, Kustomize). Portable across cloud providers. Wide talent pool. IRSA for fine-grained IAM.
- **Cons**: EKS control plane costs $0.10/hr (~$73/month) plus EC2 node costs even at idle. Minimum monthly cost ~$150-200 for dev environment. Steep learning curve. Operational overhead for cluster upgrades, node group management, and addon updates.
- **Verdict**: Rejected. Previously chosen (original ADR-004) but superseded by ECS Fargate due to the cost and operational complexity being disproportionate to the project's current scale. Kubernetes remains a viable option if the project outgrows ECS or requires multi-cloud portability.

### EC2 with Docker Compose
- **Pros**: Simplest deployment model. Lowest cost (single EC2 instance). Familiar to most developers.
- **Cons**: No orchestration — manual restart on failure, no auto-scaling, no rolling updates. Single point of failure. Does not scale beyond a single machine without significant rework.
- **Verdict**: Rejected. Acceptable for local development but not for production. Lacks the reliability and scalability guarantees needed.
