# Architech AI

A platform that builds websites for customers largely through AI agents. The first
milestone is a Requirements Vertical Slice: a customer creates a Website Project, submits
input, and a controlled Requirements Agent turns that input into two validated, versioned
artifacts — a Customer Profile and a set of Website Requirements.

## Repository layout

```
backend/                 Spring Boot platform backend (Java, Maven)
frontend/                React + TypeScript + Vite platform frontend
e2e/                     Playwright end-to-end tests against the real frontend+backend
.claude/skills/           Developer-agent skills (AIW-114) - see docs/developer-agent/
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

Backend tests are split into unit tests (`./mvnw test`, run from `backend/` - no Postgres
needed) and integration tests against real Postgres semantics (`./mvnw verify`, AIW-91) - the
latter spins up its own disposable Postgres via Testcontainers automatically (needs only a
running Docker daemon, not `docker compose up -d`) and applies Flyway migrations to it fresh
every run. `./mvnw verify` also runs the JaCoCo coverage check (AIW-90) across both - the
human-readable report lands at `backend/target/site/jacoco/index.html`, never committed.
Naming convention: `*Tests.java` = unit test (Surefire, `test` phase); `*IT.java` = integration
test (Failsafe, `integration-test`/`verify` phases) - this is how the two stay separately
identifiable in CI output rather than a mixed single run.

If the backend fails to start with a connection error, check that
`docker compose ps` shows Postgres as healthy and that `.env` matches
`docker-compose.yml`.

## End-to-end tests

See [`e2e/README.md`](e2e/README.md) for the Playwright suite that drives the real frontend and
backend together (AIW-92) - conventions, how to run it locally, and why its "happy path"
currently ends in a deterministic validation failure rather than a fabricated success.

## Backend Docker image

`backend/Dockerfile` is a multi-stage build producing a small, non-root runtime image
(AIW-68) - only the built jar ships, never the JDK/Maven build tooling or any secret.

```bash
cd backend
docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t architech-backend:$(git rev-parse --short HEAD) .
docker run --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/architech \
  -e SPRING_DATASOURCE_USERNAME=architech \
  -e SPRING_DATASOURCE_PASSWORD=architech \
  architech-backend:$(git rev-parse --short HEAD)
```

`GIT_SHA` is baked in as an `org.opencontainers.image.revision` label (`docker inspect`) so a
built image's exact source commit is always recoverable, independent of whatever tag it's
later given. The image tag itself (`$(git rev-parse --short HEAD)` above) is a build-time
convention, not something the Dockerfile enforces. CI (`backend-ci.yml`'s `docker-build` job)
builds this same image on every push/PR to prove it stays reproducible - it never pushes
anywhere; publishing to a real registry is AIW-70's concern once one exists.

## Database migrations

Schema changes are made exclusively through new Flyway migration files under
`backend/src/main/resources/db/migration/` — never by editing the database by hand.

## AI provider credentials

See [`docs/operations/secret-management.md`](docs/operations/secret-management.md) for how
Anthropic/OpenAI API keys are supplied in local dev vs. production/staging, and what keeps
them out of code, the frontend bundle, git history, and logs.

## Azure environment architecture

See [`docs/operations/azure-environment-architecture.md`](docs/operations/azure-environment-architecture.md)
for the platform's DEV/STAGING/PROD model, resource group and naming conventions, and the
current one-subscription / future prod-subscription-split plan.

## Developer-agent skills

See [`docs/developer-agent/CONTRACT.md`](docs/developer-agent/CONTRACT.md) for the skill
contract a coding-agent session (Claude Code, today) follows when working ticket-by-ticket in
this repo - not another `core.ai`-driven agent, a formalization of this project's own manual
ticket → branch → implement → verify → human-approval workflow. Skills live under
`.claude/skills/<skill-id>/`; `docs/developer-agent/scripts/check_skill_conformance.py`
validates a skill directory against the contract.

## CI quality and security gate policy

See [`docs/operations/ci-quality-gate-policy.md`](docs/operations/ci-quality-gate-policy.md) for
the layered required-check policy (PR / build-release / STAGING-PROD promotion), coverage and
security-severity rules, and the emergency bypass procedure.
