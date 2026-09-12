# Website Developer Agent V1 — Frozen Handoff

Status: **FROZEN**

This document identifies the V1 contract surface for the Website Developer Agent. Implementation may choose class names, packages, persistence technology details, and DTO shapes that are not explicitly frozen, but must preserve the semantics of the files in this handoff.

This product-facing agent is unrelated to the internal engineering-agent workflow in AIW-103 (`docs/developer-agent/`).

## Frozen Website Files

- `project-types/website/agents/developer-agent/agent.yaml`
- `project-types/website/agents/developer-agent/AGENT.md`
- `project-types/website/agents/developer-agent/contracts/common.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/developer-execution-input.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/developer-agent-result.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/implementation-anchor.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/functional-binding.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/unresolved-issue.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/developer-blocker.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/website-implementation-candidate.v1.schema.json`
- `project-types/website/agents/developer-agent/contracts/website-design-proposal.v1.schema.json` (composes, does not copy, `design-proposal-set.schema.json#/$defs/proposal` - see that schema's own `$comment`)
- `project-types/website/agents/developer-agent/contracts/developer-safe-integration-contract-view.v1.schema.json` (placeholder pending AIW-144 - see that file's own `$comment`)
- `project-types/website/agents/developer-agent/skills/website-developer/skill.yaml`
- `project-types/website/agents/developer-agent/skills/website-developer/SKILL.md`
- all 8 modules under `skills/website-developer/modules/`
- `project-types/website/agents/developer-agent/rules/website-developer-integrity/rule.yaml`
- `project-types/website/agents/developer-agent/rules/website-developer-integrity/RULE.md`

The three pre-existing canonical upstream schemas (`project-types/website/schemas/customer-profile.schema.json`, `website-requirements.schema.json`, `design-proposal-set.schema.json`) are also frozen as of AIW-133, which gave each a real `urn:aiw:schema:*:v1` `$id` so the Developer schemas above can compose them by reference rather than copy them - a no-op for the existing Requirements/Designer validation pipeline (`ArtifactSchemaValidator` strips `$id` unconditionally regardless of value).

## Required Core Semantics

Implementation must also preserve:
- `RUNNER_VALIDATION_CONTRACT.md`
- `ARTIFACT_PERSISTENCE_CONTRACT.md`

`SOURCE_CONTEXT_CONTRACT.md` does not apply to this agent - like the Designer Agent, the Developer Agent's inputs are already-canonical artifacts (Customer Profile, Website Requirements, and one target Design Proposal), not a raw Source Context evidence snapshot.

## Website Developer Agent V1 Boundaries

- V1 supports only `WEBSITE` / `INITIAL_GENERATION`;
- one execution implements exactly one target Design Proposal; A/B/C sibling fan-out across proposals is Workflow-owned, never agent-internal;
- required input: exactly one `developer-execution-input` artifact (itself composing the canonical Customer Profile, Website Requirements and target Design Proposal, plus Runtime/Dependency/Verification/Tool profile refs, Integration Context and execution-control context);
- exactly one required output: a `developer-agent-result`, a strict union of `IMPLEMENTATION_READY | BLOCKED`;
- no implementation authority outside the supplied Runtime/Dependency/Verification/Tool-Capability profiles (React + TypeScript + Vite client/static-build only; no arbitrary server/database);
- no branch/merge/rebase/push/remote-credential Git authority - Git is inspection-only (`status`/`diff`/`diff-stat`);
- no self-certified verification, Candidate identity, repository state identity, Preview, QA, selection, or deployment status;
- no unbounded self-correction - correction happens only inside a Core/Workflow-authorized `maxCorrectionCycles` budget within one execution; a Runner retry after `FAIL`/`ERROR` creates a new immutable `AgentExecution` with lineage, never a mutation of the prior one;
- no silent enrichment of customer facts, requirements, or the target Design Proposal's visible structure/design intent;
- no invented providers, endpoints, backends, or fake external success for an unbound function;
- candidate output is not canonical until authoritative Runner Verification and immutable repository-state persistence succeed.

## Implementation Freedom

The following are intentionally not frozen by this Developer Agent V1 handoff:
- exact Java class/package names;
- the sandbox/workspace execution primitive (Azure Container Apps Jobs, per this session's own architecture decision - not itself part of the frozen contract);
- the exact agentic tool-calling loop implementation (extending `AiRequest`/`AiMessage`/`AiGateway`/`AnthropicProvider` for `tools`/`tool_use`/`tool_result`);
- the exact git-backed workspace/Development Base provisioning mechanism;
- exact provider-native structured-output envelope;
- exact global status enums;
- exact Skill Loader manifest/include syntax;
- exact DB schema;
- exact AI provider/model behind the `implementation-reasoning` model profile;
- exact retry count for the enclosing workflow;
- the real `developer-safe-integration-contract-view:v1` projection content (AIW-144 replaces the current placeholder).

Do not resolve these implementation freedoms by changing the frozen artifact semantics.
