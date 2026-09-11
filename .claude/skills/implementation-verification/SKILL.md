---
name: implementation-verification
description: Verifies implemented changes before they are presented for human review - use this after implementation, before handing anything to a human or claiming a ticket is done. Never skip this, even for a change that "obviously" works.
---

# Implementation Verification

## Purpose

Prove the implementation actually works, rather than asserting that it does. This is the one
skill in the chain whose entire job is to catch the gap between "I wrote code that should do X"
and "I ran the real thing and confirmed it does X."

## When to use

After implementation, before presenting anything to a human for review (and before this
session's own standing "ja push"/"ja merge" approval steps even become relevant - there is
nothing to approve if verification hasn't happened yet). Never skip it for a change that looks
small or obviously correct - this repo's own history includes real regressions and real
baseline drift that only surfaced because verification was actually run, not assumed (see
`FIXTURE.md`).

## Inputs

- `plan` (object, required): `implementation-planning`'s output for the same ticket -
  `filesToChange`/`testChanges` say which stacks and checks are actually relevant, so
  verification doesn't run irrelevant suites or, worse, skip a relevant one.

## Preconditions

- A branch prepared by `safe-git-branch` exists, with the implementation's changes present.
- `plan` is available, to know which stacks/checks apply.

## Steps

1. Review the actual diff (`git diff`/changed-files list) for anything unintended - a debug
   `console.log`/`System.out.println`, a stray file, a change outside what the plan scoped.
2. Run the narrowest relevant tests first (the specific test class/file touched), then the
   required broader/full checks - stack-aware, using this repo's own real commands, never a
   substitute or an assumption that "it probably still passes":
   - **Backend (Spring Boot)**: `./mvnw clean verify` from `backend/` - runs unit (Surefire) +
     integration (Failsafe, against a disposable Testcontainers Postgres - this is also where a
     Flyway migration gets genuinely exercised, not just reviewed by eye) + JaCoCo coverage
     check.
   - **Frontend (React/TS/Vite)**: `npm run test:coverage`, `npm run build`, `npm run lint`,
     each from `frontend/`.
   - **E2E**: `npm test` from `e2e/` (excludes `@visual` - see below).
   - **Visual regression**: `npm run test:visual` from `e2e/`, inside the pinned official
     Playwright Docker image if generating/checking baselines locally - a bare local run on a
     non-matching OS produces false-positive diffs (see `FIXTURE.md`'s partial-failure example),
     which is not the same thing as a real regression.
   - **Security scanners** (when the change could plausibly affect them - new dependency,
     new workflow, anything touching a file that might contain a credential): Semgrep, Trivy,
     Gitleaks, run in **git-aware mode only** - never `--no-git` or an equivalent
     ignore-gitignore mode against this real working directory; that exact mode has already
     caused one real secret exposure into a session transcript (a gitignored `.env` picked up
     during a `--no-git` test scan) and a second near-repeat of the same mistake caught just
     before it happened again, both in this repository's own session history - it is now a
     hard rule, not a judgment call.
   - **This skill's own conformance-check convention**: when the change is itself a
     `.claude/skills/*` skill, `docs/developer-agent/scripts/check_skill_conformance.py
     <skill-dir>` is the relevant check, same as every skill this contract has produced so far.
   - **IaC (Terraform)**, where applicable: `terraform fmt`/`validate` - `plan`/`apply` verified
     only once real cloud credentials exist (not yet, in this repository).
3. Verify database migrations/configuration changes specifically where the plan flagged them -
   confirm the migration actually applied cleanly in the integration-test run above, not just
   that the SQL file looks plausible.
4. Confirm nothing unintended was introduced: no secret, no debug artifact, no generated file
   that should have stayed gitignored, no scope creep beyond what the plan actually specified.
5. Record every check's real status - `passed`, `failed`, or `not-run` (tool unavailable, out
   of scope for this change). Never omit a relevant check because it's inconvenient to run.
6. If anything failed, either fix the underlying issue and re-verify, or report the failure
   honestly - **never weaken, skip, or delete a meaningful test solely to obtain a green
   result.** A test that's actually wrong (testing something no longer true) gets fixed to test
   the right thing, not removed to make the suite pass.
7. Note any deviation from `plan` (a file changed that wasn't listed, a test approach that
   differed) - this is expected sometimes, but it must be stated, not silently absorbed.

## Output / result contract

A single `verificationReport` object:

```json
{
  "changedFiles": ["..."],
  "checksRun": [
    { "command": "./mvnw clean verify", "status": "passed", "summary": "82 unit + 85 integration tests, coverage check passed" }
  ],
  "overallStatus": "verified",
  "planDeviations": ["..."],
  "residualRisks": ["..."]
}
```

`overallStatus` is one of `verified` (every relevant check passed), `failed` (a relevant check
failed and wasn't fixed), or `incomplete` (a relevant check could not be run at all - tool
unavailable, out of scope for the session). `checksRun` always lists every check that was
*relevant*, including `not-run` ones with a reason - a check silently never mentioned is not the
same as a check honestly marked `not-run`.

## Stop conditions

- A required check for a touched stack fails and can't be fixed within the ticket's own scope -
  report it plainly as `overallStatus: "failed"`, never claim `verified`.
- A required tool is unavailable (not installed, no network, no credentials) - report
  `overallStatus: "incomplete"` and exactly which check couldn't run, never silently treat
  "couldn't check" as "checked and fine."

## Allowed action categories

- `read-only` - runs tests/builds/lints/scanners and reads their output; produces no tracked
  repository change itself (build artifacts like `target/`, `coverage/`, `node_modules/` are
  already gitignored, ephemeral outputs of running the checks, not something this skill commits).

## Authority

A check's own real exit code and output are authoritative over any expectation about what it
"should" say - if a check that was expected to pass fails, the failure is what gets reported,
never smoothed over. `plan` is authoritative for *which* checks are relevant, but never for
*whether* they actually passed - that's determined by running them, not by the plan having
anticipated success.
