---
name: repository-investigation
description: Inspects the current repository state relevant to a ticket before any planning or code edit happens - use this after jira-ticket-intake and before implementation-planning, for every ticket, even one that "seems obvious."
---

# Repository Investigation

## Purpose

Answer "what does the repository actually contain and do today, relevant to this ticket" -
`jira-ticket-intake` already established what's being *asked for*; this skill establishes
what's *true right now*, so planning never proceeds on a stale or guessed mental model of the
codebase.

## When to use

After `jira-ticket-intake`, before `implementation-planning` - for every ticket, including ones
that look simple. A ticket referencing a class, file, or behavior by name is a claim, not a
fact, until this skill actually looks.

## Inputs

- `implementationContext` (object, required): `jira-ticket-intake`'s output, or - for standalone
  use - any object with at least `summary`/`description` text naming what to investigate.

## Preconditions

- The repository is checked out locally and readable.
- `implementationContext` names or clearly implies what to look for. If a ticket is so vague
  that nothing concrete can be searched for, that itself is worth reporting back rather than
  investigating at random.

## Steps

1. From `implementationContext`, extract concrete anchors to search for: package/class/file
   names mentioned literally, and the behavior/feature area implied by the summary and
   description even when no literal name is given.
2. Locate the files/classes/components those anchors actually point to today - search by name
   first; if a literal name isn't found, search by the behavior it's supposed to relate to
   before concluding it doesn't exist (a class may have been renamed or moved, not removed -
   see the renamed-file fixture below for a real example of exactly this in this repo's own
   history).
3. Read neighboring implementations, their tests, and any established convention (an existing
   pattern for the same kind of problem elsewhere in the codebase) - not just the one file the
   ticket happens to name.
4. Note the architecture boundary the change would sit in (e.g. `core.*` project-type-agnostic
   vs. `projecttype.website.*`; `backend/` vs `frontend/`) and any existing abstraction that
   should be reused rather than duplicated.
5. Classify every claim in the output as either a **verified fact** (you read the file/test
   yourself, this run) or an **assumption** (inferred, not directly read) - never blur the two.
6. Record unresolved questions or risks a human/planner should know about before implementation
   starts, even if they don't block investigation itself from finishing.

## Output / result contract

A single `repositoryContext` object:

```json
{
  "inspectedFiles": [
    { "path": "backend/src/main/java/ai/architech/backend/core/error/ErrorCode.java", "relevantBecause": "..." }
  ],
  "existingConventions": ["..."],
  "architectureBoundaries": ["..."],
  "verifiedFacts": ["..."],
  "assumptions": ["..."],
  "notInspected": ["..."],
  "unresolvedQuestions": ["..."]
}
```

`inspectedFiles` lists only files actually opened and read this run - never a file the skill
merely knows the *name* of from the ticket or from general familiarity. `notInspected` is the
explicit complement: things the ticket references or implies that were deliberately not opened
(out of scope, or genuinely irrelevant) - stated plainly rather than left to guesswork about
what "counts" as investigated. Every array is present even when empty.

## Stop conditions

- A file/class/package the ticket explicitly depends on does not exist anywhere in the current
  repository (searched by name and by behavior, not just a literal string match), and its
  absence changes which implementation approach is viable - report this, don't invent a
  plausible-sounding location for it.
- Multiple files could plausibly be "the" place to change, and picking wrong would materially
  change the implementation - report the ambiguity as an unresolved question rather than
  silently picking one.

## Allowed action categories

- `read-only` - this skill never edits, creates, or deletes a file. "Do not modify files during
  the investigation phase" is this ticket's own explicit behavioral rule, not just a default -
  a finding that something needs fixing goes in `unresolvedQuestions`, not into an edit made
  here.

## Authority

This skill's own reading of the repository is authoritative over what a ticket *claims* exists -
per its own behavioral rule, never speculate about a file's contents or behavior when those
facts would affect the implementation and the file wasn't actually opened this run.
Cross-cutting technical debt discovered along the way does not broaden this ticket's scope by
itself - note it in `unresolvedQuestions`, don't fix it here.
