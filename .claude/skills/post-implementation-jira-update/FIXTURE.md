# Worked examples

Scenarios 1-3 are grounded in this ticket's own real Jira history: AIW-110's actual changelog
(verified directly against `mcp__atlassian__getJiraIssue` with `expand: changelog` while building
this skill) shows exactly two transitions - `To Do` → `In Progress` (2026-09-11T13:07), then
`In Progress` → `Done` (2026-09-11T13:17) - **never** `In Review`, even though PR #73 was open in
between. That's real, observed project practice, not an assumption, and it's what step 3's
deterministic table is built from. Scenario 4 is illustrative - this project has never actually
had a ticket reach a real `blocked` state at this skill's level (every close call, e.g. AIW-102's
real CI visual-regression failure, was resolved inside an earlier phase before ever reaching a
review checkpoint).

## 1. `post-local-completion` - review checkpoint reached, not yet published (real: AIW-110)

**Trigger context:** `human-review-publish` just presented its Phase 1 review summary for AIW-110
(`awaitingApproval: true`, `publicationPerformed: false`) - nothing has been pushed yet.

```json
{
  "issueKey": "AIW-110",
  "trigger": "post-local-completion",
  "commentPosted": true,
  "commentBody": "Implementation complete locally, awaiting review approval before publication.\n\nSummary: human-review-publish skill (SKILL.md + skill.yaml + FIXTURE.md) implemented against AIW-114's contract.\nChecks: check_skill_conformance.py passed; semgrep 0 findings; gitleaks no leaks found.\nAcceptance criteria: complete.\nKnown limitations: none reported.\n\nNo commit/push/PR has been created yet - awaiting explicit approval.",
  "statusTransition": { "attempted": false, "from": "In Progress", "to": null, "reason": "post-local-completion never transitions status, per this skill's own deterministic table" },
  "descriptionOrAcModified": false
}
```

## 2. `post-publish` - PR created (real: AIW-110, PR #73)

**Trigger context:** the human approved ("ja push"), and `human-review-publish`'s Phase 2 created
commit `8287c80`, pushed the branch, and opened the real PR
`https://github.com/LetsLeek/Architech-AI/pull/73`.

```json
{
  "issueKey": "AIW-110",
  "trigger": "post-publish",
  "commentPosted": true,
  "commentBody": "Pull request opened: https://github.com/LetsLeek/Architech-AI/pull/73 (branch AIW-110-human-review-publish-skill, commit 8287c80).\n\nChecks: check_skill_conformance.py passed; semgrep 0 findings; gitleaks no leaks found.\nAcceptance criteria: complete.\n\nAwaiting CI and merge approval.",
  "statusTransition": { "attempted": false, "from": "In Progress", "to": null, "reason": "post-publish never transitions status - this project's own real Jira history never uses In Review while a PR is open, it stays In Progress until merged" },
  "descriptionOrAcModified": false
}
```

## 3. `post-merge` - completed (real: AIW-110)

**Trigger context:** CI passed (9/9 checks), the human approved ("ja merge"), and PR #73 was
actually merged - real, verified merge evidence, not inferred from the PR having been opened.

```json
{
  "issueKey": "AIW-110",
  "trigger": "post-merge",
  "mergeConfirmation": { "prUrl": "https://github.com/LetsLeek/Architech-AI/pull/73", "mergeCommitSha": "8287c80", "mergedAt": "2026-09-11T13:17:51+02:00" },
  "commentPosted": true,
  "commentBody": "Merged: https://github.com/LetsLeek/Architech-AI/pull/73 (8287c80).\n\nAll acceptance criteria verified complete. No known follow-up work.",
  "statusTransition": { "attempted": true, "from": "In Progress", "to": "Done", "reason": "acceptanceCriteriaReport.overallStatus is complete and mergeConfirmation is present" },
  "descriptionOrAcModified": false
}
```

This matches the real transition history exactly: straight `In Progress` → `Done`, timed right
after the real merge, not before it and not automatically from a "code was written" signal alone.

## 4. `blocked` - partial failure, never reaches Done (illustrative)

This project has never actually had a ticket stop here - every real near-miss (e.g. AIW-102's
visual-regression baseline mismatch) was caught and fixed by `implementation-verification`
*before* a review checkpoint was ever presented, per that skill's own design. This scenario shows
what this skill would do if `acceptance-criteria-verification` had instead reported
`overallStatus: "incomplete"` for a criterion whose check never actually ran.

```json
{
  "issueKey": "AIW-999",
  "trigger": "blocked",
  "commentPosted": true,
  "commentBody": "Implementation attempted but blocked: acceptance criterion 'Visual regression baseline matches CI' could not be verified - the local Playwright run used a different font-rendering environment than CI's ubuntu-latest, and no CI run has been executed against this branch yet to produce a real comparison.\n\nWhat was done: implementation complete, local tests pass.\nWhat's blocking: acceptance-criteria-verification reports overallStatus 'incomplete' for this criterion - see acceptanceCriteriaReport for detail.\nNo commit/push/PR has been created; this ticket is not ready for review.",
  "statusTransition": { "attempted": false, "from": "In Progress", "to": null, "reason": "blocked triggers never transition status, and never to Done regardless of how much code was written" },
  "descriptionOrAcModified": false
}
```

The comment is explicit about *what was attempted* and *why it isn't done* - never silent, and
never phrased to imply completion. `descriptionOrAcModified: false` here matters just as much as
in the success paths: even a blocked ticket's original requirement stays untouched, not quietly
narrowed to match what was actually achieved.
