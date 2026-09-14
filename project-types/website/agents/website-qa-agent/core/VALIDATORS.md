# Website QA V1 — Core Validator Set

Validator set ref: `website-qa-validator-set@1.0.0`

These validators are Platform/Core components. They are not generative-agent responsibilities.

## QAExecutionPreflightValidator
Validates Candidate existence/immutability, Technical Verification PASS, source state, authority refs, QA Profile/Rule/Skill/Validator/Registry versions, Tool Capability compatibility and project/customer boundaries. Normal semantic QA must not start after a foundational preflight failure.

## CandidateBindingValidator
Validates exact Candidate, repository state, Source Design, Runtime, Technical Verification and execution-surface binding. Wrong Preview/Candidate binding must yield an Evaluation integrity problem, never a Candidate Finding against the intended Candidate.

## QAInputReferenceValidator
Validates all normative basis and authority references against the active immutable input snapshot.

## EvidenceBindingValidator
Validates Evidence existence, integrity, Candidate/execution binding and relevant context. Foreign Candidate Evidence requires explicit compatible reuse provenance.

## FindingInvariantValidator
Validates Registry code, primary Domain, normative basis type, Evidence, Candidate attribution and Severity bounds before persistence.

## FindingFingerprintNormalizer
Produces a stable structured fingerprint from Candidate, finding code, normative basis, route/context/anchors and other stable fields. Do not hash free-form summary alone.

## FindingDeduplicationValidator
Checks structural consistency of semantic deduplication and prevents obvious duplicate persistence/over-merging.

## AuthorityIssueInvariantValidator
Enforces code-specific authority invariants. `CONFLICTING_AUTHORITY` should reference actual conflicting authority; missing-authority codes must identify the expected authority type.

## EvaluationIssueInvariantValidator
Enforces code-specific evaluation context, e.g. drift requires an execution surface; missing capability requires capability/check context.

## DomainResultInvariantValidator
Constructs/validates Domain applicability, coverage and assessment from executed checks/reviews/findings/issues. Invalid positive states such as APPLICABLE + NONE + CONFORMING must be rejected.

## RequirementCoverageValidator
For Full Release, ensures every applicable Requirement has a traceable assessment.

## ProfileApplicabilityValidator
Determines conditional Domain applicability from Profile + Product Authority. Missing integration authority must not be disguised as `NOT_APPLICABLE`.

## ToolProfileCompatibilityValidator
Preflight-checks that Profile-required capabilities exist before expensive execution begins.

## RemediationLineageValidator
Validates previous Finding/Candidate lineage, same Variant Lineage, stable Product Authority/Source Design for normal QA_REMEDIATION, and current Candidate ownership of related Findings/Evidence.

## ComparisonEligibilityValidator
Requires a valid current Comparison Readiness PASS Candidate for every required Variant Lineage; never silently drop a required variant.

## FullReleaseEligibilityValidator
Requires a valid, non-stale FULL_RELEASE PASS for the exact active release-path Candidate before transition to Final Human/Customer Approval.

## Versioning
All validator behavior is versioned. Historical QA Results retain the Validator Set reference used to produce them.
