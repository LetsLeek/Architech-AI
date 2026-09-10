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
| Backend Docker image build + vulnerability scan (`docker-build` job, AIW-68 + AIW-96 - see [below](#container-image-scanning-trivy-image-mode)) | Existing, required | Yes (build failure always; scan failure for fixable HIGH/CRITICAL findings) |
| Frontend unit/component tests (Vitest + RTL, AIW-88) | Existing, required | Yes |
| Backend integration tests against real Postgres (Testcontainers, AIW-91) | Existing, required | Yes |
| SAST (Semgrep - see [below](#sast-codeql-vs-semgrep-fallback), AIW-93) | Existing, required | Yes for new HIGH/CRITICAL findings (see [Security severity policy](#security-severity-policy)) |
| Dependency vulnerability check (Trivy - see [below](#dependency-vulnerability-scanning-trivy-fallback), AIW-94) | Existing, required | Yes for new HIGH/CRITICAL findings |
| Secret scanning (Gitleaks - see [below](#secret-scanning-gitleaks-fallback), AIW-95) | Existing, required | Yes - any detected secret blocks |
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
| Visual regression baseline (AIW-102) | Planned | **Warning-only** initially (small, stable screen set per AIW-102's own scope) |
| Playwright E2E against an isolated local stack (mock AI, disposable Postgres, AIW-92) | Existing (`e2e-ci.yml`) | **Warning-only** initially - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |
| SBOM + build provenance (AIW-101) | Planned | Generated, not itself a pass/fail gate - a release without one is incomplete, not rejected |

## STAGING/PROD promotion gate

Checks a running environment, not just source or an image in isolation - only meaningful once
Säule 1 (Azure infrastructure) actually exists:

| Check | Status | Blocking? |
|---|---|---|
| Deployment health checks / smoke tests (AIW-85) | Planned | Yes - failed smoke test blocks promotion |
| Playwright E2E against real STAGING (AIW-92) | Planned - blocked on Säule 1 (no STAGING yet) | Yes - failed required E2E blocks promotion to production, per AIW-92's own AC |
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

**Playwright E2E (AIW-92):** lands warning-only in the Build/release gate rather than blocking
the PR gate - it boots the real frontend, backend and a disposable Postgres together (heavier
and slower than the PR gate's checks, and this is the suite's first run in the repo, so it
hasn't yet proven itself flake-free). Promote it to blocking once it's been stable for a
run-window; see [`e2e/README.md`](../../e2e/README.md) for why its "happy path" asserts on a
validation-failure terminal state (the platform's only registered AI provider outside an opt-in
real-AI profile is a deterministic mock) rather than a fabricated success.

## SAST: CodeQL vs. Semgrep fallback

GitHub's native CodeQL code scanning requires GitHub Advanced Security on a private repository.
This repo is private, and GHAS is not available on its current plan - confirmed via the API:
`GET /repos/.../` returns `security_and_analysis: null` (populated with a real object wherever
GHAS is actually available, even if disabled), and `GET /repos/.../code-scanning/*` 403s. Making
the repo public would unlock free CodeQL but isn't appropriate for proprietary code, so per
AIW-93's own AC ("if the GitHub plan does not support the required private-repository feature,
the limitation and an approved SAST fallback are documented and implemented rather than silently
omitting SAST") - **Semgrep OSS** (`sast-ci.yml`) is that approved fallback, chosen (over
separate SpotBugs+FindSecBugs/ESLint-security tooling) because one tool with one ruleset covers
both Java and TypeScript, needs no GitHub/SaaS account, and runs as a plain CI job.

Since GitHub's Security tab isn't available either (same GHAS gap), findings stay visible two
other ways: the gate step's own text output lists file:line directly in the job log, and every
run uploads a full SARIF report (all severities, not just the blocking ones) as a build artifact
via `semgrep-sarif` - open it with any SARIF viewer (e.g. the VS Code SARIF Viewer extension) for
the complete picture, including WARNING/INFO findings the gate itself never blocks on.

**Severity mapping:** Semgrep's `ERROR` severity is this project's HIGH/CRITICAL (blocks per the
[Security severity policy](#security-severity-policy) below); `WARNING`/`INFO` are this project's
MEDIUM/LOW (visible in the SARIF artifact, never blocking). The gate runs `--severity ERROR
--error`, which restricts evaluation to ERROR-severity rules and fails the build if any of them
fire - confirmed by both a clean run (exit 0) and a deliberately introduced SQL-injection pattern
(exit 1, correctly flagged by `java.lang.security.audit.formatted-sql-string`) before this
threshold was locked in, the same way every other gate this session added was verified.

**Suppressing a finding requires a reason, not just silence:** an inline `// nosemgrep:
<rule-id>` comment with no justification is not an acceptable suppression - always pair it with
a `- <reason>` explaining why the match is a false positive or an accepted risk in that specific
spot (`// nosemgrep: java.lang.security.audit.formatted-sql-string - value is a compile-time
constant, never user input`). Excluding whole paths (generated code, vendored files) belongs in
`.semgrepignore` with the same reasoning as a comment above the entry, not a bare glob.

**Baseline:** 0 findings at any severity as of AIW-93 - the only findings the first scan turned
up were 13 mutable GitHub Actions tag references (`actions/checkout@v7` etc.) across the three
existing workflows, all pinned to full commit SHAs as part of landing this gate rather than
carried forward as suppressed debt. Since the baseline is genuinely clean, this gate is blocking
from day one rather than following the warning-only-until-proven ratchet used for
coverage/E2E/a11y.

## Dependency vulnerability scanning: Trivy fallback

Same GHAS gap as [CodeQL](#sast-codeql-vs-semgrep-fallback): GitHub's native "Dependency review"
action needs GitHub Advanced Security on a private repository - confirmed via
`GET /repos/.../dependency-graph/compare/{basehead}` returning 403 the same way the code-scanning
endpoints do. `dependency-scan-ci.yml` uses **Trivy** (filesystem mode) as the approved fallback,
chosen because it covers both Maven (`backend/pom.xml`) and npm (`frontend/`, `e2e/`
`package-lock.json`) in one tool and one pass, following the identical two-pass report+gate
pattern AIW-93 established (full SARIF report uploaded as a build artifact; a second,
severity-filtered `--severity HIGH,CRITICAL --exit-code 1` pass gates the build). Unlike
GitHub's native dependency review, this isn't scoped to just the PR's diff - it scans every
manifest on every run, which is a strictly stronger check (also catches a vulnerability newly
*disclosed* against an already-present dependency, not only one newly *introduced* by a PR).

`--include-dev-deps` is set explicitly - Trivy's default silently excludes npm
`devDependencies` (e2e/'s only dependency, `@playwright/test`, is one, so without this flag that
whole target scans as empty), which would leave real supply-chain exposure (arbitrary code
execution during `npm ci`/test runs) unchecked.

Dependabot itself is **not** affected by the GHAS gap - vulnerability alerts and automated
security updates are enabled directly via repo settings (`PUT
/repos/.../vulnerability-alerts` and `/automated-security-fixes`, confirmed available - these
endpoints are readable/writable on this plan, just off by default), and
[`.github/dependabot.yml`](../../.github/dependabot.yml) configures routine weekly
version-update PRs (grouped by minor/patch, capped `open-pull-requests-limit`) across all four
ecosystems in this repo (Maven, the two npm packages, and GitHub Actions itself - which keeps
AIW-93's SHA-pinned actions current without losing the pinning).

**Baseline:** the first scan found 3 CRITICAL Tomcat CVEs (CVE-2026-65182/65905/68525, via
`org.apache.tomcat.embed:tomcat-embed-core` 11.0.24, transitively pinned by
spring-boot-starter-parent). Fixed the same way AIW-93 fixed its own baseline findings rather
than suppressing them: `backend/pom.xml` now overrides Spring Boot's managed `tomcat.version` to
11.0.25 (removable once a spring-boot-starter-parent release manages that version itself).
0 findings at any severity as of AIW-94, across all three manifests including dev dependencies -
verified the gate both ways (temporarily reverting the Tomcat override reproduced the exit-1
failure before the fix was restored), so like AIW-93 this gate is blocking from day one rather
than warning-only.

## Container image scanning: Trivy image mode

Unlike SAST/dependency-review/secret-scanning, this one isn't a GHAS-availability fallback -
scanning a locally-built image doesn't touch any GitHub-native feature. AIW-96 extends the
existing `docker-build` job (`backend-ci.yml`, AIW-68) with Trivy in **image** mode (distinct
from AIW-94's **filesystem** mode - `pom.xml`/lockfiles vs. an actual container's OS packages
and JAR), scanning the exact image `docker-build` just produced, by tag, before anything could
push or retag it - the only "immutable identity" available today, since AIW-70's registry (and
therefore a real digest) doesn't exist yet. Follows AIW-93/94/95's report+gate pattern (SARIF
artifact; a `--severity HIGH,CRITICAL --exit-code 1` pass gates the job) with one addition:
`--ignore-unfixed`, so a HIGH/CRITICAL finding with no available fix is visible in the report
but never blocks - matching AIW-96's own AC ("vulnerabilities... with an available fix block
release"): unfixed findings are tracked/triaged, not actionable today, so they can't be blocking.

Landed in the **PR gate**, not Build/release - the already-required `docker-build` job runs on
every PR regardless, so extending it with a scan of the image it already built is not
meaningfully heavier than what's already blocking merges, and it means a vulnerable image never
gets merged instead of being caught after the fact. Once AIW-70's registry exists, a genuine
promotion-time re-scan by pushed digest can be added as a further, later gate - this doesn't
replace that, it's what's achievable with what exists today.

**Baseline:** the first scan found 5 fixable HIGH vulnerabilities in the runtime image's Alpine
OS packages (openssl/libssl3/libcrypto3, libexpat) - the `eclipse-temurin:21-jre-alpine` base
image's packages were older than Alpine's current advisories. Fixed the same way AIW-93/94/95
fixed their own baseline findings: `backend/Dockerfile`'s runtime stage now runs `apk update &&
apk upgrade --no-cache` right after `FROM`, pulling current patched packages at build time
instead of waiting for the next upstream base-image refresh - this actually resolved every
finding at every severity (34 → 0), not just the 5 blocking ones. Verified the rebuilt image
still starts and reports healthy against a real Postgres before locking this in. Like
AIW-93/94/95, this gate is blocking from day one against a genuinely clean baseline.

## Secret scanning: Gitleaks fallback

Same GHAS gap as [CodeQL](#sast-codeql-vs-semgrep-fallback) and
[dependency review](#dependency-vulnerability-scanning-trivy-fallback) - GitHub's native Secret
Scanning / Push Protection needs GitHub Advanced Security on a private repository. Confirmed
directly rather than inferred: a `PATCH` attempt to enable
`security_and_analysis.secret_scanning` returns `422 Secret scanning is not available for this
repository`. `secret-scan-ci.yml` uses **Gitleaks** as the approved fallback, scanning full git
history (`fetch-depth: 0`, not just the PR diff) on every push/PR - a secret later removed from
HEAD but still sitting somewhere in git log still gets caught, which matters for a check whose
whole point is protecting source control, not just the current tree.

Unlike Semgrep/Trivy, there's no severity tier to split into a report-only pass and a gate
pass: every Gitleaks match is already verified/high-confidence by construction, so the scan
itself is the gate (any match fails the job) - a SARIF report still uploads as a build artifact
(`if: always()`, since Gitleaks writes it before exiting non-zero on a match) purely for
visibility, given GitHub's Security tab isn't reachable without GHAS either.

**If the gate (or anyone) finds a real credential**, not a test fixture: rotate/revoke it at
the provider immediately - removing it from the latest commit is not sufficient, since it
already exists in git history and possibly in anyone's local clone or CI logs. Only after
rotation does cleaning history (if truly necessary) become a lower-priority follow-up. This
mirrors the real incident already documented in
[`secret-management.md`](secret-management.md#no-key-ever-appears-in-code-the-frontend-bundle-git-history-or-logs).

**Suppressing a match** (a genuine false positive, or a documented test fixture using an
obvious dummy value) goes in a `.gitleaksignore` file at repo root, one fingerprint per line
with a comment above it explaining why - like Semgrep's `nosemgrep` convention, a bare
suppression with no reason isn't acceptable, and since `.gitleaksignore` is a tracked file, any
change to it goes through the same PR review as everything else (satisfies AIW-95's
"bypass/allowlist changes are auditable and reviewed" AC without extra tooling). No such file
exists yet - the current baseline is 0 findings across all 84 commits in the repo's history, so
this gate is blocking from day one like AIW-93/94, verified both ways (a deliberately
introduced AWS-style key pattern was caught before removal).

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
