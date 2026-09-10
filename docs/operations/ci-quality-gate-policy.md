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
| Playwright E2E against an isolated local stack (mock AI, disposable Postgres, AIW-92) + accessibility checks (axe-core, AIW-98 - see [below](#accessibility-checks-axe-core-in-playwright)) | Existing, required (promoted from warning-only - 10/10 green runs since AIW-92 landed) | Yes - E2E failure always; new serious/critical a11y violations |
| Frontend coverage thresholds (AIW-89) | Existing, required | Yes - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |
| Backend coverage thresholds (AIW-90) | Existing, required | Yes - see [Coverage and threshold ratchet](#coverage-and-threshold-ratchet) |

This deliberately incorporates AIW-22/23/24's existing checks rather than duplicating them - the
CI workflows and `develop`'s branch protection required-status-checks list (`Backend tests`,
`Frontend build`, `Backend Docker image`, `Frontend tests`, `SAST (Semgrep)`,
`Dependency scan (Trivy)`, `Secret scan (Gitleaks)`, `Playwright E2E`) are the actual PR gate;
this document records the policy behind that configuration, not a second parallel mechanism.

## Build/release gate

Runs after merge, against the artifact that would actually get promoted - heavier or slower
checks belong here rather than on every PR iteration:

| Check | Status | Blocking? |
|---|---|---|
| Visual regression baseline (`Visual Regression` job, e2e-ci.yml - see [below](#visual-regression-aiw-102), AIW-102) | Existing | **Warning-only** (small, stable screen set per AIW-102's own scope) |
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

**Playwright E2E (AIW-92):** landed warning-only initially (first run of this suite in the repo,
heavier/slower than the PR gate's other checks, hadn't yet proven itself flake-free). Promoted
to required as of AIW-98, after 10/10 green runs across every PR since it landed - the ratchet's
own stated exit condition ("promote to blocking once stable for a run-window") was met. See
[`e2e/README.md`](../../e2e/README.md) for why its "happy path" asserts on a validation-failure
terminal state (the platform's only registered AI provider outside an opt-in real-AI profile is
a deterministic mock) rather than a fabricated success.

## Accessibility checks: axe-core in Playwright

`@axe-core/playwright` runs against every critical screen (`e2e/tests/accessibility.spec.ts`) in
the same Playwright suite/job as AIW-92's E2E journey test - promoted to required alongside it
(see immediately above), rather than needing its own separate warning-only ratchet period,
because its own baseline measured clean from the first run.

**Severity tiering, same shape as SAST/dependency/container scanning:** only `serious`/`critical`
impact violations fail the build, matching AIW-87's HIGH/CRITICAL-blocks policy applied to
axe's own impact scale; `moderate`/`minor` findings are attached to the test result in full
(`testInfo.attach('axe-violations', ...)`) so they stay visible for triage without blocking
merges over cosmetic issues.

**Automated checks are a floor, not a substitute** for manual keyboard-navigation and
screen-reader review - axe-core can only catch mechanically detectable issues (missing labels,
invalid ARIA, contrast, structural landmarks, ...), never whether a flow is actually usable
operated by keyboard/screen-reader alone. `e2e/README.md` states this explicitly next to the
tests themselves, not just here.

**Baseline:** the first scan found one real critical violation (`FileInputSection`'s file input
had no accessible label) and two moderate ones (missing `<main>` landmark). Fixed the same way
every other AIW-93..97-era gate fixed its own baseline rather than suppressing it: added a
`<label>` wrapping the file input, and wrapped the routed page content in `<main>` in `App.tsx`.
0 violations at any severity as of AIW-98, across both critical screens.

**Documented exceptions**, if one is ever genuinely needed (a rule disabled via
`.disableRules([...])` or `.exclude(...)` on a specific `AxeBuilder` call), require an inline
comment explaining why the flagged element isn't a real barrier and what the follow-up path is -
same convention as `.gitleaksignore`/`nosemgrep` elsewhere in this repo. None exist today.

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

## Workflow hardening and optimization (AIW-99)

Applies uniformly across all six workflows (`backend-ci.yml`, `frontend-ci.yml`, `e2e-ci.yml`,
`sast-ci.yml`, `dependency-scan-ci.yml`, `secret-scan-ci.yml`):

**Concurrency.** Every workflow now has:
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```
A new push to the same branch/PR cancels whatever's already running for it instead of letting a
now-superseded run finish uselessly - pure cost/time savings, no behavior change to what
actually gets checked.

**Least-privilege permissions.** The repo's own default (`default_workflow_permissions: read`,
confirmed via `GET /repos/.../actions/permissions/workflow`) already means a workflow with no
explicit `permissions:` block only gets read access - but an explicit `permissions: contents:
read` on every workflow (all six now have it) makes that self-documenting and audit-proof
against the *default* ever being changed later, rather than relying on it silently.

**Dependency caching.** `backend-ci.yml`, `frontend-ci.yml`, and `dependency-scan-ci.yml`
already had `cache: maven`/`cache: npm` on their `setup-java`/`setup-node` steps.
`e2e-ci.yml`'s `setup-node` step was missing it entirely (its `setup-java` step already had
`cache: maven`) - fixed, with both `frontend/package-lock.json` and `e2e/package-lock.json`
listed as cache-dependency paths, since that job installs both packages. All caching uses the
setup actions' own built-in cache handling, which already degrades to a normal (slower, not
broken) install on a cache miss - nothing here can fail a job just because the cache is cold.

**Timeouts.** Every job now has `timeout-minutes`, sized from actually-observed run times (see
the before/after table below) with headroom for normal CI variance - a genuinely hung job now
fails within single-digit minutes instead of potentially running for GitHub's 360-minute
default.

**Action pinning policy**, formalized (already practiced since AIW-93, not previously written
down as a standing rule): every third-party `uses:` - including first-party `actions/*` ones -
is pinned to a full 40-character commit SHA with a trailing `# vX` comment, never a mutable tag
or branch. Verify a tag's resolved commit with `gh api repos/{owner}/{repo}/commits/{tag}`
before pinning (cross-check against `git/refs/tags/{tag}` when in doubt, since an annotated
tag's ref SHA points at the tag object, not the commit, and the two must match). Semgrep's
`p/owasp-top-ten` ruleset (already run on every PR via `sast-ci.yml`) includes a rule that
flags exactly this pattern (`github-actions-mutable-action-tag`) - re-confirmed 0 findings
across all six workflow files as part of landing this ticket, so the policy isn't just written
down, it's continuously enforced by a gate that already exists.

**Path filtering: deliberately not used.** Every workflow triggers on every push/PR to
`main`/`develop` regardless of which files changed. AIW-99's own AC warns against path filters
that "accidentally skip required cross-stack checks" - given SAST/dependency/secret scanning
all reasonably need to see the *whole* repo state (a vulnerable dependency or a leaked secret
doesn't respect a `paths:` filter), and E2E/accessibility exercise both frontend and backend
together, no combination of path filters here could be scoped safely without real risk of a
required check silently not running on a PR that actually needed it. The AC's caution is a
reason not to add them, not an oversight to fix.

**Untrusted PR code cannot reach production secrets.** No workflow references `secrets.*`
anywhere (`grep -rn "secrets\." .github/workflows/` - zero matches) - there is nothing to leak,
since none of these jobs authenticate to anything (no registry push, no cloud credentials; see
AIW-96's own note on this for the Docker build specifically). All six workflows trigger on plain
`pull_request` (`grep -rn "pull_request_target" .github/workflows/` - zero matches), never the
more permissive `pull_request_target`, which is the trigger that would otherwise hand a
fork-originated PR the base branch's secrets/permissions. This stays true as new workflows are
added - `pull_request_target` should never be reached for in this repo without a specific,
reviewed reason.

**Before/after (evidence, not estimate)** - per-job wall time, most recent run of each workflow
immediately before this ticket's changes vs. immediately after, both from `gh run view --json
jobs`:

| Job | Before | After (PR #62) |
|---|---|---|
| Backend tests | 75s | 75s |
| Backend Docker image | 101s | 120s |
| Frontend build | 19s | 27s |
| Frontend tests | 18s | 26s |
| Playwright E2E | 97s | 104s |
| SAST (Semgrep) | 37s | 38s |
| Dependency scan (Trivy) | 37s | 24s |
| Secret scan (Gitleaks) | 8s | 7s |

The concurrency/cache changes are not expected to meaningfully change a single, non-superseded
run's own wall time (the caching gaps fixed were on a job that already had partial caching,
and cancel-in-progress only saves time across *superseded* runs, which a single measurement
can't show) - their value shows up as reduced total runner-minutes billed over many pushes
during active development (redundant superseded runs no longer run to completion), not as a
per-run speedup. The table above exists to prove nothing regressed, not to claim a speedup that
this kind of change doesn't produce - and that's what it shows: every job landed within normal
CI variance of its baseline (±10-25s on jobs in the 20-120s range), no regression.

## CI report publishing (AIW-100)

Makes what actually failed visible directly on the workflow run's summary page (GitHub's
`$GITHUB_STEP_SUMMARY`, rendered as sanitized markdown - no script execution, so writing
untrusted PR-derived content there carries no privileged-execution risk per AIW-100's own AC),
not just a generic non-zero exit code buried in a raw log.

**Every scanner** (Semgrep, Trivy fs, Trivy image, Gitleaks) now writes a summary immediately
after its own report-generation step (so it appears even if the later gate step fails the job):
total finding count, then each finding as a collapsible `<details>` block using the SARIF's own
`message.text` - which, for Trivy especially, already includes a working `[CVE-ID](link)`
markdown link, package name, installed/fixed versions. **Gitleaks is the one deliberate
exception**: its summary step only ever reads `ruleId`/file/line, never
`.locations[0].physicalLocation.region.snippet` - that field is where Gitleaks' own SARIF puts
the actual matched secret text, and a summary whose entire purpose is flagging a leak must never
itself become a wider-audience copy of that leak (discovered this distinction the hard way
during this ticket's own local testing - see [[architech_secret_handling]] in this session's
memory for the incident, not repeated here since it involved a real credential).

**Backend/frontend test and coverage reports**: `backend-ci.yml`'s `test` job now also uploads
raw Surefire/Failsafe output (`backend-test-reports`, 7-day retention - large text, only useful
for debugging a recent failure) alongside the existing coverage artifact, and writes a
line/branch % table to the job summary (parsed from `jacoco.csv`, same numbers AIW-90 already
made a build artifact - nothing more sensitive exposed). `frontend-ci.yml`'s `test` job does the
same from Vitest's `json-summary` coverage reporter (added in `vite.config.ts` alongside the
existing text/html/lcov reporters).

**Retention, configured per artifact type rather than one default everywhere**:

| Artifact type | Retention | Why |
|---|---|---|
| Security SARIF reports (Semgrep/Trivy/Gitleaks) | 30 days | Small text files, real audit/compliance value |
| Coverage reports (backend/frontend) | 14 days | Moderate size, useful for a short trend window |
| Backend Surefire/Failsafe raw output | 7 days | Text, but only useful for debugging a recent failure |
| Playwright report/traces/videos | 7 days | Large binaries, only useful for debugging a recent failure |

**Untrusted PR content stays non-privileged**: every summary-writing step reads a scanner's own
output file with `jq`/`awk` and appends plain text to `$GITHUB_STEP_SUMMARY` - never `eval`,
never a shell interpolation of scanned content, never HTML/script rendering beyond what GitHub's
own step-summary sanitizer already allows. A malicious PR's file paths or matched snippets
flowing into a summary can at worst look visually odd in that PR's own check output - it cannot
execute anything, affect another PR, or reach a secret (see AIW-99's own confirmation: no
workflow here references `secrets.*` or uses `pull_request_target`).

## SBOM and build provenance (AIW-101)

Two independent layers, on the same `docker-build` job that already builds and vulnerability-
scans the image (AIW-68/96) - only after that image's HIGH/CRITICAL gate passes, since there's
no reason to attest provenance for an image that's already blocked from merging:

**1. Always present: `backend-sbom-provenance` artifact (30-day retention).** A CycloneDX SBOM
(`trivy image --format cyclonedx` - reuses the Trivy binary AIW-96 already installs, one
maintained tool rather than a second one) plus a plain `provenance.json` with repository, commit
SHA, workflow name, run ID/URL, artifact name, and the image's own content digest (`docker
inspect --format='{{.Id}}'` - there's no registry yet per AIW-70, so no `RepoDigest` from a push
exists; the image's own config digest is the stable identifier available without one). This
alone satisfies AIW-101's AC on release metadata identifying repository/commit/workflow/digest,
independent of whether the attestation layer below is available.

**2. Attempted: native GitHub attestations (`actions/attest-build-provenance` +
`actions/attest`, the latter replacing the now-deprecated `actions/attest-sbom` - switched
before merge after this ticket's own PR surfaced the deprecation warning).** Unlike
CodeQL/dependency review/secret scanning (AIW-93/94/95), this genuinely is **not** a GitHub
Advanced Security feature - it needs only `attestations: write` + `id-token: write` permissions
on the job and doesn't require pushing to a registry (`subject-digest` + `subject-name` is an
explicitly supported input per the action's own `action.yml`). Both steps run with
`continue-on-error: true`, and that mattered in practice: **confirmed blocked on this repo**,
with a precise, unambiguous error rather than a generic failure -
`Failed to persist attestation: Feature not available for user-owned private repositories. To
enable this feature, please make this repository public.` This is a **third, distinct**
plan-gap pattern in this repo (alongside the GHAS gap from AIW-93/94/95): not GHAS-gated, but
gated specifically for *user-owned* (personal-account) private repositories - an org-owned
private repo, or making this one public, would unlock it. Since `continue-on-error: true` was
already in place, the job still passed on the strength of layer 1 above; nothing needed fixing
to keep this ticket's own required check green.

**Verification procedure for an artifact's origin:**
1. Download the `backend-sbom-provenance` artifact from the `Backend Docker image` job of the
   commit's CI run (or from `backend-coverage`/other artifacts' sibling run) - `provenance.json`
   states the exact commit and image digest that build produced.
2. Rebuild locally (`docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t
   architech-backend:verify backend/`) and compare `docker inspect --format='{{.Id}}'
   architech-backend:verify` against `provenance.json`'s `imageDigest` - a match confirms the
   image is reproducible from that exact source commit (this only holds because the Dockerfile
   has no ambient/non-deterministic build inputs beyond `GIT_SHA` itself - see AIW-68's own
   design note on this).
3. Independently, `docker inspect architech-backend:verify` also shows the
   `org.opencontainers.image.revision` OCI label (AIW-68) baked in at build time - it should
   equal `provenance.json`'s `commit` field; a mismatch between the label, the digest-derived
   rebuild, and the git history itself is the actual tamper signal to look for.
4. `gh attestation verify oci:architech-backend@<digest> --owner LetsLeek` would
   cryptographically verify the attestation's Sigstore signature chain back to the specific
   workflow run - stronger than steps 1-3 alone, but **not usable today**: native attestations
   are confirmed blocked on this repo (see above). Steps 1-3 are the actual verification
   procedure until either this repo moves to an org, or goes public, or a registry (AIW-70)
   makes `push-to-registry`-based attestation available instead.

## Visual regression (AIW-102)

A separate `Visual Regression` job in `e2e-ci.yml` (own `npm run test:visual`, own artifact
upload) runs a deliberately small set of Playwright screenshot comparisons against the two
critical screens - see [`e2e/README.md`](../../e2e/README.md#visual-regression-aiw-102) for the
full write-up (mitigations against flaky diffs, how to regenerate baselines correctly, why
baseline updates require human review rather than an automatic CI step).

**Warning-only, deliberately not promoted alongside Playwright E2E/accessibility (AIW-98).**
Unlike those, visual comparisons stay inherently more exposed to environment-driven flakiness
(font rendering, anti-aliasing) even with `animations: 'disabled'` and masked dynamic regions -
this needs its own proof-of-stability window before following the same promotion path AIW-92's
journey test took. Revisit once it's accumulated a comparable stable run-history.

**Visual tests don't replace behavioral/unit/accessibility tests** - they run alongside
`critical-flow.spec.ts` (AIW-92) and `accessibility.spec.ts` (AIW-98), which already prove the
same flow actually works and is accessible; visual tests catch drift *on top of* that, nothing
more. The suite stays intentionally small for M2 (two screens) - AIW-102's own scope notes this
can expand later to cover generated customer website previews once the Designer Agent (Säule 4,
still blocked on its own frozen spec package) exists.

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
