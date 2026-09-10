# Contributing

## Branching

- `develop` is the integration branch. `main` is not used for day-to-day work.
- One branch per Jira ticket, off `develop`: `<TICKET-KEY>-<short-slug>` (e.g. `AIW-25-project-persistence-model`).
- One PR per ticket, targeting `develop`.

## Merge requirements

`develop` has branch protection enabled: GitHub blocks merging a PR until these checks pass (enforced technically, not just by convention). See [`docs/operations/ci-quality-gate-policy.md`](docs/operations/ci-quality-gate-policy.md) for the full policy behind each one - this is just the current list:

- **Backend tests**: full Maven test suite (unit + Testcontainers-backed integration tests) against a real Postgres instance, plus JaCoCo coverage.
- **Frontend build**: `npm ci`, lint, `tsc` type-check + production build.
- **Frontend tests**: Vitest + React Testing Library, plus coverage.
- **Backend Docker image**: builds the production image and vulnerability-scans it (Trivy).
- **SAST (Semgrep)**: static analysis (the CodeQL fallback for this private, user-owned repo - see the policy doc).
- **Dependency scan (Trivy)**: filesystem/dependency vulnerability scan (the native Dependency Review fallback).
- **Secret scan (Gitleaks)**: full git-history secret scan (the native Secret Scanning fallback).
- **Playwright E2E**: end-to-end + accessibility checks against the real running app.

All run on every PR into `develop`/`main`, regardless of which files changed. That's deliberate: a required status check that's skipped by a path filter never reports at all, which GitHub then shows as permanently "Expected" and blocks the merge button forever - a well-known GitHub Actions gotcha.

`enforce_admins` is **on** - an admin merging past a failing required check is not possible for anyone, including in an emergency (see the policy doc's "Emergency bypass procedure" for what to do instead: document the reason and get the same explicit approval a normal merge needs).

## Explicit approval for push/merge

This project uses an explicit, literal approval step for anything that leaves a local branch:

- **"ja push"** before pushing a branch/commits to the remote.
- **"ja merge"** - separately, after CI is green - before merging a PR.

These are distinct: approving a push does not imply approval to merge, and vice versa. This
applies whether a human or an agentic tool (see [`docs/developer-agent/`](docs/developer-agent/))
is doing the work.

## Scope

CI here covers build/test/lint/security gating. There is deliberately no production deployment
pipeline yet — that depends on a real Azure environment existing first (out of scope until
then).
