# DAST scanning (AIW-97)

Dynamic application security testing against the deployed STAGING application, using OWASP ZAP's
baseline (passive) scan.

## How to run it

GitHub Actions → **DAST (OWASP ZAP baseline)** → **Run workflow**. `workflow_dispatch` only,
deliberately manual - a human decides when to scan, same pattern as STAGING/PROD promotion
(AIW-82/AIW-83) and rollback (AIW-86). A `workflow_run` trigger chained automatically off a
successful STAGING promotion was considered and rejected: this repo's default branch is `main`
(where `workflow_run` looks for the workflow file), while all real deploy activity happens on
`develop` - that mismatch would make the trigger silently not fire rather than reliably chain, a
worse failure mode than an explicit manual step.

Targets STAGING's own real URL (`staging-environment.md`) directly, via plain HTTP - no Azure
credentials of any kind are used or needed by this workflow.

## Findings classification (`.zap/rules.tsv`)

Version-controlled rule file, per this ticket's own AC. Any ZAP rule ID not listed defaults to
`WARN` (visible in the report, never blocks). The scan itself also runs with `-I` (ignore
warnings for exit-code purposes) during this **initial tuning period** - no findings have been
triaged yet, so nothing is currently set to `FAIL`.

**Tuning process** (once a real scan has actually run against STAGING): review the uploaded
`zap-baseline-report` artifact, then for each finding either:
- add an `IGNORE` line to `.zap/rules.tsv` with a comment explaining why it's a false positive or
  an accepted risk (never a blanket "ignore everything" - each one documented individually, per
  the AC's own "known acceptable findings are explicitly documented rather than globally
  ignored"), or
- add a `FAIL` line for a confirmed, real, high-confidence severe finding - this is what starts
  actually blocking production promotion for that specific rule, per AIW-87's severity policy
  (the same ERROR/HIGH-blocks, lower-doesn't posture already established for SAST/Trivy/Gitleaks
  in `ci-quality-gate-policy.md`).

Removing the `-I` flag entirely (so any untuned `WARN` also fails the build) is the natural next
step once enough tuning has happened that false-positive noise is low - not done yet, since that
would immediately block on completely untriaged findings the first time this ever runs for real.

## Authentication coverage

**Current status: 100% of the API is unauthenticated coverage, because 100% of the API is
unauthenticated.** There is no `SecurityConfig`/authentication layer anywhere in this backend
today (confirmed: no security configuration class exists in the codebase) - every route ZAP's
baseline scan can reach is exactly the same set of routes a real, unauthenticated attacker could
reach. This is not a scanning gap to document around; it's the actual current state of the
application. The moment authentication is added to any route (a distinct, future piece of work,
not part of AIW-97's own scope), this section must be revisited: baseline (unauthenticated) scan
coverage would then need to be explicitly distinguished from an authenticated scan (ZAP supports
this via a configured auth script/context, not the baseline action used here) to avoid a false
sense of complete coverage.

## Results retention

The scan action uploads its own artifact (`zap-baseline-report`) automatically. This one
artifact uses GitHub's default 90-day retention rather than this project's usual 30-day
security-finding convention (`ci-quality-gate-policy.md`/AIW-100) - the action's own inputs don't
expose a retention-days control, and a longer retention is a strictly safe deviation (more
history kept, not less), so it was left as-is rather than adding a redundant second upload step
purely to force the number down.

No credentials or sensitive response data are exposed: the scan targets STAGING - which has no
authentication to begin with (see above) - over plain HTTP requests ZAP itself generates; nothing
in this pipeline ever sends or logs a secret.

## Never targets production

This workflow has exactly one hardcoded target URL, STAGING's own (`STAGING_URL` env var) -
there is no PROD URL anywhere in this file, no input that could redirect it there, and no
trigger tied to a PROD deployment. Active/attack-style DAST against PROD is out of scope
entirely, not just switched off by default.

## Status

Built and ready, **not yet actually run against a real STAGING deployment** - per this session's
explicit instruction, pipelines are built without being executed against real infrastructure.
`.zap/rules.tsv` has zero real triaged entries as a direct consequence: there is nothing to triage
until a first real scan produces findings to review.
