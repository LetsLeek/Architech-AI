---
name: implementation-planning
description: Turns validated Jira context and repository investigation results into a bounded implementation plan before any code change begins - use this after repository-investigation and before touching any file.
---

# Implementation Planning

## Purpose

Turn "what's being asked for" (`jira-ticket-intake`) plus "what's actually there"
(`repository-investigation`) into a concrete, bounded plan - specific enough to guide
implementation, without prescribing fabricated code details or inventing architecture
`repository-investigation` never confirmed.

## When to use

After `repository-investigation`, before any file is created or edited. Re-run (or explicitly
note a deviation - see Steps 6) if implementation reveals a material fact the plan didn't
account for, rather than pushing on with a plan that's now known to be wrong.

## Inputs

- `implementationContext` (object, required): `jira-ticket-intake`'s output.
- `repositoryContext` (object, required): `repository-investigation`'s output, for the same
  ticket.

## Preconditions

- `repositoryContext` was produced for the same ticket as `implementationContext`.
- `repositoryContext` did not itself stop with an unresolved blocker - planning cannot safely
  build on an investigation that never completed.

## Steps

1. Restate the goal and scope in one or two sentences, directly from
   `implementationContext.summary`/`acceptanceCriteria` - not reworded into something broader
   or narrower than the ticket actually asked for.
2. List files/modules likely to change, **using only `repositoryContext.inspectedFiles` and
   `verifiedFacts`** - never a path that wasn't actually confirmed to exist. If the right file
   genuinely isn't clear from the investigation, that's a gap in the investigation, not
   something to paper over here by guessing.
3. List any genuinely new files required, and why an existing file/abstraction
   (`repositoryContext.existingConventions`) doesn't already cover the need - prefer the
   smallest change that satisfies the acceptance criteria and existing architecture over a new
   parallel abstraction.
4. Note data/schema/configuration impacts (a new Flyway migration, a new config key, a new env
   var) and what test changes they imply.
5. Note test changes required generally - which existing test files need updates, which new
   ones are needed, and at what level (unit/integration/E2E) given this repo's own established
   split (`backend/`: Surefire `*Tests.java` vs. Failsafe `*IT.java`; `e2e/`: Playwright).
6. Note risks, dependencies (including anything from `repositoryContext.unresolvedQuestions`
   that the plan can't itself resolve), and migration considerations (backward compatibility,
   rollout order, whether existing data needs a backfill).
7. State out-of-scope items explicitly - anything `repositoryContext` surfaced as adjacent
   technical debt or a tempting related improvement that this ticket does not need to touch.
8. Set `flags` for anything destructive (data loss, irreversible schema change), a public API
   change, or security-sensitive - a flag doesn't block the plan from existing, it makes sure a
   human reviewing it can't miss that this needs closer attention before approval.

## Output / result contract

A single `plan` object:

```json
{
  "goal": "...",
  "scope": "...",
  "filesToChange": [
    { "path": "...", "changeSummary": "...", "groundedIn": "repositoryContext.inspectedFiles[...]" }
  ],
  "newFilesRequired": [{ "path": "...", "reason": "..." }],
  "dataSchemaConfigImpacts": ["..."],
  "testChanges": ["..."],
  "risks": ["..."],
  "dependencies": ["..."],
  "migrationConsiderations": ["..."],
  "outOfScope": ["..."],
  "flags": ["..."]
}
```

Every array is present even when empty (`flags: []` is the normal, common case - most tickets
touch nothing destructive/public-API/security-sensitive, and an empty list says that plainly
rather than omitting the field).

## Stop conditions

- `repositoryContext` reports a stop condition of its own that this plan can't route around
  without guessing - inherit the block, don't attempt to plan past it.
- No plan can be constructed that stays grounded in `repositoryContext.verifiedFacts` - every
  candidate approach requires inventing a file path or architecture repository-investigation
  never actually confirmed.

## Allowed action categories

- `read-only` - planning reasons over its two inputs and produces a plan object; it never
  commits, pushes, or mutates Jira, per this ticket's own explicit rule. A plan that recommends
  an `external-visible` or `destructive` action later still only *recommends* it via `flags` -
  performing it is a different skill/step, gated the normal way.

## Authority

`repositoryContext.verifiedFacts` is authoritative over anything this plan might otherwise
assume - a plan that contradicts what was actually verified is wrong by definition, not a
judgment call. Material deviations discovered during implementation invalidate the plan; note
the deviation and the reason rather than silently implementing something different from what
was planned.
