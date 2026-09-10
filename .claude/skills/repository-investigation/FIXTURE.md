# Worked examples

## 1. Known-file scenario (real data: investigating for AIW-104)

**Input:** `jira-ticket-intake`'s AIW-104 output (see that skill's own `FIXTURE.md`) - a ticket
whose AC explicitly says "references, but does not duplicate, the canonical backend error
architecture from AIW-59."

**Output** (from actually reading `backend/src/main/java/ai/architech/backend/core/error/` in
this repository on 2026-09-11 - real file contents, not invented):

```json
{
  "inspectedFiles": [
    { "path": "backend/src/main/java/ai/architech/backend/core/error/ErrorCode.java", "relevantBecause": "The stable, machine-readable identifier every business error maps to - AIW-104's skill must teach reusing this, not inventing a new exception type." },
    { "path": "backend/src/main/java/ai/architech/backend/core/error/ApplicationException.java", "relevantBecause": "The one exception type business code should throw - its own Javadoc explicitly documents replacing a prior one-subclass-per-error pattern." },
    { "path": "backend/src/main/java/ai/architech/backend/core/error/GlobalExceptionHandler.java", "relevantBecause": "Where ApplicationException/ErrorCode become an HTTP response - relevant boundary for what a new error code actually needs." }
  ],
  "existingConventions": [
    "Adding a new business error is adding an ErrorCode enum constant plus `throw new ApplicationException(code, message)` at the failure point - never a new exception class, per ApplicationException's own Javadoc.",
    "ErrorCode's HttpStatus is the single source of truth for the API status code - GlobalExceptionHandler has no per-error branching to keep in sync.",
    "ApplicationException's message is sent to the client verbatim - callers must only pass already-safe text (no stack traces, no secrets, no internal detail)."
  ],
  "architectureBoundaries": [
    "core.error is project-type-agnostic (ai.architech.backend.core) - correct location for a convention that applies to any error, not website-specific behavior."
  ],
  "verifiedFacts": [
    "ErrorCode currently has 8 constants (PROJECT_NOT_FOUND, EVIDENCE_SNAPSHOT_NOT_FOUND, UNSUPPORTED_PROJECT_TYPE, PROJECT_HAS_NO_INPUT, REQUIREMENTS_ANALYSIS_ALREADY_RUNNING, MODEL_RUNTIME_FAILURE, AI_BUDGET_HARD_LIMIT_EXCEEDED, INTERNAL_ERROR), each with an HttpStatus.",
    "ApplicationException's own Javadoc names two now-removed exception classes (EvidenceSnapshotNotFoundException, UnsupportedProjectTypeException) it replaced - real prior art for 'don't create one exception class per business case.'"
  ],
  "assumptions": [],
  "notInspected": [
    "ErrorResponse.java and RequestCorrelationFilter.java exist in the same package but weren't opened this run - not directly relevant to 'which exception type to throw,' AIW-104's actual scope."
  ],
  "unresolvedQuestions": [
    "AIW-104's AC asks for 'not-found, validation, conflict and external-service failure patterns where supported by the codebase' - PROJECT_NOT_FOUND/EVIDENCE_SNAPSHOT_NOT_FOUND cover not-found and MODEL_RUNTIME_FAILURE covers external-service, but no existing ErrorCode constant demonstrates a pure validation or conflict-only example beyond REQUIREMENTS_ANALYSIS_ALREADY_RUNNING (conflict) - worth flagging to whoever plans AIW-104's actual skill content, not something this investigation should resolve by picking an example itself."
  ]
}
```

## 2. Renamed-file scenario (real data: this repo's own git history)

**Input:** a hypothetical earlier-era reference to `BackendApplicationTests.java`.

**Output:** a literal search for that filename finds nothing at that path today - but before
concluding it doesn't exist, a behavior-based search (backend application context smoke test)
finds `backend/src/test/java/ai/architech/backend/BackendApplicationIT.java`, and `git log
--follow` on that path shows it really is the same file, renamed during AIW-91 (the
unit/integration test split - `*Tests.java` → `*IT.java` for every `@SpringBootTest` class).

```json
{
  "inspectedFiles": [
    { "path": "backend/src/test/java/ai/architech/backend/BackendApplicationIT.java", "relevantBecause": "Same file as the referenced BackendApplicationTests.java - renamed in AIW-91, confirmed via git log --follow." }
  ],
  "existingConventions": [
    "`*Tests.java` = plain unit test (Surefire); `*IT.java` = @SpringBootTest integration test (Failsafe) - established in AIW-91, documented in the root README."
  ],
  "architectureBoundaries": [],
  "verifiedFacts": [
    "BackendApplicationTests.java was renamed to BackendApplicationIT.java in commit b36c269 (AIW-91) - confirmed via git log --follow, not assumed from the naming convention alone."
  ],
  "assumptions": [],
  "notInspected": [],
  "unresolvedQuestions": []
}
```

This is exactly the case this skill's own instructions warn about: a literal name not existing
doesn't mean the thing it refers to is gone - it may have moved.

## 3. Missing-reference scenario (real data: the Designer Agent spec package)

**Input:** a hypothetical ticket instructing "update the Designer Agent's rule.yaml under
`project-types/website/agents/designer-agent/`."

**Output:** confirmed via `find project-types -iname "*designer*"` returning nothing - this
path, and the whole Designer Agent V1 spec package it would require, does not exist anywhere in
this repository as of 2026-09-11 (matches this project's own known M2 Pillar 4 blocker - the
frozen spec package hasn't been provided yet).

```json
{
  "inspectedFiles": [],
  "existingConventions": [
    "project-types/website/agents/requirements-agent/ is the analogous, already-frozen spec package for the Requirements Agent - the Designer Agent's own package, once provided, would likely mirror this shape (agent.yaml, AGENT.md, rules/, skills/)."
  ],
  "architectureBoundaries": [],
  "verifiedFacts": [
    "No file or directory matching 'designer' exists anywhere under project-types/ - confirmed by search, not inferred."
  ],
  "assumptions": [],
  "notInspected": [],
  "unresolvedQuestions": [
    "This ticket cannot be implemented against a spec package that doesn't exist - report as blocked (this skill's own stop condition), don't invent a plausible-looking rule.yaml to fill the gap."
  ]
}
```
