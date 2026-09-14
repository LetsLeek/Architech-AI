# Website QA Agent V1

Status: FROZEN implementation specification.

Website QA V1 is an evidence-driven, candidate-bound quality evaluation system that combines deterministic validation and bounded semantic review to assess an immutable, technically verified website implementation against authoritative customer data, requirements, source design, authorized functionality/integrations and a versioned QA policy.

It reports structured defects and evaluation/authority issues but never repairs source, changes Product Authority, selects variants, grants customer approval or deploys.

## Key boundaries

- QA evaluates existing authority; it never creates Product Authority.
- Exactly one immutable `WebsiteImplementationCandidate` is tested per QA execution.
- `CandidateFinding`, `AuthorityIssue`, and `EvaluationIssue` are distinct.
- Severity is `CRITICAL | MAJOR | MINOR`.
- Blocking is policy-derived: `BLOCK | ALLOW | ESCALATE`.
- The semantic agent never produces an authoritative gate decision.
- Final gate outcome is Core-owned: `PASS | HOLD`.
- Source remediation is performed only by the Website Developer Agent through `QA_REMEDIATION`.
- Every source mutation requires full Technical Verification and a new Candidate before Re-QA.

## Profiles

- `COMPARISON_READINESS`
- `FULL_RELEASE`

## Repository contents

- `agent.yaml` / `AGENT.md`
- normative `rules/`
- semantic `skills/`
- JSON `schemas/`
- versioned `registries/`
- QA `profiles/`
- performance policy
- Core validator / aggregator specification
- fixture manifest and representative fixtures
- Website Developer `QA_REMEDIATION` extension
- `INTEGRATION.md`

The package is intentionally repository-neutral because it can be integrated into different platform layouts without changing QA semantics.
