# Agent Runner and Validation Contract — V1

## Core Principle

Agents produce candidate outputs. The platform determines whether candidates are valid and may become canonical artifacts.

A successful model call is not equivalent to a successful AgentExecution.

## Responsibilities

### Agent Runner

The Runner coordinates one AgentExecution attempt:

1. resolve the agent configuration;
2. resolve required skills and rules;
3. obtain the immutable evidence snapshot/context for the attempt;
4. resolve the model profile through the AI Gateway;
5. enforce applicable budget limits;
6. invoke the model;
7. receive and retain candidate output for audit;
8. parse the candidate;
9. invoke the Validation Pipeline;
10. request atomic artifact persistence only when all required validation succeeds;
11. record execution outcome and telemetry.

The Runner must not contain Website-specific price, opening-hours, or requirement-classification validation logic.

### Validation Pipeline

Validation is layered. For the Requirements Agent V1 the required deterministic layers are:

1. **Output Contract Validation**
   - required artifact types are present;
   - no required artifact is duplicated;
   - unexpected outputs are rejected according to the active contract;
   - output is parseable by the structured-output mechanism.

2. **JSON Schema Validation**
   - validate each candidate against its authoritative schema.

3. **Source Reference Validation**
   - every supplied `sourceRef` exists in the exact evidence snapshot used by the attempt.

4. **Local/Cross Reference Validation**
   - `localRef` uniqueness;
   - `affects` targets exist where applicable;
   - opening-hours `locationRefs` resolve to locations;
   - provenance `targetRef` resolves to a valid referencable entity.

5. **Artifact Semantic Validation**
   - relational invariants that are inappropriate or unnecessarily complex in JSON Schema.

6. **Required Deterministic Cross-Artifact Validation**
   - machine-checkable consistency between the required candidate artifacts.

A quality profile or workflow policy may add semantic AI review later. Such a reviewer cannot override failing deterministic validation.

## Validator Behavior

Validators detect and report. They do not mutate, normalize, repair, or auto-correct candidate output.

If deterministic transformation is introduced later, it must be modeled as an explicit transformation stage rather than hidden inside a validator.

A validation issue should carry enough structured information for audit and controlled retry feedback. The exact Java DTO is not frozen here. Useful concepts include:
- code;
- category;
- artifact type;
- path/target when applicable;
- message;
- severity.

Issue severity does not itself decide retry behavior.

## Validation Categories

V1 should be able to distinguish at least:
- output-contract;
- schema;
- reference;
- semantic;
- cross-artifact;
- system/infrastructure failures.

The exact enum names are implementation-level and need not be identical to these labels.

## Retry Ownership

Agents do not own retry loops.

Workflow policy decides whether an invalid attempt may be retried, escalated for review, or failed. Retries must be bounded.

A retry may receive structured validation issues from the previous attempt. Do not expose irrelevant internal stack traces as model instructions.

Each retry is a new AgentExecution and therefore has its own AI telemetry/cost record.

## Success Boundary

An AgentExecution is successful only after:
- the required model output was received;
- the candidate was parsed;
- all required validation passed; and
- all required artifacts were atomically persisted as valid versions.

The exact global AgentExecution status enum is not frozen by this contract.
