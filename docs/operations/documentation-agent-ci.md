# Documentation Agent CI integration (AIW-203)

How the Website Documentation Agent V1 epic's (AIW-186..206) schema/fixture/registry checks are
verified in CI - and why no new CI infrastructure was needed to do it.

## The pattern: plain JUnit tests under the existing `Backend tests` gate

Every M1-M4 agent (Requirements, Designer, Developer, Website QA) verifies its own frozen
package's schema/fixture/registry conformance the same way: ordinary JUnit test classes
(`Frozen*AgentSpecIT`, `*SchemaRegistryIT`, `RuleLoaderIT`, `SkillLoaderIT`, `*ProfileLoaderIT`,
...) under `backend/src/test/java`, executed by the plain `./mvnw verify` invocation
`backend-ci.yml`'s `Backend tests` job already runs on every PR (see
[`ci-quality-gate-policy.md`](ci-quality-gate-policy.md)'s pull request gate table - `Backend
tests` and `Backend integration tests against real Postgres` are both already required, existing
checks). There is no separate, per-agent CI workflow or job anywhere in this repo, and no
standalone offline-validation script (the raw Documentation package's own
`tools/validate_contract_package.py` is a local author-side smoke check, never adopted into this
platform's actual CI - the same is true for every other agent's own frozen-package tooling).

The Documentation Agent follows this identical pattern, ticket by ticket, as its own package
integration/loading/validation code landed (AIW-187 through AIW-202) - no CI wiring work was
deferred or missing at any point; each ticket's own test classes were already exercised by the
existing `Backend tests` job the moment they were merged.

**Confirmed directly against a real CI run**, not just inferred from the pattern: the `Backend
tests` job's own log for PR #190 (the most recently merged Documentation ticket at the time of
writing, `gh run view <run-id> --log`) shows 123 separate `Tests run:` lines under
`ai.architech.backend.core.documentation.*`, spanning every sub-package this epic has built:

- `core.documentation.profiles` - `DocumentationProfileLoaderIT`/`...UnitTests`,
  `InvalidDocumentationProfileExceptionTests`.
- `core.documentation.policy` - `DocumentationPolicyLoaderIT`/`...UnitTests`,
  `DocumentationWorkflowPolicyLoaderIT`/`...UnitTests`, `InvalidDocumentationPolicyExceptionTests`.
- `core.documentation.locale` - `LocaleRegistryLoaderIT`/`...UnitTests`,
  `TerminologyRegistryLoaderIT`/`...UnitTests`, `InvalidLocaleRegistryExceptionTests`.
- `core.documentation.errors` - `DocumentationErrorRegistryLoaderIT`/`...UnitTests`,
  `InvalidDocumentationErrorRegistryExceptionTests`.
- `core.documentation.claimtypes` - the `claim-types.yaml` registry loader tests (AIW-196).
- `core.documentation.context` - `DocumentationContextAssemblerIT`, `DocumentationSecretScannerTests`,
  `DocumentationAudienceMinimizerTests`, `DocumentationFindingDisclosureEvaluatorIT`,
  `DocumentationAuthorityAdapterIT`.
- `core.documentation.reports` - `DocumentationDeterministicReportGeneratorIT`.
- `core.documentation.generation` - `DocumentationGenerationRunnerIT`.
- `core.documentation.orchestration` - `DocumentationGenerationOrchestratorIT`.
- `core.documentation.canonical` - `DocumentationCanonicalPackagePersisterIT`.
- `core.documentation.rendering` - `DocumentationMarkdownRendererIT`.
- `core.documentation.triggers` - `DocumentationWorkflowTriggerEvaluatorTests`,
  `DocumentationTriggerServiceIT`.
- `core.validation` (schema/identity/structure/deterministic/factual-consistency validators) -
  `DocumentationCandidateStructureValidatorIT`, `DocumentationCandidateDeterministicValidatorIT`,
  `DocumentationFactualConsistencyValidatorTests`, `DocumentationContextPreflightValidatorIT`.
- `FrozenDocumentationAgentSpecIT` and `DocumentationSchemaRegistryIT` (`core.agent`/
  `core.validation`) - the same "is the frozen package internally consistent" proof
  `FrozenWebsiteQaAgentSpecIT`/`WebsiteQaSchemaRegistryIT` already established for M4.

All 123 passed on that run, alongside every other backend test, under the one existing coverage
gate (`backend/pom.xml`'s `jacoco-maven-plugin` `check` execution, `ci-quality-gate-policy.md`'s
93%/80% line/branch floor) - Documentation's own code is counted in that same floor, not exempted
or measured separately.

## What this means for future Documentation tickets

Any new Documentation Agent test class (AIW-204's evaluation-gate fixtures, AIW-205's end-to-end
proof, or any future ticket) needs **no CI changes** to start running in the PR gate - placing it
under `backend/src/test/java` is sufficient, exactly like every prior ticket in this epic. The one
exception, per [[architech_cost_conscious_testing]] and this epic's own established discipline
(AIW-194/198/199/202 all state this explicitly in their own class javadoc): a test must mock
`AiGateway` (or whatever wraps it) rather than ever activating the `real-ai` Spring profile, since
CI has no budget/approval to make live model calls on every PR.
