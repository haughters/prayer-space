# ADR-007: API Gateway Pattern — Amazon API Gateway (HTTP APIs)

## Status

Accepted (Supersedes previous Spring Cloud Gateway decision)

## Date

2026-07-03

## Context

Prayer Link has multiple backend microservices (prayer-service, group-service, notification-service, identity-service, admin-service) that need to be exposed to the frontend. Following the shift to a serverless (AWS Lambda) architecture to achieve a $0 baseline cost, we must also replace the previously proposed Spring Cloud Gateway. A Spring Boot application acting as a gateway would require a constantly running container, violating the scale-to-zero requirement.

## Decision

Use **Amazon API Gateway (HTTP APIs)** as the single entry point for all API requests from the frontend.

### Route Configuration

| Route Pattern            | Target Service (Lambda) | Notes |
|--------------------------|-------------------------|-------|
| `/api/prayers/{proxy+}`  | prayer-service          | Prayer CRUD, prayed-for endpoint |
| `/api/groups/{proxy+}`   | group-service           | Group lookup, passcode validation |
| `/api/identity/{proxy+}` | identity-service        | Device registration, seen updates |
| `/api/admin/{proxy+}`    | admin-service           | Admin login, group/member/admin management |

*Note: The `api-gateway` Java microservice is officially deprecated and removed from the architecture.*

### Cross-Cutting Concerns

#### CORS
- Handled natively by API Gateway HTTP APIs.
- Allowed origins: `https://{domain}` (production), `http://localhost:5173` (local Vite dev server).
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`.
- Allowed headers: `Content-Type`, `X-Device-ID`, `Authorization`.
- Allow credentials: `true` (for cookie-based admin auth).
- Max age: `3600` seconds.

#### Rate Limiting & WAF
- **Implementation**: HTTP APIs support native throttling (rate limiting) at the route or stage level.
- **Limits**: Configured in AWS CDK to prevent burst abuse.
- If advanced filtering (like IP reputation) is needed, the architecture can easily upgrade from HTTP APIs to REST APIs + AWS WAF, though HTTP APIs are preferred for lower cost and lower latency.

#### Request Logging
- Handled natively by API Gateway access logging, pushed to CloudWatch Logs.
- Logs include IP, request path, latency, and status code.

#### Authentication (Admin Routes)
- **Implementation**: JWT authorizers or Lambda custom authorizers attached specifically to the `/api/admin/*` routes in API Gateway.
- Eliminates the need for downstream services to repeatedly parse and validate JWT signatures.

## Consequences

### Positive
- **True $0 Baseline Cost**: API Gateway HTTP APIs are billed strictly per request ($1.00 per million requests after the free tier). Idle cost is $0.
- **Lower Latency**: HTTP APIs are highly optimized and add less latency than a Spring Cloud Gateway hopping through an ALB.
- **Fully Managed**: No JVM to tune, no memory limits to monitor, no Docker images to build for the gateway layer.
- **Native Security**: Built-in CORS handling and JWT authorizers drastically simplify backend code.

### Negative
- **Vendor Lock-in**: Tying routing and authorization logic into AWS API Gateway makes it harder to migrate the application to another cloud provider or bare metal.
- **Local Testing**: Developers must rely on tools like AWS SAM Local or local frontend proxy rules to mock the API Gateway routing locally, rather than running a Java gateway locally.

## Alternatives Considered

### Spring Cloud Gateway (ECS Fargate)
- **Pros**: Cloud agnostic, easy to run locally, pure Java.
- **Cons**: Requires a 24/7 running container (~$15/month bare minimum) and an ALB (~$16/month). Prevents achieving the $0 baseline cost model.
- **Verdict**: Rejected.

### Application Load Balancer (ALB) Direct Routing
- **Pros**: Simple path-based routing direct to Lambdas.
- **Cons**: ALBs carry a fixed hourly cost (~$16/month). They lack native JWT validation and rate limiting without WAF.
- **Verdict**: Rejected.
