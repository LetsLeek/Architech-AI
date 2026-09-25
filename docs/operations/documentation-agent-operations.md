# Documentation Agent V1 operations (AIW-206)

Rollout prerequisites, known limitations, and an engineering handoff map for the Website
Documentation Agent V1 epic (AIW-186..205) - the fifth and final M1-M5 agent, generating
audience-specific handover documentation (`CUSTOMER_HANDOVER`/`TECHNICAL_HANDOVER`) from the
already-completed Requirements → Designer → Developer → QA pipeline's own artifacts. Companion
docs: [`documentation-agent-ci.md`](documentation-agent-ci.md) (how its tests run in CI) and
[`documentation-agent-evaluation-gates.md`](documentation-agent-evaluation-gates.md) (the
real-model release gates, not run by default).

## What exists today

Everything from a frozen candidate + QA `PASS`/`HOLD` result through to a persisted, immutable,
revisioned canonical package and a rendered Markdown export - proven end-to-end,
fixture-driven, in `DocumentationAgentV1EndToEndIT` (AIW-205). Concretely: preflight eligibility →
context assembly (with pre-generation secret redaction and audience minimization) → deterministic
report generation → bounded model-invocation retries → schema/identity/structure validation →
post-generation secret scan → deterministic claim-authority/keys/domains/disclosure/lifecycle
validation → the one semantic (model-backed) factual-consistency check → atomic, idempotent,
linearly-revisioned canonical persistence → Markdown rendering → workflow-trigger dispatch (both
the automatic `FULL_RELEASE_QA_FINALIZED`→Technical trigger and on-demand generation for either
profile).

## Rollout prerequisites - what must be true before this runs for real

1. **A real Anthropic API key configured and the `real-ai` Spring profile active.** Every model
   call in this epic (`documentation-reasoning` for generation, AIW-194;
   `documentation-factual-consistency` for the semantic check, AIW-198) defaults to the platform's
   `mock` provider (`application.yml`) - nothing here calls a real model until that profile is
   deliberately switched on, the same activation this platform already requires for M1-M4.
2. **Cost awareness: up to 6 real model calls per triggered generation in the worst case**, not
   just one. `DocumentationGenerationOrchestrator` (AIW-199) allows up to `maxGenerationAttempts`
   (3) full generation attempts, and for each deterministically-sound candidate, up to
   `maxSemanticEvaluatorExecutionsPerUnchangedCandidate` (2) semantic-evaluation attempts against
   it - 3 generation calls + up to 2 evaluation calls per successful candidate in the worst realistic
   case. Budget accordingly before enabling this against real traffic; see
   [`documentation-agent-evaluation-gates.md`](documentation-agent-evaluation-gates.md) for the
   *separate*, deliberately-excluded-from-CI real-model release gate, which adds its own real cost
   only when explicitly run (`./mvnw verify -Preal-model-eval`).
3. **No real caller exists yet.** There is no HTTP controller, no event-bus listener, and no
   scheduled job anywhere in this codebase that invokes `DocumentationTriggerService`
   automatically - every ticket in this epic deliberately stopped at "build the classifier/service,
   let a future ticket wire in the real caller" (the same pattern M1-M4's own early tickets used).
   Whoever wires this in for real needs to call either:
   - `DocumentationTriggerService.dispatchAutomaticTrigger(...)` when a real `FULL_RELEASE`
     `QaResult` reaches `PASS` for a release-path candidate (the only automatic trigger with a real
     data source today), or
   - `DocumentationTriggerService.generateOnDemand(...)` directly, for either profile, whenever a
     human/API caller explicitly requests a handover document.
4. **`SCOPED_APPROVAL_RECORDED` and `DEPLOYMENT_RECORDED` cannot fire from real platform data
   today.** Both presuppose an `ApprovalRecord`/`DeploymentRecord` type that does not exist
   anywhere in this codebase (verified repeatedly across AIW-189 through AIW-202,
   `grep -rl "ApprovalRecord\|DeploymentRecord" backend/src/main/java` → empty). Customer Handover
   is reachable today only via on-demand generation, never via its own named automatic trigger,
   until one of those two types gets built by a future, currently-unticketed piece of work.

## Known limitations - consolidated from every ticket's own documented gap

Collected here so a future engineer doesn't have to re-read 20 PR descriptions to find them:

- **Bounded fact catalog, not exhaustive extraction** (AIW-190): only 5 verified fact types are
  extracted into a context's `resolvedFacts` (business name, opening-hours count, per-binding
  functional-binding state, QA gate, implementation summary). Real, schema-supported upstream
  content (selected pages, actual routes, integration-contract terms, per-requirement text) is not
  extracted - deliberately, to avoid fabricating a mapping without verified knowledge of the
  upstream content schema's real shape.
- **No route/URL mapping data anywhere in this codebase** (AIW-193/AIW-201): the
  `implementation-manifest` report's `routes` field, and any route-aware rendering, is always
  empty - `WebsiteImplementationCandidate.implementationAnchors` only ever carries repository file
  paths, never a URL route.
- **`integration-reference-report`'s `purpose` field is synthesized, not curated** (AIW-193): a
  generic `"Integration via " + interfaceName` string, since no dedicated business-purpose field
  exists on the safe integration contract view.
- **No cross-candidate remediation-lineage tracking** (AIW-193): `qa-finding-register`'s
  `currentAssessment` is always `"CURRENT"` - `PERSISTS`/`CHANGED` would require comparing a
  finding against a prior candidate's own findings, a real, separate, unticketed mechanism.
- **No conflict-detection logic** (AIW-190/192/199): `documentation-context`'s `contextIssues`
  array is always empty; `DocumentationContextAssembler` accepts it as an external parameter for
  exactly this reason.
- **No correction-feedback loop on a regenerated attempt** (AIW-199): a retried generation attempt
  calls the model fresh with the same context, no prior validation issues injected into the
  prompt - the same gap `RequirementsAnalysisRunner` already documents for M1, unsolved for any
  agent in this codebase yet.
- **Markdown rendering only** (AIW-201): `HTML`/`PDF` are real, schema-permitted `format` values
  with no renderer built - PDF needs a new library dependency (a separate decision), HTML is a
  genuinely different templating concern.
- **No persisted `DocumentationRun` audit trail** (AIW-199): the orchestrator's typed outcome
  (`DocumentationGenerationOutcome`) is in-memory only. A future audit/observability need would
  require adding real persistence for it, informed by whatever real caller/UI actually consumes it
  first (deliberately not built ahead of that need).
- **`materialImpactFactKeys` always empty** (AIW-192): `findingDisclosureView` entries never link
  to a specific safe fact for the disclosed finding's material impact - no per-finding fact exists
  in the bounded catalog (above) to link to.
- **The real-model evaluation gates are built but have never been run** (AIW-204): a deliberate,
  pending action for whoever owns the next real release/config/model change - see
  [`documentation-agent-evaluation-gates.md`](documentation-agent-evaluation-gates.md).

None of these are silent gaps - every one is documented in its own ticket's class javadoc and PR
description at the point it was deliberately deferred, per this epic's own established practice of
stating a limitation plainly rather than working around it with unverified logic.

## Engineering handoff map

Package structure under `ai.architech.backend.core.documentation`:

| Package | Built in | What it does |
|---|---|---|
| `profiles` | AIW-188 | Loads `CUSTOMER_HANDOVER@1.0.0`/`TECHNICAL_HANDOVER@1.0.0` |
| `policy` | AIW-188 | `DOCUMENTATION_POLICY`/`DOCUMENTATION_WORKFLOW_POLICY` |
| `locale` | AIW-188 | Active locales + `de-AT`/`en-GB` terminology |
| `errors` | AIW-188 | The 39-code error registry |
| `claimtypes` | AIW-196 | `claim-types.yaml`'s domain-minimum registry |
| `context` | AIW-189/190/191/192/197 | `DocumentationAuthorityAdapter`, `DocumentationContextAssembler`, `DocumentationSecretScanner`, `DocumentationAudienceMinimizer`, `DocumentationFindingDisclosureEvaluator` |
| `reports` | AIW-193 | `DocumentationDeterministicReportGenerator` (the 5 Core-owned reports) |
| `generation` | AIW-194 | `DocumentationGenerationRunner` (the one model-invocation call) |
| `orchestration` | AIW-199 | `DocumentationGenerationOrchestrator` (the full bounded-retry pipeline) |
| `canonical` | AIW-200 | `DocumentationLine`/`DocumentationPackageVersion`/`DocumentationCanonicalPackagePersister` |
| `rendering` | AIW-201 | `DocumentationMarkdownRenderer` |
| `triggers` | AIW-202 | `DocumentationWorkflowTriggerEvaluator`/`DocumentationTriggerService` - **the actual entry point** for any future controller/listener |

Deterministic and semantic candidate validators live in the shared `core.validation` package
alongside the Developer/QA agents' own validators, not under `core.documentation` -
`DocumentationCandidateStructureValidator` (AIW-195), `DocumentationCandidateDeterministicValidator`
(AIW-196), `DocumentationContextPreflightValidator` (AIW-189), `DocumentationFactualConsistencyValidator`
(AIW-198), `DocumentationSchemaRegistry` (AIW-187) - matching this codebase's existing convention of
keeping every `*Validator`/`*SchemaRegistry` class in one shared location regardless of which agent
it belongs to.

## Rollout checklist

1. Configure a real Anthropic API key and activate the `real-ai` Spring profile in the target
   environment (see [`api-authentication.md`](api-authentication.md) for the shared platform
   authentication gate every `/api/**` call already requires, unrelated but adjacent).
2. Decide and build the real caller: a controller endpoint for on-demand generation, and/or a
   listener wired to wherever `QAPolicyAggregator`'s own gate-`PASS` outcome becomes durably
   observable, calling `DocumentationTriggerService` (see prerequisite 3 above).
3. Run the real-model evaluation gates at least once before the first real release
   (`./mvnw verify -Preal-model-eval`, [`documentation-agent-evaluation-gates.md`](documentation-agent-evaluation-gates.md))
   to confirm the actual configured model behaves acceptably against both the generator and
   semantic-validator fixtures - not required by any automated gate, but strongly recommended
   before the first live traffic per `INTEGRATION_HANDOFF.md`'s own "real-model tests required for
   release/config/model changes."
4. Monitor real cost from day one given prerequisite 2's worst-case 6-calls-per-generation figure.
5. If `ApprovalRecord`/`DeploymentRecord` types get built by future work, revisit
   `DocumentationWorkflowTriggerEvaluator` to wire their real data sources into the
   `SCOPED_APPROVAL_RECORDED`/`DEPLOYMENT_RECORDED` triggers, which today only accept a caller's
   own external assertion.
