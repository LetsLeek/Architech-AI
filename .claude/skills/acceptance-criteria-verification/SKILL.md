---
name: acceptance-criteria-verification
description: Evaluates each Jira acceptance criterion against the implemented and verified result before human review - use this after implementation-verification, as the last skill before presenting anything to a human.
---

# Acceptance Criteria Verification

## Purpose

Answer, criterion by criterion, "does this actually satisfy what the ticket asked for" -
`implementation-verification` already proved the checks pass in general; this skill maps that
evidence (and the implementation itself) back onto the ticket's own specific acceptance
criteria, one at a time, so nothing gets silently dropped, reworded, or assumed.

## When to use

After `implementation-verification`, as the last skill before a human reviews anything. Not a
substitute for it - this skill consumes `verificationReport` as evidence, it does not re-run
tests/builds itself.

## Inputs

- `implementationContext` (object, required): `jira-ticket-intake`'s output -
  `acceptanceCriteria` is the list this skill checks off.
- `verificationReport` (object, required): `implementation-verification`'s output for the same
  ticket - reused as evidence, never duplicated by re-running checks here.

## Preconditions

- Both inputs were produced for the same ticket.
- `verificationReport.overallStatus` is not `incomplete` for any check a given criterion
  actually depends on - a criterion can't be marked satisfied on the strength of a check that
  never ran.

## Steps

1. Take `implementationContext.acceptanceCriteria` as the literal, complete list to evaluate -
   never drop one because it looks already covered by another, and never add one the ticket
   didn't actually state.
2. For each criterion, find concrete evidence: a specific file/test from
   `verificationReport.changedFiles`/`checksRun`, an observed behavior, a config/migration
   change - never "the code looks like it should do this." A criterion whose satisfaction
   depends on runtime/test behavior is not satisfied by code that merely appears correct on
   read-through; it needs the actual test/check result as evidence.
3. Some criteria have a documented escape valve **within the ticket itself** (e.g. "X, unless Y,
   in which case Z is acceptable instead") - when that valve applies, mark the literal clause
   `not-applicable` with the reasoning, not `not-satisfied` - see `FIXTURE.md`'s AIW-93 example
   for a real case of exactly this.
4. Some criteria are inherently about documentation/design quality (e.g. "a procedure is
   documented," "conventions are explicit") rather than something a mechanical check confirms -
   mark these `not-verifiable` with a note on what a human should actually read to judge it,
   rather than rubber-stamping `satisfied` because a file with the right name exists.
5. If a criterion's wording is ambiguous, or two criteria on the same ticket contradict each
   other, report it explicitly rather than picking an interpretation and moving on.
6. Set the overall status: every criterion `satisfied` or `not-applicable` (with reasoning) is
   the only combination that means the ticket is actually done: any `not-satisfied` or
   unresolved `not-verifiable`/ambiguous criterion means it isn't, regardless of how much other
   work is finished.

## Output / result contract

A single `acceptanceCriteriaReport` object:

```json
{
  "criteria": [
    {
      "text": "...",
      "status": "satisfied",
      "evidence": "...",
      "gap": null
    }
  ],
  "overallStatus": "complete",
  "ambiguousCriteria": []
}
```

`status` is one of `satisfied`, `not-satisfied`, `not-verifiable`, `not-applicable`. `gap` is
`null` when `status` is `satisfied` or `not-applicable`, and a concrete description of what's
missing otherwise. `overallStatus` is `complete` only when every criterion is `satisfied` or
`not-applicable` with clear reasoning; `incomplete` otherwise. `ambiguousCriteria` lists
anything Step 5 surfaced, always present even when empty.

## Stop conditions

- A criterion's wording is genuinely ambiguous or contradicts another criterion on the same
  ticket, in a way this skill cannot resolve on its own - report it in `ambiguousCriteria`, do
  not guess an interpretation and mark it satisfied or not based on the guess.

## Allowed action categories

- `read-only` - reads `implementationContext`/`verificationReport` and produces a report; never
  re-runs a check itself (that would duplicate `implementation-verification`'s job) and never
  edits anything to make a criterion true.

## Authority

`implementationContext.acceptanceCriteria` is the literal, authoritative list of what "done"
means for this ticket - criteria cannot be silently dropped or rewritten by this skill or by
whatever implemented the ticket, per this ticket's own explicit rule. Where a criterion's own
text provides its own escape valve (see Step 3), that valve is honored exactly as written, not
extended to cover cases the ticket didn't actually name.
