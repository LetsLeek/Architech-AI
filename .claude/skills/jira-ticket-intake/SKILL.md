---
name: jira-ticket-intake
description: Loads and normalizes the complete Jira work context for one issue key before any code change begins - use this first, for any ticket, before investigating the repository or planning an implementation.
---

# Jira Ticket Intake

## Purpose

Given a Jira issue key, gather everything needed to understand *what is being asked for* -
before looking at any code. This skill answers "what does the ticket say," never "what does the
repository actually do" - that is `repository-investigation`'s job (AIW-105), not this one.

## When to use

At the start of working any ticket, before branching, investigating the repository, or
planning an implementation. Re-run it if a ticket's Jira state changes materially mid-work
(e.g. AC edited, a blocking dependency completes) rather than relying on a stale first read.

## Inputs

- `issueKey` (string, required): a Jira issue key, e.g. `AIW-104`.

## Preconditions

- Jira MCP tools (`mcp__atlassian__*`) are available and authenticated for this session.
- `issueKey` matches this project's key pattern (`AIW-<number>`).

## Steps

1. Fetch the issue itself: summary, description, acceptance criteria (if the description has an
   "Acceptance Criteria" section, extract it separately - don't leave it buried in raw
   description text), issue type, status, priority, labels, fix version(s).
2. Fetch the parent Epic, if one exists (`fields.parent`) - key, summary, status. Absent is a
   normal, valid state (not every ticket has one) - never invent one.
3. Fetch issue links (`fields.issuelinks`) - for each, record its type (Blocks/is blocked
   by/Relates/etc.), direction, and the linked issue's key, summary, and status.
4. For every link where *this* ticket **is blocked by** another: check that linked issue's
   status.
   - If it's Done, fetch enough of it (summary, and its own description if the dependency looks
     like it materially shapes this ticket's implementation - e.g. "references the canonical
     backend error architecture from AIW-59") to include as real implementation context, not
     just a cross-reference.
   - If it's not Done, this is a real blocker - surface it plainly rather than proceeding as if
     it didn't matter (see Stop conditions).
5. Fetch comments, if any exist. Summarize substantive ones (a decision, a scope change, a
   clarification); skip purely mechanical ones (status-transition auto-comments) unless they
   carry real information.
6. Assemble the Output structure below. Do not add fields beyond what's listed, and do not
   invent values for anything Jira left empty - `null`/absent is the correct, honest
   representation of "this ticket has no parent Epic," not a reason to guess one.

## Output / result contract

A single `implementationContext` object:

```json
{
  "issueKey": "AIW-104",
  "summary": "...",
  "description": "...",
  "acceptanceCriteria": ["...", "..."],
  "issueType": "Story",
  "status": "To Do",
  "priority": "High",
  "labels": ["backend", "developer-agent", "error-handling", "skills"],
  "fixVersions": ["M2 - Designer Agent"],
  "parentEpic": { "key": "AIW-103", "summary": "...", "status": "To Do" },
  "links": [
    { "type": "Blocks", "direction": "is blocked by", "key": "AIW-59", "summary": "...", "status": "Done" }
  ],
  "completedDependencies": [
    { "key": "AIW-59", "summary": "...", "relevantContext": "..." }
  ],
  "comments": [{ "author": "...", "summary": "..." }],
  "source": "jira",
  "verificationNote": "Ticket claims only - not verified against the current repository state. Run repository-investigation before acting on any of this."
}
```

`acceptanceCriteria`, `labels`, `fixVersions`, `links`, `completedDependencies`, and `comments`
are always arrays (empty when there's nothing to report, never omitted or `null`).
`parentEpic` is `null`, not omitted, when there is none. The `source`/`verificationNote` pair is
always present, verbatim - it's what makes "ticket claims" and "verified repository facts"
explicitly separate, per this skill's own behavioral rule, rather than an implicit convention a
reader has to already know.

## Stop conditions

- `issueKey` does not resolve to an existing, accessible issue - report that plainly, do not
  fabricate a plausible-looking context.
- A "is blocked by" dependency is not Done and its absence materially affects how the target
  ticket should be implemented - report which dependency and why, do not proceed as if
  unblocked.
- The ticket's own summary, description, and acceptance criteria contradict each other on scope
  in a way that can't be resolved without a human decision - report the contradiction, do not
  pick one interpretation silently.
- A Jira tool call fails or access is denied - report the failure, do not substitute fabricated
  context to keep going.

## Allowed action categories

- `external-read` - reads Jira only. Never creates branches, commits, pushes, opens PRs, or
  mutates Jira (transitions, comments) - that is explicitly out of scope for this skill, per its
  own ticket's behavioral rules, regardless of what a later skill in the same session might do.

## Authority

Jira describes requested work; it is not authoritative proof of the current repository state.
The repository itself (verified by `repository-investigation`, not this skill) is authoritative
for what the code actually does today. Where this skill's output and the real repository
disagree, the repository wins - this skill's job is only to state clearly what was asked for.
