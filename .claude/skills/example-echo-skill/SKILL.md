---
name: example-echo-skill
description: Conformance fixture for AIW-114's developer-agent skill contract - not a real skill, proves the contract is checkable
---

# Example Echo Skill (conformance fixture)

## Purpose

A deliberately trivial placeholder skill that exists only to prove `docs/developer-agent/CONTRACT.md`'s
skill shape is real and checkable, not aspirational. Never invoke this for actual work - it does
nothing beyond restating its own input. Real developer-agent skills (AIW-60, 61, 104-112) each
implement this contract for an actual step of the ticket-execution workflow.

## When to use

Never, outside of running the conformance checker against it. If you're looking for the
Jira-ticket-intake, repo-investigation, or implementation-planning skill, those don't exist yet -
this fixture is what they'll be checked against once they do.

## Inputs

- `message` (string, required): arbitrary text to echo back.

## Preconditions

None - this skill has no real precondition, since it does no real work.

## Steps

1. Read the `message` input.
2. Restate it verbatim as the output, prefixed with `echo: `.

## Output / result contract

- `echoedMessage` (string): `echo: ` followed by the input `message`, unchanged otherwise.

## Stop conditions

- `message` is missing or empty - stop, nothing to echo.

## Allowed action categories

- `read-only` - this skill never writes, commits, or reaches any external system.

## Authority

None beyond this file - a fixture has no reviewer, no rule, and no human-approval boundary to
defer to, since it never does anything real enough to need one.
