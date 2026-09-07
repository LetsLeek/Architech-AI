# Requirements Agent V1 — Frozen Handoff

Status: **FROZEN**

This document identifies the V1 contract surface for the Website Requirements Agent. Implementation may choose class names, packages, persistence technology details, and DTO shapes that are not explicitly frozen, but must preserve the semantics of the files in this handoff.

## Frozen Website Files

- `project-types/website/agents/requirements-agent/agent.yaml`
- `project-types/website/agents/requirements-agent/AGENT.md`
- `project-types/website/schemas/customer-profile.schema.json`
- `project-types/website/schemas/website-requirements.schema.json`
- `project-types/website/skills/extract-business-requirements/skill.yaml`
- `project-types/website/skills/extract-business-requirements/SKILL.md`
- all 13 modules under `skills/extract-business-requirements/modules/`
- `project-types/website/rules/requirements-integrity/rule.yaml`
- `project-types/website/rules/requirements-integrity/RULE.md`

## Required Core Semantics

Implementation must also preserve:
- `SOURCE_CONTEXT_CONTRACT.md`
- `RUNNER_VALIDATION_CONTRACT.md`
- `ARTIFACT_PERSISTENCE_CONTRACT.md`

## Requirements Agent V1 Boundaries

- no agent tools;
- only Runner-supplied immutable evidence;
- exactly two required outputs;
- no design/implementation planning;
- no self-validation or self-success;
- no unbounded retries;
- no silent evidence enrichment;
- no silent conflict resolution;
- candidate output is not canonical until required validation and atomic persistence succeed.

## Implementation Freedom

The following are intentionally not frozen by this Requirements Agent V1 handoff:
- exact Java class/package names;
- exact SourceContext transport envelope;
- exact provider-native structured-output envelope;
- exact global status enums;
- exact Skill Loader manifest/include syntax;
- exact DB schema;
- exact AI provider;
- exact retry count for the enclosing workflow;
- optional semantic review policy.

Do not resolve these implementation freedoms by changing the frozen artifact semantics.
