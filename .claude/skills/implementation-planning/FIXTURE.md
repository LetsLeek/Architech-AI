# Worked examples

## 1. Normal ticket (real data: planning AIW-104)

**Input:** `jira-ticket-intake`'s AIW-104 output + `repository-investigation`'s AIW-104 output
(both real, see those skills' own `FIXTURE.md`).

**Output** - a genuine forward-looking plan (AIW-104 is still "To Do" as of 2026-09-11), grounded
only in what the investigation actually verified:

```json
{
  "goal": "Document a versioned developer-agent skill teaching consistent backend error handling, referencing (not duplicating) AIW-59's core.error architecture.",
  "scope": "A new .claude/skills/backend-error-handling-conventions/ skill (SKILL.md + skill.yaml, per AIW-114's contract) - documentation/instructions only, no application code changes.",
  "filesToChange": [],
  "newFilesRequired": [
    { "path": ".claude/skills/backend-error-handling-conventions/SKILL.md", "reason": "The skill itself, per AIW-114's contract." },
    { "path": ".claude/skills/backend-error-handling-conventions/skill.yaml", "reason": "Structured metadata companion, same contract." },
    { "path": ".claude/skills/backend-error-handling-conventions/FIXTURE.md", "reason": "AIW-104's own AC requires 'testable through representative implementation scenarios.'" }
  ],
  "dataSchemaConfigImpacts": [],
  "testChanges": [
    "No application test changes - conformance is via check_skill_conformance.py against the new skill directory, same as every other developer-agent skill."
  ],
  "risks": [
    "AIW-104's AC asks for validation and conflict-only examples beyond what ErrorCode currently has constants for (per repositoryContext's unresolvedQuestions) - the skill's fixtures may need to describe a not-yet-added ErrorCode constant as an illustrative example, clearly labeled as such, rather than only citing the 8 that exist today."
  ],
  "dependencies": ["AIW-59 (Done) - core.error package this skill documents and must not duplicate."],
  "migrationConsiderations": [],
  "outOfScope": [
    "Adding new ErrorCode constants or modifying core.error itself - this ticket documents the existing convention, it doesn't extend it."
  ],
  "flags": []
}
```

## 2. Cross-stack ticket (real data: AIW-130, verified against the actual merged commit)

**Input:** a `jira-ticket-intake`/`repository-investigation` pair for AIW-130 ("expose AI
provider/model/token/cost info on Requirements Analysis result").

**Output** - reconstructed against `repositoryContext.verifiedFacts` and cross-checked against
this repo's real commit `fc21bb7` (`git show --stat`) to confirm the plan's `filesToChange`
exactly matches what was actually touched, not approximated:

```json
{
  "goal": "Expose AgentExecution's existing provider/model/token/cost fields (already tracked since AIW-37) through the Requirements Analysis API response and frontend UI - currently inspectable only via direct DB access.",
  "scope": "Backend response DTO + its controller test, frontend API type + display component. No new persistence - AgentExecution already has every field needed.",
  "filesToChange": [
    { "path": "backend/src/main/java/ai/architech/backend/projecttype/website/RequirementsAnalysisResponse.java", "changeSummary": "Add provider/model/promptTokens/completionTokens/costUsd fields, sourced from AgentExecution's existing getters.", "groundedIn": "repositoryContext.inspectedFiles" },
    { "path": "backend/src/test/java/ai/architech/backend/projecttype/website/RequirementsAnalysisControllerIT.java", "changeSummary": "Assert the new fields appear (and stay null together for a mock-provider run).", "groundedIn": "repositoryContext.inspectedFiles" },
    { "path": "frontend/src/api/requirementsAnalysis.ts", "changeSummary": "Extend the result type with the same five fields.", "groundedIn": "repositoryContext.inspectedFiles" },
    { "path": "frontend/src/components/RequirementsAnalysisSection.tsx", "changeSummary": "Display provider/model/tokens/cost when present.", "groundedIn": "repositoryContext.inspectedFiles" }
  ],
  "newFilesRequired": [],
  "dataSchemaConfigImpacts": [],
  "testChanges": [
    "Backend: extend the existing controller integration test's assertions rather than adding a new test file - this is additive to an already-tested response shape.",
    "Frontend: no new test file needed at this scope; a future ticket could add component-level coverage for the new display, but this plan's scope is exposure, not new test infrastructure."
  ],
  "risks": [
    "Must not weaken the response's existing 'no provider credentials/internals leak through here' invariant - all five new fields are usage/cost data the user who ran the analysis already implicitly caused, not secrets."
  ],
  "dependencies": ["AIW-37 (Done) - AgentExecution already tracks all five fields; this ticket only exposes them, it doesn't add new tracking."],
  "migrationConsiderations": [],
  "outOfScope": [
    "Any new cost-tracking logic - CostCalculator/AgentExecution already compute and persist these values."
  ],
  "flags": []
}
```

This plan's `filesToChange` list is exactly the four files AIW-130's real merged commit
(`fc21bb7`) actually touched - confirmed via `git show --stat`, not coincidence. (The test
file's path above uses its current name, `...ControllerIT.java` - at `fc21bb7`'s own time it
was still `...ControllerTests.java`, renamed later by AIW-91; a repository-investigation run
today would find the current name, which is what a real plan should cite.)

## 3. Migration-sensitive ticket (illustrative - not a real ticket)

**Input:** a hypothetical ticket adding a `NOT NULL` column to an existing, populated table.

**Output** - the point of this fixture is the `flags` field, not a real schema:

```json
{
  "goal": "Add a required 'archivedAt' timestamp semantics to the existing projects table (illustrative).",
  "scope": "One new Flyway migration, plus the JPA entity/repository changes it implies.",
  "filesToChange": [
    { "path": "backend/src/main/java/.../Project.java", "changeSummary": "Add the new mapped field.", "groundedIn": "illustrative - not a real investigation" }
  ],
  "newFilesRequired": [
    { "path": "backend/src/main/resources/db/migration/V<next>__add_project_archived_at.sql", "reason": "Schema change - this repo's own convention is Flyway migrations only, never hand-editing the database (see root README)." }
  ],
  "dataSchemaConfigImpacts": [
    "Existing rows have no value for the new column - a NOT NULL constraint without a default would break every existing row; requires either a default value or a backfill step in the same migration."
  ],
  "testChanges": ["Integration test covering the migration actually applies cleanly against a populated table, not just an empty one."],
  "risks": ["Irreversible once applied to a real environment with real data - this repo has no rollback tooling for a bad migration today."],
  "dependencies": [],
  "migrationConsiderations": [
    "Backfill strategy must be decided before writing the migration, not discovered while writing it.",
    "Rollout order: migration must run before any code depending on the new column is deployed."
  ],
  "outOfScope": [],
  "flags": [
    "destructive-adjacent: a NOT NULL column added without a safe default/backfill can fail against existing data - human sign-off needed on the backfill approach before this plan is acted on, not just before the migration file is merged."
  ]
}
```
