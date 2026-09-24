# Worked examples

## 1. Complete, dependency-linked ticket (real data: AIW-104)

**Input:** `{ "issueKey": "AIW-104" }`

**Output** (fetched from this project's own Jira on 2026-09-11 - a real ticket, not a
hypothetical one, chosen specifically because it has a parent Epic, labels, a fix version, and a
real "is blocked by" link to a completed dependency that materially shapes its implementation):

```json
{
  "issueKey": "AIW-104",
  "summary": "Create backend error-handling coding conventions skill",
  "description": "Teach the developer agent how to implement backend error behavior consistently with the platform's structured error-handling architecture. ...",
  "acceptanceCriteria": [
    "A versioned developer-agent skill/module documents the error-handling implementation workflow.",
    "The skill explicitly requires inspecting the current codebase before choosing an error pattern.",
    "New business errors prefer reusable exception categories + error codes unless a dedicated type is technically justified.",
    "The skill references, but does not duplicate, the canonical backend error architecture from AIW-59.",
    "Examples cover not-found, validation, conflict and external-service failure patterns where supported by the codebase.",
    "The skill is testable through representative implementation scenarios."
  ],
  "issueType": "Story",
  "status": "To Do",
  "priority": "High",
  "labels": ["backend", "developer-agent", "error-handling", "skills"],
  "fixVersions": ["M2 - Designer Agent"],
  "parentEpic": { "key": "AIW-103", "summary": "Internal Engineering Agent Workflow & Skills", "status": "To Do" },
  "links": [
    { "type": "Blocks", "direction": "is blocked by", "key": "AIW-59", "summary": "Implement structured backend error-handling architecture", "status": "Done" }
  ],
  "completedDependencies": [
    {
      "key": "AIW-59",
      "summary": "Implement structured backend error-handling architecture",
      "relevantContext": "Done. Introduced core.error (ErrorCode enum, ApplicationException, GlobalExceptionHandler, ErrorResponse, RequestCorrelationFilter). AIW-104's own AC explicitly requires referencing this rather than duplicating it."
    }
  ],
  "comments": [],
  "source": "jira",
  "verificationNote": "Ticket claims only - not verified against the current repository state. Run repository-investigation before acting on any of this."
}
```

## 2. Minimal ticket (illustrative)

**Input:** `{ "issueKey": "AIW-999" }` (illustrative key, not a real ticket)

**Output:**

```json
{
  "issueKey": "AIW-999",
  "summary": "Fix typo in health check response",
  "description": "The /actuator/health response has a typo in one status string.",
  "acceptanceCriteria": [],
  "issueType": "Task",
  "status": "To Do",
  "priority": "Low",
  "labels": [],
  "fixVersions": [],
  "parentEpic": null,
  "links": [],
  "completedDependencies": [],
  "comments": [],
  "source": "jira",
  "verificationNote": "Ticket claims only - not verified against the current repository state. Run repository-investigation before acting on any of this."
}
```

Nothing is invented to fill the empty fields - `acceptanceCriteria` has no explicit AC section
in the description, so it's an empty list, not a guess at what the AC "probably" says.

## 3. Blocked by an incomplete dependency (illustrative - stop condition)

**Input:** `{ "issueKey": "AIW-998" }` (illustrative key, not a real ticket)

The ticket is blocked by another issue that is not Done and materially affects it (e.g. it
needs a shared library the blocking ticket hasn't shipped yet).

**Result:** the skill does not return an `implementationContext` - it stops and reports:

```
Cannot safely produce implementation context for AIW-998: it is blocked by AIW-997
("Add shared retry-policy library"), currently in status "In Progress," and AIW-998's own
description says it depends on that library existing. Proceeding would require guessing an
interface that doesn't exist yet. Recommend resolving AIW-997 first, or confirming with a human
that a different, unblocked approach is acceptable.
```
