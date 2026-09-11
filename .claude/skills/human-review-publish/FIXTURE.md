# Worked examples

## 1. Approve path (real: AIW-109, this very chain's own most recent ticket)

**Phase 1 output** - the review summary actually presented for AIW-109:

```json
{
  "phase": "review",
  "changedFiles": [
    ".claude/skills/acceptance-criteria-verification/SKILL.md",
    ".claude/skills/acceptance-criteria-verification/skill.yaml",
    ".claude/skills/acceptance-criteria-verification/FIXTURE.md"
  ],
  "summary": "Sixth real skill built against AIW-114's contract - maps every AC criterion to concrete evidence.",
  "checksRun": ["check_skill_conformance.py: passed", "semgrep: 0 findings", "gitleaks: no leaks found"],
  "acceptanceCriteriaStatus": "complete",
  "risks": [],
  "publicationPerformed": false,
  "awaitingApproval": true
}
```

**What actually happened next**: the human replied "ja push und merge" - a single message naming
both approvals at once for this exact, just-presented change set (this repository's own session
history already has multiple real instances of a combined push+merge approval phrased this
way). Phase 2 then proceeded: commit already existed, branch pushed, PR #72 opened, CI polled
until green (9/9 checks), then merged.

**Phase 2 output:**

```json
{
  "phase": "published",
  "changedFiles": ["... (same as above)"],
  "summary": "... (same as above)",
  "checksRun": ["... (same as above)"],
  "acceptanceCriteriaStatus": "complete",
  "risks": [],
  "publicationPerformed": true,
  "awaitingApproval": false,
  "commitSha": "68e8dad",
  "branchName": "AIW-109-acceptance-criteria-verification-skill",
  "prUrl": "https://github.com/LetsLeek/Architech-AI/pull/72"
}
```

## 2. Reject / rework path (illustrative - no real rejection occurred in this project's session history to date)

**Phase 1 output** - same shape as above, but suppose the reviewer's actual reply had instead
been "die Fixtures sind mir zu erfunden, bitte mit echten Daten" (real project language for "the
fixtures are too made-up, please use real data").

**Result:** no `approval` input is ever produced. The skill does not proceed to Phase 2 at all -
the ticket goes back to whichever earlier skill in the chain owns the gap
(`implementation-planning`/the implementation itself, not this skill), and once reworked, a
**new** Phase 1 review is presented - not a diff against the old one, a complete fresh summary,
since the underlying change set is now different.

```json
{
  "phase": "review",
  "publicationPerformed": false,
  "awaitingApproval": true,
  "note": "Previous review rejected - reworked change set below is a new review, not a continuation of the rejected one."
}
```

## 3. Changed after approval - stale approval correctly not reused (real: AIW-99)

**What actually happened** (this repository's own real history): AIW-99's implementation was
reviewed and approved ("ja push"), pushed, and merged as PR #62. After CI ran, the actual
before/after timing measurements were filled into the documentation (a small, genuinely
low-risk follow-up, commit `03763e1`, "Fill in AIW-99's after-measurement from PR #62's own CI
run") - **not treated as already covered by the earlier "ja push."** A fresh, explicit question
was asked before pushing that follow-up commit, even though it was documentation-only and
low-risk, because it was still a materially different change set than the one the original
approval had named.

**Phase 1 output for the follow-up commit** (a new review, not a continuation):

```json
{
  "phase": "review",
  "changedFiles": ["docs/operations/ci-quality-gate-policy.md"],
  "summary": "Fill in AIW-99's real before/after CI timing table from PR #62's own run, replacing placeholder 'see PR' values.",
  "checksRun": ["Docs-only change - no application tests affected."],
  "acceptanceCriteriaStatus": "complete",
  "risks": [],
  "publicationPerformed": false,
  "awaitingApproval": true,
  "note": "This is a new change set on top of an already-approved-and-merged PR, not a continuation - the original 'ja push' approval does not cover it."
}
```

The human then explicitly re-approved ("Ja, pushen") before this second commit was pushed -
confirming the stale-approval rule isn't just a documented rule but something this project's
own sessions have actually followed in practice.

## 4. Failed verification (illustrative - grounded in this session's own established discipline)

**Input:** `verificationReport.overallStatus: "failed"` for some check.

**Result:** this skill never reaches Phase 1's "ready for review" framing at all -
`acceptance-criteria-verification` would already have reported `overallStatus: "incomplete"`
for any criterion depending on the failed check, which is this skill's own Phase 1, Step 1 stop
condition. In practice, throughout this project's own real session history, a failing check
(e.g. AIW-102's real CI visual-regression failure) has always been fixed and re-verified
*before* a review summary was ever presented - this skill's design assumes and enforces that
same discipline structurally, rather than relying on it being remembered every time.

```json
{
  "phase": "blocked-before-review",
  "publicationPerformed": false,
  "awaitingApproval": false,
  "note": "acceptanceCriteriaReport.overallStatus is 'incomplete' - routed back to implementation-verification/implementation-planning rather than presented as a review checkpoint."
}
```
