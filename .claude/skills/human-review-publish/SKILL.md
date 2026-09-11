---
name: human-review-publish
description: The explicit checkpoint between local implementation and externally visible Git publication - use this after acceptance-criteria-verification, every time, with no exceptions for tickets that "obviously" look ready.
---

# Human Review and Publish

## Purpose

Be the one place in the whole chain where a human, not the agent, decides whether a change is
ready to become visible to anyone else. Every earlier skill in this contract stays `read-only`
or `local-git` at most - this is the first and only skill whose normal operation reaches
`external-visible` actions (commit, push, PR), and it only reaches them after a real, current,
specific approval.

## When to use

After `acceptance-criteria-verification`, for every ticket, with no exceptions - not for a
change that "obviously" looks fine, not because a similar ticket was approved quickly last time.
Also re-run (from the review step, not skipped straight to publish) whenever code changes after
an approval was already given - see Behavioral rules.

## Inputs

- `implementationContext`, `verificationReport`, `acceptanceCriteriaReport` (all required):
  the outputs of every prior skill in this chain, for the same ticket.
- `approval` (optional): absent on the first invocation. Present only on a later, separate
  invocation, and must name the specific reviewed change set it approves - never inferred from
  an earlier, different approval on a different change set.

## Preconditions

- `acceptanceCriteriaReport.overallStatus` is `complete` - this skill refuses to present a
  change set as ready for approval when its own acceptance-criteria check says it isn't (see
  Stop conditions).
- `safe-git-branch` has already prepared the branch this work is on.

## Steps - Phase 1: Review (always runs first)

1. Confirm `acceptanceCriteriaReport.overallStatus` is `complete`. If it isn't, stop here and
   route back to implementation - do not present an incomplete change set as if it were a normal
   review checkpoint.
2. Assemble the review summary: changed files (from `verificationReport`), a concise
   implementation summary, every check executed and its result
   (`verificationReport.checksRun`), acceptance-criteria status
   (`acceptanceCriteriaReport.criteria`), and known limitations/risks
   (`verificationReport.residualRisks` + anything `acceptanceCriteriaReport` flagged as
   `not-verifiable`).
3. State explicitly, verbatim, that commit/push/PR have **not** yet happened - never let the
   review summary read ambiguously as if publication already occurred.
4. Stop. This is not a soft pause the skill can talk itself past - it is the skill's own normal,
   expected stopping point, every single time, whether the change looks large or trivial.

## Steps - Phase 2: Publish (only on a later invocation, only with matching approval)

5. Confirm the `approval` input names *this exact* change set (the same commit content /
   branch state the review summary in Phase 1 described) - not a generic "yes" that could apply
   to anything, and not an approval given for a version of the change that has since been
   edited (see Behavioral rules - stale approval never carries forward).
6. Create a clear, ticket-referenced commit (this repository's own convention: a message ending
   in a `Co-Authored-By:`/session-reference trailer, per existing commits in this repo).
7. Push the branch - only now, only with approval matching this change set.
8. Create a pull request with ticket context, implementation summary, and verification results
   (mirroring `verificationReport`/`acceptanceCriteriaReport`, not a fresh, unverified claim) -
   never state a check passed if `verificationReport` shows it as `failed` or `not-run`.

## Behavioral rules

- No automatic commit/push/PR immediately after implementation - Phase 1 always runs first and
  always stops.
- Approval must be specific to the current reviewed change set. If the code changes after
  review - even a small follow-up fix, even one that looks obviously safe - the previous
  approval does not silently cover it; go back to Phase 1 with the updated state, and (per
  `implementation-verification`'s own scope) re-run whatever verification the change actually
  touches before presenting the new review.
- No force-push, ever, unless separately and explicitly authorized outside this skill's normal
  scope (this skill's own `allowedActionCategories` doesn't include `destructive` at all).
- A PR's description must never claim a check passed if `verificationReport` shows it `failed`
  or `not-run` - the PR is only as honest as the report it's built from.

## Output / result contract

A single `reviewResult` object:

```json
{
  "phase": "review",
  "changedFiles": ["..."],
  "summary": "...",
  "checksRun": ["... (mirrors verificationReport.checksRun)"],
  "acceptanceCriteriaStatus": "complete",
  "risks": ["..."],
  "publicationPerformed": false,
  "awaitingApproval": true
}
```

On a later, approved invocation, `phase` becomes `"published"`, `publicationPerformed` becomes
`true`, and the object additionally carries `commitSha`, `branchName`, and `prUrl`.
`awaitingApproval` is `true` for every `"review"`-phase result, always - it is never optimistic
about approval being likely or expected soon.

## Stop conditions

- `acceptanceCriteriaReport.overallStatus` is not `complete` - route back to implementation
  instead of presenting a review checkpoint.
- No approval has been given yet for this specific change set - the skill's own normal stopping
  point, not an error.
- The code changed materially after a prior approval - the prior approval does not carry
  forward; a new Phase 1 review is required.
- Approval names a force-push or any destructive action - out of scope regardless of context.

## Allowed action categories

- `local-git` - the ticket-referenced commit itself.
- `external-visible` - push and PR creation, **only** in Phase 2, **only** after a matching
  approval was actually given. Never `destructive` - this skill's own scope excludes force-push
  and any irreversible action unconditionally.

## Authority

A real, current, specific human approval is authoritative over this skill's own judgment about
whether a change is "obviously" ready - no change set, however small, skips Phase 1's stop. Once
given, an approval is authoritative only for the exact change set it was given against; any
material change after that requires a fresh one, per this ticket's own explicit rule.
