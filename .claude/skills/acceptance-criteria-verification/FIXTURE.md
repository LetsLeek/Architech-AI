# Worked examples

## 1. Fully satisfied (real data: AIW-93)

**Input:** AIW-93's real acceptance criteria (7 bullets) plus its real verification evidence
(Semgrep configured and passing, `sast-ci.yml` required on every PR, SARIF report + job-summary
findings, `permissions: contents: read`, documented `nosemgrep` justification convention,
documented CodeQL-vs-Semgrep fallback rationale).

**Output** - note criterion 1's status specifically: the literal clause ("CodeQL is configured")
is false, but criterion 7 is this ticket's own documented escape valve for exactly that case, so
criterion 1 is `not-applicable` with the reasoning stated, not `not-satisfied`:

```json
{
  "criteria": [
    { "text": "GitHub CodeQL is configured for Java and JavaScript/TypeScript when supported by the repository plan.", "status": "not-applicable", "evidence": "Repo plan does not support CodeQL - verified via PATCH attempt to enable secret_scanning-adjacent security_and_analysis returning null/403 on the code-scanning endpoints (AIW-93's own investigation). This clause's precondition ('when supported') never triggers.", "gap": null },
    { "text": "Scanning runs automatically on pull requests/default branch at an appropriate cadence.", "status": "satisfied", "evidence": "sast-ci.yml triggers on push+pull_request to develop/main.", "gap": null },
    { "text": "Security findings are visible in a central location and linked to the affected code.", "status": "satisfied", "evidence": "SARIF artifact (30-day retention) + AIW-100's job-summary step lists file/line per finding.", "gap": null },
    { "text": "New high-confidence HIGH/CRITICAL findings are treated as merge blockers according to AIW-87 policy where platform capabilities support enforcement.", "status": "satisfied", "evidence": "--severity ERROR --error gate, 'SAST (Semgrep)' is a required status check on develop.", "gap": null },
    { "text": "False-positive/bypass handling requires documented justification.", "status": "satisfied", "evidence": "nosemgrep convention (rule-id + reason required) documented in ci-quality-gate-policy.md.", "gap": null },
    { "text": "Workflow permissions follow least privilege.", "status": "satisfied", "evidence": "sast-ci.yml declares permissions: contents: read explicitly.", "gap": null },
    { "text": "If the GitHub plan does not support the required private-repository feature, the limitation and an approved SAST fallback are documented and implemented rather than silently omitting SAST.", "status": "satisfied", "evidence": "Semgrep fallback implemented and documented, with the plan limitation stated explicitly (this is what makes criterion 1's not-applicable status legitimate rather than a loophole).", "gap": null }
  ],
  "overallStatus": "complete",
  "ambiguousCriteria": []
}
```

## 2. Partially satisfied (real data: a real intermediate state during AIW-92's own development)

**Input:** AIW-92's real acceptance criteria, evaluated at a real point *during* that ticket's
actual development in this session - before a genuine bug (a `getByRole('button', { name: 'Add
evidence' })` locator matching two elements, since both the free-text and structured input
sections have identically-labeled submit buttons) had been fixed.

**Output:**

```json
{
  "criteria": [
    { "text": "Playwright is configured and runnable locally and in GitHub Actions.", "status": "satisfied", "evidence": "playwright.config.ts committed, webServer entries boot frontend+backend.", "gap": null },
    { "text": "Critical happy-path flows are covered first (application load, navigation and at least one core project/requirements interaction available in the current product state).", "status": "not-satisfied", "evidence": "critical-flow.spec.ts exists but fails: strict-mode violation on an ambiguous locator, so the flow does not actually pass end to end yet.", "gap": "Locator needs scoping to the specific input section before this flow genuinely passes." },
    { "text": "Tests use stable selectors and avoid brittle implementation-specific selectors.", "status": "not-satisfied", "evidence": "Selectors are role/text-based in style, but one is ambiguous in practice (matches two real elements) - 'stable' has to mean 'resolves to exactly the intended element,' not just 'uses the right kind of API.'", "gap": "Same locator as above needs scoping (e.g. to the containing section.input-section) to be genuinely stable, not just stylistically role-based." },
    { "text": "CI captures Playwright HTML report, trace and screenshots/videos on failures where useful.", "status": "satisfied", "evidence": "Upload step configured with if: always(), retain-on-failure trace/video/screenshot settings.", "gap": null },
    { "text": "Tests run against an isolated test environment or STAGING depending on the workflow stage.", "status": "satisfied", "evidence": "Runs against the real local/CI stack (mock AI, disposable-or-local Postgres) - the isolated-local-stack case, appropriate for this workflow stage.", "gap": null },
    { "text": "Failed required E2E tests block promotion to production.", "status": "not-verifiable", "evidence": "No production promotion pipeline exists yet in this repository (blocked on Säule 1/Azure) - this criterion can't be checked against a real promotion gate that doesn't exist.", "gap": "Re-evaluate once AIW-79-86's deployment pipeline exists." },
    { "text": "Test data setup/cleanup is deterministic and does not use production data.", "status": "satisfied", "evidence": "Mock AI provider, disposable/local Postgres, no production data path exists in this test setup at all.", "gap": null },
    { "text": "Test suite is designed to be sharded later if runtime grows.", "status": "satisfied", "evidence": "playwright.config.ts's own comment documents the single-shared-backend assumption and what sharding would require revisiting.", "gap": null }
  ],
  "overallStatus": "incomplete",
  "ambiguousCriteria": []
}
```

This snapshot is real, not hypothetical - it's exactly the state this session's own AIW-92 work
was actually in before the locator fix, which is why `acceptance-criteria-verification` exists:
the ticket was NOT done at that point even though most criteria already looked fine, and this
skill's job is to say so plainly rather than count "6 of 8 look good" as good enough.

## 3. Unverifiable criterion (real data: AIW-101)

**Input:** AIW-101's real acceptance criteria, specifically: "Verification procedure for an
artifact's origin is documented."

**Output** (excerpt - just the relevant criterion):

```json
{
  "text": "Verification procedure for an artifact's origin is documented.",
  "status": "not-verifiable",
  "evidence": "docs/operations/ci-quality-gate-policy.md's 'SBOM and build provenance' section contains a numbered 4-step procedure (rebuild locally, compare digests, cross-check the OCI revision label, gh attestation verify). The procedure's existence is confirmed by reading the file - but whether it is actually clear and complete enough for someone unfamiliar with this ticket to follow is a documentation-quality judgment, not something this skill can mechanically confirm.",
  "gap": "A human reviewer should read the linked section directly and judge its clarity themselves rather than accept this skill's own assessment of prose quality."
}
```

Marking this `satisfied` because a section with the right heading exists would be exactly the
"code appearance without real evidence" shortcut this ticket's own behavioral rules forbid -
`not-verifiable` with a pointer to what a human should actually read is the honest answer.
