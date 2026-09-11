---
name: post-implementation-jira-update
description: Updates a ticket's Jira state with implementation evidence at defined points in the developer-agent workflow - local completion, PR creation, merge, or a blocked/partial-failure state - without ever rewriting the original ticket or marking incomplete work Done. Use this after human-review-publish's checkpoint, and again after a PR is actually merged, not as a substitute for either.
---

# Post-Implementation Jira Update

## Purpose

Keep a ticket's Jira state an honest, evidence-backed record of what was actually done - never a
rewrite of the original requirement, and never optimistic about work that isn't actually
complete. This skill is invoked more than once across one ticket's real lifecycle, not as a
single terminal step: once after `human-review-publish` reaches its review checkpoint (before any
push), again once a PR actually exists, again once that PR is actually merged, and, separately,
whenever an earlier phase stops with a real blocker - each a distinct, real point in this
project's own established workflow, not a single generic "done" notification.

## When to use

At one of four points, named by `trigger` (see Inputs) - never speculatively, and never combined
into one end-of-run summary that blurs "code was written" with "the ticket is actually finished."

## Inputs

- `implementationContext` (object, required): `jira-ticket-intake`'s output - carries `issueKey`
  and the ticket's own original description/acceptance criteria, so this skill can quote evidence
  *about* them without ever rewriting them.
- `verificationReport` (object, required): `implementation-verification`'s output.
- `acceptanceCriteriaReport` (object, required): `acceptance-criteria-verification`'s output.
- `reviewResult` (object, required): `human-review-publish`'s output for the same ticket (`phase`
  `"review"` or `"published"`, and when published: `commitSha`, `branchName`, `prUrl`).
- `trigger` (string, required): one of `"post-local-completion"` (review checkpoint reached, not
  yet published), `"post-publish"` (PR just created), `"post-merge"` (PR just merged), or
  `"blocked"` (an earlier phase stopped with a real blocker - failed verification, incomplete
  acceptance criteria, or a rejected review).
- `mergeConfirmation` (object, required only when `trigger` is `"post-merge"`): `{ prUrl,
  mergeCommitSha, mergedAt }` - real evidence the merge happened, not inferred from `reviewResult`
  alone (that only ever confirms the PR was *created*, per `human-review-publish`'s own scope,
  which explicitly stops at PR creation and never performs the merge itself).

## Preconditions

- Jira MCP tools (`mcp__atlassian__*`) are available and authenticated for this session.
- `implementationContext.issueKey` resolves to an existing, accessible issue.

## Steps

1. **Never touch the original ticket description or acceptance criteria.** This skill only adds
   a comment and, where warranted, transitions status - it never calls `editJiraIssue` on the
   description/AC fields to reflect what happened. Scope changes or failures are reported in a
   new comment, in the open, not folded into a silently rewritten original requirement.
2. **Assemble the comment** from real evidence already produced by earlier skills - never
   fabricate a check result or acceptance-criteria status that wasn't actually reported:
   - Concise implementation summary (from `reviewResult.summary` / `verificationReport`).
   - Branch/PR reference, when one exists (`reviewResult.branchName`/`prUrl`, or
     `mergeConfirmation.prUrl` for the merge case).
   - Verification and acceptance-criteria results (`verificationReport.checksRun`,
     `acceptanceCriteriaReport.criteria`) - pass/fail states as actually reported, never
     softened.
   - Known limitations/follow-up work (`verificationReport.residualRisks` +
     `acceptanceCriteriaReport`'s `not-verifiable` entries, if any).
3. **Decide the status transition**, strictly by `trigger` - see the table below. This project's
   own real, verified Jira history (every ticket transitioned through this session, e.g. AIW-110)
   never uses the `In Review` status even while a PR is open - it goes `To Do` → `In Progress` →
   `Done`, nothing between - so this skill's default deterministic mapping matches that
   established, observed practice rather than the theoretically-available `In Review` status:

   | `trigger` | Comment posted | Status transition |
   |---|---|---|
   | `post-local-completion` | Yes - local completion + what's awaiting review | None (stays `In Progress`) |
   | `post-publish` | Yes - PR link + evidence | None (stays `In Progress`) - matches this project's own observed practice |
   | `post-merge` | Yes - merge confirmation + evidence | `Done`, **only** if `acceptanceCriteriaReport.overallStatus` is `"complete"` |
   | `blocked` | Yes - what was attempted and what's blocking | None (stays wherever it already is) - never `Done` |

4. **Post the comment** (`mcp__atlassian__addCommentToJiraIssue`).
5. **Transition status**, only per the table above, only after the comment is posted (so the
   comment's evidence is always visible before or alongside any status change, never after a
   `Done` transition with no explanation attached yet).

## Output / result contract

A single `jiraUpdateResult` object:

```json
{
  "issueKey": "AIW-110",
  "trigger": "post-merge",
  "commentPosted": true,
  "commentBody": "...",
  "statusTransition": { "attempted": true, "from": "In Progress", "to": "Done", "reason": "acceptanceCriteriaReport.overallStatus is complete and mergeConfirmation is present" },
  "descriptionOrAcModified": false
}
```

`statusTransition.attempted` is `false` (with `to: null`) for `post-local-completion` and
`post-publish` by design, per the table above - this is not a bug or an omission, it's this
skill's own deterministic rule matching real project practice. `descriptionOrAcModified` is
always `false` - it exists in the contract specifically so a caller/reviewer can assert this
skill never did the one thing it must never do.

## Stop conditions

- `trigger` is `"post-merge"` but `mergeConfirmation` is absent or doesn't name a real
  `mergeCommitSha` - do not transition to `Done` on the strength of `reviewResult` alone (it only
  ever confirms a PR was created, not merged).
- `trigger` is `"post-merge"` but `acceptanceCriteriaReport.overallStatus` is not `"complete"` -
  do not transition to `Done`; post the comment (the merge is still real and worth recording) but
  leave status untouched and say why.
- A Jira tool call fails - report the failure plainly; never claim a comment was posted or a
  status changed when it wasn't.

## Allowed action categories

- `external-read` - reading the ticket's current state before posting (so a comment never
  duplicates one already posted for the same trigger/change set).
- `external-visible` - `addCommentToJiraIssue` and `transitionJiraIssue`. Per AIW-111 and this
  ticket's own behavioral rule ("Jira mutations that were not explicitly requested must respect
  the approval-boundary policy from AIW-111"), status transitions on the ticket the user is
  already actively working ("ja mach weiter mit AIW-XXX") fall under the same already-requested
  carry-out AIW-111 deliberately left `transitionJiraIssue` unrestricted for. A progress comment
  on that same ticket is treated the same way here, for the same reason - it is inherent to
  reporting on work the user explicitly asked to continue, not a separate, freestanding Jira
  write. If a stricter posture is wanted (e.g. requiring approval before any comment is posted,
  not just before ticket creation/edits), that is a `.claude/settings.json` change to make
  explicitly, not something this skill assumes.

## Authority

The ticket's own original description and acceptance criteria are authoritative over anything
this skill posts - a comment reports on them, it never supersedes or edits them (see Steps 1 and
the Output contract's `descriptionOrAcModified`). Real, verified evidence from `verificationReport`/
`acceptanceCriteriaReport`/`mergeConfirmation` is authoritative over this skill's own optimism -
where no real evidence exists for a claim (a check that wasn't run, a merge that wasn't
confirmed), this skill states that plainly rather than assuming success.
