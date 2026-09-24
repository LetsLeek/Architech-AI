# Sprint 1 — Platform Foundation

Status: implemented and locally verified. Covers Jira epic **Platform Foundation**
(AIW-7), tickets **AIW-15 … AIW-21**. Nothing beyond this scope is included yet —
see "Not included" at the bottom.

## Quick start

Prerequisites: JDK 21+ (repo has been tested on JDK 27), Node 20+, Docker.

```bash
git clone https://github.com/LetsLeek/Architech-AI.git
cd Architech-AI

cp .env.example .env        # defaults work out of the box, no real secrets needed
docker compose up -d        # starts Postgres, exposed on localhost:5432

cd backend
./mvnw spring-boot:run      # runs Flyway migrations, then starts on :8080
```

In a second terminal, confirm the backend is up:

```bash
curl http://localhost:8080/actuator/health
# -> {"status":"UP", ...}
```

In a third terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev                 # starts on :5173 (prints the exact URL)
```

Open the printed URL — it redirects to `/projects`, a placeholder page (real
project creation UI is a later ticket, not part of this sprint).

### Running the backend tests

```bash
docker compose up -d        # Postgres must be running — tests hit a real DB
cd backend
./mvnw test
```

### Verifying the "no Postgres" failure path

```bash
docker compose stop postgres
cd backend
./mvnw spring-boot:run
# -> fails fast with a clear "Connection to localhost:5432 refused" error,
#    instead of hanging silently
docker compose start postgres
```

## What was implemented

| Ticket | What | How |
|---|---|---|
| AIW-15 | Monorepo repository structure | `backend/`, `frontend/`, root `docker-compose.yml`, root `.gitignore`, README rewritten as a monorepo overview. `docs/core/` and `project-types/` (the frozen Requirements Agent V1 spec) are untouched. |
| AIW-16 | Spring Boot backend | Java 21, Maven, Spring Boot 4.1.1. Health endpoint at `/actuator/health`. Package split: `ai.architech.backend.core` (project-type-agnostic platform logic, empty for now) vs. `ai.architech.backend.projecttype.website` (Website-specific logic, empty for now) — keeps Core and Website concerns separated from day one. |
| AIW-17 | React + TypeScript + Vite frontend | `npm create vite@latest -- --template react-ts`, strict TypeScript, `react-router-dom` wired up with placeholder routes (`/projects`, `/projects/:projectId`) so later tickets have somewhere to build real UI. |
| AIW-18 | PostgreSQL connection | `spring-boot-starter-data-jpa` + Postgres driver; datasource URL/user/password read from environment variables with local defaults matching Docker Compose. Missing/unreachable DB fails startup with a clear error (verified above), not a silent hang. |
| AIW-19 | Docker Compose dev environment | Single `postgres` service, named volume for persistence, healthcheck, configurable via `.env`. |
| AIW-20 | Application config & environment handling | `.env.example` documents every variable (DB + a placeholder for a future AI provider API key); `.env` is git-ignored; nothing is hardcoded or committed as a secret. |
| AIW-21 | Database migration framework | Flyway, running automatically on backend startup. `V1__baseline.sql` is intentionally empty (no domain tables yet) — the first real schema arrives once the platform's identity/versioning strategy is decided, together with the `Project` entity in the next sprint. |

All of the above was run and checked locally: backend tests green (with and
without Postgres reachable), `spring-boot:run` + health check, Flyway migration
log, `npm run build` / `npm run dev` / `npm run lint`, and a full
`docker compose up -d` → backend → frontend bootstrap from a clean checkout.

## Not included in this sprint

No domain models (Project, AgentExecution, Artifact, …), no CI/CD pipelines
(separate epic, AIW-22…24), no AI provider integration, no real UI beyond the
route skeleton. These follow in the next sprints.
