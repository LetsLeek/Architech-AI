# Designer Agent V1 — Frozen Handoff

Status: **FROZEN**

This document identifies the V1 contract surface for the Website Designer Agent. Implementation may choose class names, packages, persistence technology details, and DTO shapes that are not explicitly frozen, but must preserve the semantics of the files in this handoff.

## Frozen Website Files

- `project-types/website/agents/designer-agent/agent.yaml`
- `project-types/website/agents/designer-agent/AGENT.md`
- `project-types/website/schemas/design-proposal-set.schema.json`
- `project-types/website/skills/plan-website-design/skill.yaml`
- `project-types/website/skills/plan-website-design/SKILL.md`
- all 12 modules under `skills/plan-website-design/modules/`
- `project-types/website/rules/design-integrity/rule.yaml`
- `project-types/website/rules/design-integrity/RULE.md`

## Required Core Semantics

Implementation must also preserve:
- `RUNNER_VALIDATION_CONTRACT.md`
- `ARTIFACT_PERSISTENCE_CONTRACT.md`

`SOURCE_CONTEXT_CONTRACT.md` does not apply to this agent - the Designer Agent's inputs are the already-canonical `customer-profile` and `website-requirements` artifacts (the Requirements Agent's own required outputs), not a raw Source Context evidence snapshot.

## Designer Agent V1 Boundaries

- required inputs: `customer-profile` and `website-requirements` (both canonical, both required);
- exactly one required output: a `design-proposal-set` containing exactly three proposals;
- no implementation-architecture planning (framework, DOM, CSS, APIs, database, deployment);
- no self-validation or self-success;
- no unbounded retries;
- no silent enrichment of customer facts or requirements;
- no silent conflict resolution, and no assigning conflicting factual alternatives across proposals;
- no ranking, recommending, or otherwise distinguishing one proposal as preferred;
- candidate output is not canonical until required validation and atomic persistence succeed.

## Implementation Freedom

The following are intentionally not frozen by this Designer Agent V1 handoff:
- exact Java class/package names;
- exact artifact-fetch mechanism for the two required input artifacts;
- exact provider-native structured-output envelope;
- exact global status enums;
- exact Skill Loader manifest/include syntax;
- exact DB schema;
- exact AI provider/model behind the `design-reasoning` model profile;
- exact retry count for the enclosing workflow;
- optional semantic review policy.

Do not resolve these implementation freedoms by changing the frozen artifact semantics.
