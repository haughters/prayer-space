# Prayer Link

Prayer Link is a platform connecting people needing prayer with intercessors in their community.

## Repository Structure

- `/frontend`: Vite frontend project using vanilla HTML/CSS/JS.
- `/services`: Java Spring Boot microservices managed with Maven.
- `/infra`: AWS CDK stacks (TypeScript) and Kubernetes manifests.
- `/docs`: Architecture Decision Records (ADRs) and Product Requirements Documents (PRDs).

## Local Development

### Prerequisites
- Docker & Docker Compose
- Java 21 & Maven 3.9+
- Node.js 18+

### Setup
1. Start infrastructure backing services:
   ```bash
   docker-compose up -d
   ```
2. Initialize local database tables:
   ```bash
   ./scripts/create-tables.sh
   ```
3. Run the services via Maven or your IDE.
4. Run the frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
