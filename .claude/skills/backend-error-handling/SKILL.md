---
name: backend-error-handling
description: Teaches the developer agent how to implement a new backend failure case consistently with this platform's structured error-handling architecture (AIW-59) - use this whenever implementation work needs to signal a business failure to an API client, not just log it.
---

# Backend Error Handling

## Purpose

Keep every new backend failure case consistent with the platform's existing structured
error-handling architecture (AIW-59: `ApplicationException` + `ErrorCode` +
`GlobalExceptionHandler` + `ErrorResponse` + `RequestCorrelationFilter`, all under
`backend/src/main/java/ai/architech/backend/core/error/`) instead of reinventing error handling
per feature - a new `RuntimeException` subclass per business case, a new
`@ExceptionHandler` branch, or a hand-rolled error body shape. This skill teaches the *decision
process* for a new failure case; it does not restate AIW-59's own architecture in full - read the
real classes (referenced by file below) rather than treating this skill as a copy of them.

## When to use

Whenever implementation work needs to turn a business failure into a response an API client can
act on - not for failures that are only ever logged and never surface to a client. Used during
`implementation-planning`/actual coding, not as an initial ticket-intake or review step.

## Inputs

- `errorScenario` (string, required): the new failure case in plain language - what went wrong,
  and from where in the request lifecycle (e.g. "the request references a project id that
  doesn't exist," "a second requirements analysis was requested while one is already running for
  the same project").
- `codeContext` (object, optional): already-known facts about the current error-handling module
  state (e.g. from `repository-investigation`) - treated as a hint to verify against the real
  files in step 1, never trusted without that check, per this skill's own first step.

## Preconditions

- The backend module exists and `core/error/` (`ApplicationException`, `ErrorCode`,
  `GlobalExceptionHandler`, `ErrorResponse`, `RequestCorrelationFilter`) is present - if a future
  project type or module genuinely lacks this infrastructure, this skill does not apply until it
  does.

## Steps

1. **Inspect before choosing a pattern.** Read `core/error/ErrorCode.java`'s existing constants
   and `GlobalExceptionHandler.java` before writing anything - never assume what's already there.
   This is not optional groundwork; it is this skill's own first, non-skippable step (mirrors
   AIW-104's own acceptance criteria requiring inspection before pattern choice).
2. **Classify the failure** against the four patterns this architecture already demonstrates
   (each grounded in a real, currently-shipped call site - see Examples):
   - **Not-found** - a referenced resource doesn't exist. Real example:
     `RequirementsAnalysisController.start()` throws
     `new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId)`
     when `!projectRepository.existsById(projectId)`.
   - **Validation / precondition** - the request or current state doesn't satisfy a business rule
     needed to proceed. Real examples: `ProjectController` throws `UNSUPPORTED_PROJECT_TYPE` for
     an unrecognized `request.projectType()`; `RequirementsAnalysisController` throws
     `PROJECT_HAS_NO_INPUT` when no customer input exists yet to analyze.
   - **Conflict** - the request is individually valid but clashes with current state. Real
     example: `RequirementsAnalysisController` throws `REQUIREMENTS_ANALYSIS_ALREADY_RUNNING`
     when `agentExecutionRepository.existsByProjectIdAndStatus(projectId, RUNNING)` - a second
     concurrent run for the same project is a conflict, not a validation failure or an
     unexpected error.
   - **External-service failure** - a dependency (an AI provider, in this codebase) failed in a
     way the caller needs to know isn't *their* fault. Real example:
     `RequirementsAnalysisController` catches `RetryBudgetExhaustedException` from
     `requirementsAnalysisRunner.run(projectId)` and re-throws
     `new ApplicationException(ErrorCode.MODEL_RUNTIME_FAILURE, "...")` with a **deliberately
     hand-written, static message** - never `e.getMessage()`/`e.getCause()` from the
     runtime/provider layer, so provider-internal detail can never reach the client (the same
     rule as step 4 below, already enforced at this real call site).
   - None of the four fit → this is the rare case a genuinely new pattern is needed; treat that
     as a stop condition (see below), not a reason to force-fit one of the four.
3. **Prefer a reusable `ErrorCode` over a new `ApplicationException` subclass.** A new business
   error is, by default, a new `ErrorCode` enum constant (name + `HttpStatus`) plus a
   `throw new ApplicationException(ErrorCode.X, "safe message")` at the failure site - never a
   new exception class, and never a new `@ExceptionHandler` branch in
   `GlobalExceptionHandler`. A dedicated exception type is only justified when the failure
   carries structured data `ApplicationException`'s `(errorCode, message[, cause])` shape
   genuinely cannot express - and that justification must be stated explicitly, not assumed.
4. **Write a message that is already safe to expose.** No stack traces, no secrets, no internal
   implementation detail (class names, SQL, provider error bodies) - the same rule
   `GlobalExceptionHandler.handleUnexpected` already enforces structurally for *unexpected*
   exceptions (a static `"An unexpected error occurred."`, the real exception logged server-side
   only), applied here by hand-written discipline for *expected* business errors, since
   `ApplicationException.getMessage()` is sent to the client verbatim.
5. **Do nothing for correlation IDs.** `RequestCorrelationFilter` and
   `GlobalExceptionHandler`'s `respond()` already attach the current request's correlation id to
   every `ErrorResponse` automatically, via `MDC` - a new error case never needs its own
   correlation handling; if a change seems to require touching `RequestCorrelationFilter` itself,
   stop and reconsider (see Stop conditions).
6. **Add or adjust tests.** Mirror the existing pattern in
   `GlobalExceptionHandlerTests` (plain unit tests, no Spring context, calling handler methods
   directly against a mocked `HttpServletRequest`) for handler-level behavior, and add a
   call-site-level test (e.g. a controller test asserting the right `ErrorCode`/HTTP status for
   the new condition) for the new throw site itself - a new error path without a new or adjusted
   test is incomplete, not merely under-tested.

## Output / result contract

A single `errorHandlingPlan` object:

```json
{
  "errorScenario": "...",
  "pattern": "not-found | validation | conflict | external-service-failure | genuinely-new",
  "errorCode": { "reused": true, "name": "PROJECT_NOT_FOUND", "httpStatus": "NOT_FOUND", "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": "No project with id <id>",
  "callSite": "RequirementsAnalysisController.start()",
  "testsToAddOrAdjust": ["Controller test: 404 + PROJECT_NOT_FOUND for a non-existent projectId"],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": true, "correlationIdHandledAutomatically": true }
}
```

`dedicatedExceptionJustification` is `null` unless step 3 concluded a new exception type is
actually justified, in which case it states the specific structured data `ApplicationException`
cannot carry - never a vague "it felt cleaner." `errorCode.newConstantNeeded` is `true` only when
step 1's inspection found no existing constant already fits.

## Stop conditions

- The failure doesn't fit any of the four established patterns, and no existing `ErrorCode`
  reasonably covers it - propose the new constant and pattern explicitly rather than
  force-fitting an ill-suited existing one.
- A change seems to require touching the shared infrastructure itself
  (`GlobalExceptionHandler`, `ErrorResponse`'s shape, `RequestCorrelationFilter`) rather than
  just adding a call site - that's a change to the platform's error architecture, not an
  application of it; report it rather than modifying shared infrastructure as a side effect of
  one feature's error case.
- Step 1's inspection wasn't actually performed, or was performed against stale/remembered
  context instead of the real current files - re-run it; never propose a plan built on an
  assumption about what `ErrorCode` already contains.

## Allowed action categories

- `read-only` - inspecting `core/error/` and existing call sites before proposing a plan.
- `local-write` - adding the `ErrorCode` constant (when genuinely needed), the
  `throw new ApplicationException(...)` call site, and the corresponding test(s). Never
  `local-git`/`external-visible` - staging/committing/pushing that work is a later skill's job
  (`safe-git-branch`/`human-review-publish`), not this one's.

## Authority

`core/error/`'s real, current source is authoritative over this skill's own restated examples
above - if the actual code has since changed (a new `ErrorCode` added, a pattern refined), the
code wins and step 1's inspection is what catches that drift, not memory of this document. AIW-59
is the canonical architecture this skill applies, never duplicates; where this skill and the real
AIW-59 code disagree, the code is right and this skill is stale.
