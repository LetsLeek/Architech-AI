# CI quality and security gate policy (AIW-87)

The layered set of automated checks a change must pass, at each stage from pull request through
production promotion - what's required today, what's planned as M2's CI/CD/security tickets
land, and how bypassing any of it stays auditable rather than silent.

## Layers

Three distinct gates, each with its own required-check set - a check required at one layer is
not automatically required at another:

1. **Pull request (into `develop` or `main`)** - fast, cheap checks that run on every PR.
2. **Build/release (after merge, producing a deployable artifact)** - heavier checks that don't
   need to block every PR iteration, but must pass before an image is eligible to promote.
3. **STAGING/PROD promotion** - checks against a real running environment, not just source/image
   analysis.

## Pull request gate

| Check | Status | Blocking? |
|---|---|---|
| Backend tests (`backend-ci.yml`, AIW-22) | Existing, required | Yes |
| Frontend build (lint + type-check + build, `frontend-ci.yml`, AIW-23) | Existing, required | Yes |
| Backend Docker image build (`backend-ci.yml`'s `docker-build` job, AIW-68) | Existing, required | Yes |
| Frontend unit/component tests (Vitest + RTL, AIW-88) | Planned | Yes, once it lands |
| Backend integration tests against real Postgres (Testcontainers, AIW-91) | Existing, required | Yes |
| SAST/CodeQL (AIW-93) | Planned | Yes for new HIGH/CRITICAL findings (see [Security severity policy](#security-severity-policy)) |
| Dependency vulnerability check (AIW-94) | Planned | Yes for new HIGH/CRITICAL findings |
| Secret scanning / push protection (AIW-95) | Planned | Yes - any detected secret blocks |
| Accessibility checks (AIW-98) | Planned | **Warning-only** initially - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |
| Frontend coverage thresholds (AIW-89) | Existing, required | Yes - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |
| Backend coverage thresholds (AIW-90) | Existing, required | Yes - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |

This deliberately incorporates AIW-22/23/24's existing checks rather than duplicating them - the
`backend-ci.yml`/`frontend-ci.yml` workflows and `develop`'s branch protection required-status-
checks list (`Backend tests`, `Frontend build`, `Backend Docker image`, `Frontend tests`) are the
actual PR gate;
this document records the policy behind that configuration, not a second parallel mechanism.

## Build/release gate

Runs after merge, against the artifact that would actually get promoted - heavier or slower
checks belong here rather than on every PR iteration:

| Check | Status | Blocking? |
|---|---|---|
| Container image vulnerability scan (Trivy, AIW-96) | Planned | Yes for new HIGH/CRITICAL findings |
| Visual regression baseline (AIW-102) | Planned | **Warning-only** initially (small, stable screen set per AIW-102's own scope) |
| SBOM + build provenance (AIW-101) | Planned | Generated, not itself a pass/fail gate - a release without one is incomplete, not rejected |

## STAGING/PROD promotion gate

Checks a running environment, not just source or an image in isolation - only meaningful once
Säule 1 (Azure infrastructure) actually exists:

| Check | Status | Blocking? |
|---|---|---|
| Deployment health checks / smoke tests (AIW-85) | Planned | Yes - failed smoke test blocks promotion |
| OWASP ZAP baseline DAST against STAGING (AIW-97) | Planned | Yes for new HIGH/CRITICAL findings |
| Human approval for PROD (AIW-83) | Planned | Yes - PROD promotion is never fully automatic |

## Coverage and threshold ratchet

**Lock in whatever the first real measurement is as the floor** (a PR may not drop coverage below
the current baseline), then ratchet the floor upward over time toward an initial target of
**~70% line / ~60% branch coverage** - not 100%, and not enforced retroactively against code that
predates the tooling. Coverage regressions are blocking from day one (never allowed to silently
get worse); reaching the target itself is gradual, warning-only progress until it's actually met.
The same ratchet logic applies to accessibility (AIW-98) and visual regression (AIW-102): start
warning-only against a real baseline, promote to blocking once that baseline is proven stable.

**Frontend (AIW-89), current floor:** measured at 9.42% statements / 6.19% branches / 5.5%
functions / 10% lines (`frontend/vite.config.ts`'s `test.coverage.thresholds`, rounded down
slightly for stability) - almost nothing had tests before AIW-88/89 landed, so this floor is
intentionally far below the ~70%/60% target. It climbs ticket-by-ticket as more
components/modules get real tests, not in one jump. **Coverage is a tool for finding untested
code, not a target in itself** - a file being 100% "covered" says nothing about whether its tests
assert anything meaningful; always prefer a real behavioral test over chasing a percentage.

**Backend (AIW-90), current floor:** measured at 95.5% line / 85.6% branch coverage
(`backend/pom.xml`'s `jacoco-maven-plugin` `check` execution, rounded down slightly for
stability: 93%/80%) - unlike the frontend, this baseline already exceeds the ~70%/60% target,
since the existing 167 tests (built up across M1 and the AIW-59/87/88 work) already exercise most
of the codebase through Spring-context tests. The floor is locked to this real measurement, not
artificially lowered to the target.

## Security severity policy

A **new** HIGH or CRITICAL finding (SAST, dependency, container image, or DAST) blocks the PR or
release it first appears in. MEDIUM/LOW findings are logged and triaged (tracked, not
auto-blocking) - this project's current scale doesn't warrant a formal SLA beyond "reviewed, not
ignored." A finding that already existed before a given PR (not newly introduced by it) does not
retroactively block that PR - it's tracked as existing debt, addressed on its own ticket, so
fixing an unrelated pre-existing finding is never a precondition for shipping unrelated work.
Secret scanning is the one exception with no severity tiering: any detected secret blocks,
unconditionally, given the real incident already documented in
[`secret-management.md`](secret-management.md).

## Emergency bypass procedure

Bypassing a blocking gate must be an explicit, visible decision - never a silent skip:

* The bypass reason is stated in the PR description (or, for a direct emergency fix, the commit
  message) - what's being bypassed and why.
* The same explicit human approval this project already requires for every merge (see the
  project's standing "ja push" / "ja merge" workflow) covers a bypass decision too - approving a
  bypass is a distinct, deliberate choice, not implied by approving the merge itself.
* GitHub's own audit trail (who merged, when, which required checks were overridden) is the
  record - no separate bypass-tracking tool is introduced for a project this size.
* `develop`'s branch protection has `enforce_admins: true` - an admin merging past a failing
  required check is no longer silently possible; GitHub itself blocks it the same way for
  everyone, closing what was previously a silent bypass path.

## Branch protection changes applied with this policy

Both flagged as needing explicit sign-off (branch protection is shared infrastructure) before
being applied - approved and applied together with this document:

1. Added **Backend Docker image** (AIW-68's `docker-build` job) to `develop`'s required status
   checks, alongside the existing `Backend tests`/`Frontend build`.
2. Set `enforce_admins: true` on `develop`'s branch protection.
