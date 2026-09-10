# Architech AI

A platform that builds websites for customers largely through AI agents. The first
milestone is a Requirements Vertical Slice: a customer creates a Website Project, submits
input, and a controlled Requirements Agent turns that input into two validated, versioned
artifacts — a Customer Profile and a set of Website Requirements.

## Repository layout

```
backend/                 Spring Boot platform backend (Java, Maven)
frontend/                React + TypeScript + Vite platform frontend
docker-compose.yml       Local Postgres for development
docs/core/                Frozen, project-type-agnostic platform contracts
                          (Source Context, Runner/Validation, Artifact Persistence, ...)
project-types/website/    Frozen Website Requirements Agent V1 specification
                          (agent config, role, rules, skill modules, JSON Schemas)
```

`docs/core/` and `project-types/` are a frozen handoff package — see the notes inside
`docs/core/REQUIREMENTS_AGENT_V1_FREEZE.md` before changing anything under those paths.
Backend code is split the same way: platform-agnostic logic lives under
`ai.architech.backend.core`, website-specific logic under
`ai.architech.backend.projecttype.website`.

## Getting started

Prerequisites: JDK 21+, Node 20+, Docker.

```bash
cp .env.example .env        # adjust if needed, defaults work out of the box
docker compose up -d        # starts Postgres

cd backend
./mvnw spring-boot:run      # runs Flyway migrations, then starts on :8080
# -> curl http://localhost:8080/actuator/health

cd ../frontend
npm install
npm run dev                 # starts on :5173
```

Backend tests (`./mvnw test`, run from `backend/`) require Postgres to be running via
`docker compose up -d`.

If the backend fails to start with a connection error, check that
`docker compose ps` shows Postgres as healthy and that `.env` matches
`docker-compose.yml`.

## Database migrations

Schema changes are made exclusively through new Flyway migration files under
`backend/src/main/resources/db/migration/` — never by editing the database by hand.

## AI provider credentials

See [`docs/operations/secret-management.md`](docs/operations/secret-management.md) for how
Anthropic/OpenAI API keys are supplied in local dev vs. production/staging, and what keeps
them out of code, the frontend bundle, git history, and logs.
