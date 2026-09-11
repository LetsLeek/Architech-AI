# Worked examples

## 1. Pass (real: AIW-107)

**Input:** `implementation-planning`'s output for AIW-107 (safe-git-branch skill - a
`.claude/skills/` change only, no backend/frontend files touched).

**Output** (this repository's own real verification for that ticket, as actually run before its
PR was opened):

```json
{
  "changedFiles": [
    ".claude/skills/safe-git-branch/SKILL.md",
    ".claude/skills/safe-git-branch/skill.yaml",
    ".claude/skills/safe-git-branch/FIXTURE.md"
  ],
  "checksRun": [
    { "command": "python3 docs/developer-agent/scripts/check_skill_conformance.py .claude/skills/safe-git-branch", "status": "passed", "summary": "OK" },
    { "command": "semgrep scan --config p/owasp-top-ten --severity ERROR --error .claude/skills/safe-git-branch/", "status": "passed", "summary": "0 findings" },
    { "command": "gitleaks detect --source .", "status": "passed", "summary": "no leaks found, 109 commits scanned" }
  ],
  "overallStatus": "verified",
  "planDeviations": [],
  "residualRisks": []
}
```

No backend/frontend checks were run because none of those files were touched - `checksRun`
lists exactly the checks that were actually relevant, not a fixed list run unconditionally.

## 2. Partial failure, fixed, then genuinely verified (real: AIW-102)

**Input:** `implementation-planning`'s output for AIW-102 (visual regression tests).

**What actually happened** (this repository's own real history, commit `5923f61`): the first
verification attempt ran `npm run test:visual` inside the official Playwright Docker image
locally and it passed - but that was verifying against a *locally generated* baseline, not
against what CI would actually render. Pushed anyway to get CI's real signal; CI's own
`Visual Regression` job then failed both tests (~2% pixel diff, font-rendering differences
between the local Docker image and `ubuntu-latest`'s `playwright install --with-deps`
environment). The failure was diagnosed (not dismissed as noise), the baselines were corrected
using CI's own `*-actual.png` output, and the suite was re-verified - genuinely green afterward,
not just re-run until it happened to pass.

**Output at the point of the real failure:**

```json
{
  "changedFiles": ["e2e/tests/visual.spec.ts", "e2e/playwright.config.ts", "e2e/tests/visual.spec.ts-snapshots/projects-page-chromium-linux.png", "e2e/tests/visual.spec.ts-snapshots/project-detail-page-chromium-linux.png"],
  "checksRun": [
    { "command": "npm run test:visual (local, inside official Playwright Docker image)", "status": "passed", "summary": "2/2 passed - but only proves consistency with itself, not with CI" },
    { "command": "npm run test:visual (GitHub Actions, Visual Regression job)", "status": "failed", "summary": "2/2 failed - ~2% pixel diff, font rendering differs between the local Docker image and CI's ubuntu-latest environment" }
  ],
  "overallStatus": "failed",
  "planDeviations": [],
  "residualRisks": ["Baseline was generated in an environment that turned out not to match CI closely enough - documented as the real, repeatable baseline-correction procedure in e2e/README.md so this isn't rediscovered from scratch next time."]
}
```

This is exactly the behavior this skill exists to enforce: a passing *local* run is not the same
claim as "verified" - CI's own result is what actually matters, and a failure there is reported
as a failure, not quietly worked around.

## 3. Unavailable-tool scenario (illustrative, but grounded in a real requirement of this repo)

**Input:** `implementation-planning`'s output for a ticket touching `e2e/tests/*.spec.ts` in a
freshly cloned environment where `npx playwright install` was never run.

**Output:**

```json
{
  "changedFiles": ["e2e/tests/critical-flow.spec.ts"],
  "checksRun": [
    { "command": "npm test (e2e/)", "status": "not-run", "summary": "Chromium browser binary not installed in this environment (npx playwright install chromium required first) - not treated as a pass, and not silently skipped from the report." }
  ],
  "overallStatus": "incomplete",
  "planDeviations": [],
  "residualRisks": ["E2E behavior for this change has not actually been verified - must be run before this ticket can be considered done, not deferred indefinitely."]
}
```
