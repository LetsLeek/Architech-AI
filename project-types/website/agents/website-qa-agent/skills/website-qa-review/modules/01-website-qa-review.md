# Skill — Website QA Review

Orchestrates the semantic portion of one already-authorized QA execution.

Process:
Authority -> deterministic Evidence -> semantic Evidence -> observation -> Candidate attribution -> materiality -> classification -> structured output.

Respect the active profile. Comparison is narrower; Full Release is complete.
Consume deterministic results before semantic judgment.
Invoke only applicable domain skills plus evidence-assessment, finding-construction and deduplication; use remediation-reassessment during Re-QA.
Classify every potential issue as CANDIDATE_DEFECT, AUTHORITY_ISSUE, EVALUATION_ISSUE or NO_ISSUE before constructing output.
Do not search for arbitrary improvements.
Request only minimum additional authorized Evidence.
Stop when Evidence is sufficient or cannot be obtained within authorized capabilities.
Emit only schema-valid SemanticQAReviewOutput.
Never output authoritative Gate, blocking, approval, selection, production readiness, deployment or retry decisions.
