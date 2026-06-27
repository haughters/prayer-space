# ADR-007: API Gateway Pattern — Spring Cloud Gateway

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link has 5 backend microservices (prayer-service, group-service, notification-service, identity-service, admin-service) that need to be exposed to the frontend. Without a gateway, the frontend would need to know the address of each service individually, and cross-cutting concerns (CORS, rate limiting, logging, header validation) would be duplicated across all services.

## Decision

Use **Spring Cloud Gateway** as the single entry point for all API requests from the frontend.

### Route Configuration

| Route Pattern            | Target Service       | Notes |
|--------------------------|----------------------|-------|
| `/api/prayers/**`        | prayer-service       | Prayer CRUD, prayed-for endpoint |
| `/api/groups/**`         | group-service        | Group lookup, passcode validation |
| `/api/identity/**`       | identity-service     | Device registration, seen updates |
| `/api/admin/**`          | admin-service        | Admin login, group/member/admin management |
| `/api/notifications/**`  | notification-service | Reserved for future direct notification endpoints |

### Cross-Cutting Concerns

#### CORS
- Allowed origins: `https://{domain}` (production), `http://localhost:5173` (local Vite dev server).
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`.
- Allowed headers: `Content-Type`, `X-Device-ID`, `Authorization`.
- Exposed headers: `Set-Cookie`.
- Allow credentials: `true` (for cookie-based admin auth).
- Max age: `3600` seconds.

#### Rate Limiting
- **Implementation**: Spring Cloud Gateway's built-in `RequestRateLimiter` filter.
- **Key resolver**: Uses the `X-Device-ID` header as the rate limit key. Falls back to client IP if header is missing.
- **Limits**:
  - Default: 10 requests/second per device ID.
  - Admin routes (`/api/admin/**`): 30 requests/second per device ID (admin operations involve more API calls).
- **Storage**: In-memory for MVP. Consider Redis for production if multiple gateway replicas share rate limit state.
- **429 response**: `{ "error": "Rate limit exceeded. Please try again later.", "retryAfterSeconds": 1 }`.

#### Request Logging
- Log every request: method, path, `X-Device-ID` (first 8 chars only for privacy), response status, latency.
- Log level: `INFO` for successful requests, `WARN` for 4xx, `ERROR` for 5xx.
- Format: Structured JSON (for CloudWatch Logs Insights compatibility).

#### X-Device-ID Validation
- **Global filter**: Applied to all routes except `/api/admin/**` and `/api/identity/register`.
- **Validation**: Header must be present and match UUID v4 format regex.
- **On failure**: Return `400 Bad Request` with `{ "error": "Missing or invalid X-Device-ID header" }`.
- **Admin routes exception**: Admin routes use JWT cookies for authentication, not device IDs.

#### Health Check
- Gateway exposes `/actuator/health` for ECS health checks and ALB target group health probes.
- Gateway performs health checks on downstream services via Spring Boot Actuator's composite health indicator.

### Service Discovery
- **AWS Cloud Map**: Services discovered via Cloud Map DNS (`{service-name}.prayer-link.local`). ECS service discovery automatically registers/deregisters tasks. No Eureka/Consul needed.
- **Gateway routes** use Cloud Map service names as targets (e.g., `lb://prayer-service`).

### Gateway Configuration (application.yml structure)
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: prayer-service
          uri: http://prayer-service.prayer-link.local:8080
          predicates:
            - Path=/api/prayers/**
          filters:
            - name: RequestRateLimiter
              args:
                rate-limiter: "#{@defaultRateLimiter}"
                key-resolver: "#{@deviceIdKeyResolver}"
        # ... similar for each service
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
```

## Consequences

### Positive
- **Single entry point**: The frontend communicates with one URL. Simplifies CORS, certificate management, and client-side configuration.
- **Centralised cross-cutting concerns**: CORS, rate limiting, logging, and header validation are configured once, not duplicated in every service.
- **Spring ecosystem alignment**: Gateway is a Spring Boot application, consistent with all other services. Same build tooling, same deployment model, same monitoring.
- **AWS Cloud Map service discovery**: No additional service registry infrastructure needed.

### Negative
- **Single point of failure**: If the gateway goes down, the entire API is unreachable. Mitigated by running 2+ tasks with ECS service placement strategies.
- **Added latency**: Every request passes through an extra network hop. Spring Cloud Gateway adds ~1-5ms of overhead, which is negligible.
- **Configuration complexity**: Route configuration must be maintained as services are added or API paths change.

## Alternatives Considered

### AWS API Gateway
- **Pros**: Fully managed. Auto-scaling. Native AWS integration (IAM, WAF, CloudWatch). Pay-per-request pricing.
- **Cons**: Less control over custom filters. WebSocket support is limited. Tight AWS coupling. Configuration is via AWS Console/CloudFormation, not Spring configuration. Harder to test locally.
- **Verdict**: Rejected. Spring Cloud Gateway allows custom Java filters, integrates with the Spring ecosystem, and can be tested locally with the same Docker Compose setup.

### Kong
- **Pros**: Feature-rich. Plugin ecosystem. Database-backed or DB-less mode.
- **Cons**: Written in Lua/OpenResty — different technology from the Java backend. Requires separate learning and operational expertise. Plugin development is in Lua, not Java.
- **Verdict**: Rejected. Introducing a second technology stack (Lua) adds cognitive overhead without clear benefits.

### No Gateway (Direct Service Exposure)
- **Pros**: Simplest architecture. One fewer service to deploy.
- **Cons**: Frontend must know multiple service URLs. CORS must be configured on every service. No centralised rate limiting or logging. ALB would need complex path-based routing rules.
- **Verdict**: Rejected. The cross-cutting concern duplication makes this impractical for 5+ services.
