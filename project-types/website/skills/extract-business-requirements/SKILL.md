# Extract Business Requirements

## Purpose

Produce a normalized Customer Profile and traceable Website Requirements from the immutable Source Context supplied for one requirements-analysis attempt.

This is one skill and one AI task. The modules below are compositional instruction units, not separate agents, model calls, retries, or workflow steps. The Skill Loader must include their contents in the active skill context in the listed order.

## Phase A — Evidence Extraction

1. `modules/01-source-context.md`
2. `modules/02-customer-facts.md`
3. `modules/03-normalization.md`
4. `modules/04-provided-claims.md`
5. `modules/05-unknowns-conflicts.md`

Establish the permitted evidence boundary, extract supported facts, normalize safely, separate provided claims, and preserve unresolved information.

## Phase B — Website Intent Extraction

6. `modules/06-website-intent.md`
7. `modules/07-requirement-classification.md`
8. `modules/08-no-design-leakage.md`
9. `modules/09-strength-classification.md`

Extract only supported website intent, classify it by semantic purpose, keep design/implementation invention out of requirements, and assign strength from customer commitment.

## Phase C — Traceability and Cross-Artifact Consistency

10. `modules/10-traceability.md`
11. `modules/11-cross-artifact-check.md`

Maintain evidence traceability and ensure the Customer Profile and Website Requirements preserve their separate responsibilities.

## Phase D — Finalization

12. `modules/12-final-consistency.md`
13. `modules/13-output.md`

Perform one bounded consistency pass and emit only the required structured candidate outputs.

## Authority

The output schemas are authoritative for structure.

The Requirements Integrity rule is authoritative for evidence-integrity constraints.

Platform validators are authoritative for machine validation. Do not self-certify validity, readiness, persistence, or execution success.
