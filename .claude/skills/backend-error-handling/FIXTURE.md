# Worked examples

Scenarios 1-4 are not hypothetical - they are this skill's own required patterns
("not-found, validation, conflict and external-service failure"), each matched to a real,
currently-shipped call site in this codebase, quoted as it actually exists today. Scenario 5 is
illustrative (a stop condition, not a pattern this codebase has actually needed yet).

## 1. Not-found (real: `RequirementsAnalysisController.start()`)

**errorScenario:** "The request starts a requirements analysis for a project id that doesn't
exist."

**Step 1 inspection** confirms `ErrorCode.PROJECT_NOT_FOUND` already exists
(`HttpStatus.NOT_FOUND`) and is already used for exactly this in
`RequirementsAnalysisController`:

```java
if (!projectRepository.existsById(projectId)) {
    throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
}
```

```json
{
  "errorScenario": "Start a requirements analysis for a project id that doesn't exist.",
  "pattern": "not-found",
  "errorCode": { "reused": true, "name": "PROJECT_NOT_FOUND", "httpStatus": "NOT_FOUND", "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": "No project with id <projectId>",
  "callSite": "RequirementsAnalysisController.start()",
  "testsToAddOrAdjust": ["Already covered - see RequirementsAnalysisControllerIT's existing not-found case."],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": true, "correlationIdHandledAutomatically": true }
}
```

## 2. Validation / precondition (real: `ProjectController.create()`)

**errorScenario:** "A project is created with a `projectType` this platform doesn't support yet."

```java
private static final Set<String> SUPPORTED_PROJECT_TYPES = Set.of("website");
...
if (!SUPPORTED_PROJECT_TYPES.contains(request.projectType())) {
    throw new ApplicationException(
            ErrorCode.UNSUPPORTED_PROJECT_TYPE, "Unsupported project type: " + request.projectType());
}
```

```json
{
  "errorScenario": "Create a project with a projectType not in SUPPORTED_PROJECT_TYPES.",
  "pattern": "validation",
  "errorCode": { "reused": true, "name": "UNSUPPORTED_PROJECT_TYPE", "httpStatus": "BAD_REQUEST", "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": "Unsupported project type: <requested type>",
  "callSite": "ProjectController.create()",
  "testsToAddOrAdjust": ["Already covered - existing ProjectControllerIT case for an unsupported type."],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": true, "correlationIdHandledAutomatically": true }
}
```

Note the message safely echoes the caller's own input (`request.projectType()`) back to them -
that's fine (it's not internal detail, the client already knows what it sent), distinct from
echoing something from *inside* the system (a stack trace, a driver error) which step 4 forbids.

## 3. Conflict (real: `RequirementsAnalysisController.start()`, same method as #1)

**errorScenario:** "A second requirements analysis is requested for a project that already has
one running."

```java
if (agentExecutionRepository.existsByProjectIdAndStatus(projectId, AgentExecutionStatus.RUNNING)) {
    throw new ApplicationException(
            ErrorCode.REQUIREMENTS_ANALYSIS_ALREADY_RUNNING,
            "A requirements analysis is already running for this project");
}
```

```json
{
  "errorScenario": "A second requirements analysis is requested while one is already running for the same project.",
  "pattern": "conflict",
  "errorCode": { "reused": true, "name": "REQUIREMENTS_ANALYSIS_ALREADY_RUNNING", "httpStatus": "CONFLICT", "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": "A requirements analysis is already running for this project",
  "callSite": "RequirementsAnalysisController.start()",
  "testsToAddOrAdjust": ["Already covered - existing RequirementsAnalysisControllerIT case for a concurrent run."],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": true, "correlationIdHandledAutomatically": true }
}
```

This is the concrete case that shows *why* conflict is distinct from validation, per step 2: the
request itself (`POST /projects/{id}/requirements-analysis`) is perfectly valid on its own; it
only fails because of *other current state* (an already-running execution) - a 409, not a 400.

## 4. External-service failure (real: `RequirementsAnalysisController.start()`, same method)

**errorScenario:** "The AI provider/runtime exhausts its retry budget while generating the
requirements analysis."

```java
try {
    result = requirementsAnalysisRunner.run(projectId);
} catch (RetryBudgetExhaustedException e) {
    // Deliberately a hand-written, static message - never e.getMessage() or e.getCause() -
    // so nothing from the model/runtime layer can ever reach the client.
    throw new ApplicationException(
            ErrorCode.MODEL_RUNTIME_FAILURE,
            "Requirements analysis could not run: the model/runtime failed on every permitted attempt. No candidate output was produced.");
}
```

```json
{
  "errorScenario": "The AI provider/runtime exhausts its retry budget generating the analysis.",
  "pattern": "external-service-failure",
  "errorCode": { "reused": true, "name": "MODEL_RUNTIME_FAILURE", "httpStatus": "BAD_GATEWAY", "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": "Requirements analysis could not run: the model/runtime failed on every permitted attempt. No candidate output was produced.",
  "callSite": "RequirementsAnalysisController.start() (catching RetryBudgetExhaustedException)",
  "testsToAddOrAdjust": ["Already covered - RequirementsAnalysisControllerRuntimeFailureTests."],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": true, "correlationIdHandledAutomatically": true }
}
```

This is the real call site step 2 and step 4 both point to for "never `e.getMessage()`/
`e.getCause()` from a lower layer" - `RetryBudgetExhaustedException`'s own message is discarded
entirely in favor of a hand-written one, specifically because once a real `AiProvider` is wired
in, that lower layer could carry provider-internal error detail this platform must never forward
to a client.

## 5. Stop condition - touching shared infrastructure (illustrative)

**errorScenario:** "We want error responses to also include a `retryable: boolean` field so
frontend clients know whether to automatically retry."

**Why this stops rather than proceeding:** this isn't a new `ErrorCode` and call site - it's a
change to `ErrorResponse`'s own shape (`core/error/ErrorResponse.java`), which every existing
error response already relies on being exactly `(errorCode, message, correlationId, timestamp,
path)`. That's platform error-architecture work, not an application of the existing pattern to
one new business case - exactly this skill's own stop condition ("the change seems to require
touching the shared infrastructure itself").

```json
{
  "errorScenario": "Add a retryable field to every error response.",
  "pattern": "genuinely-new",
  "errorCode": { "reused": false, "name": null, "httpStatus": null, "newConstantNeeded": false },
  "dedicatedExceptionJustification": null,
  "message": null,
  "callSite": null,
  "testsToAddOrAdjust": [],
  "safetyCheck": { "noStackTraceOrInternalsInMessage": null, "correlationIdHandledAutomatically": null },
  "note": "Stopped: this requires changing ErrorResponse's own shape, shared by every existing error case - out of this skill's scope. Report back rather than modifying shared error infrastructure as a side effect of one request."
}
```
