# QA Fixtures

The production implementation should add positive and negative fixtures for every schema, validator and policy invariant.

Minimum required groups:
- SchemaContractTests
- QAExecutionPreflightTests
- CandidateBindingTests
- EvidenceBindingTests
- FindingInvariantTests
- AuthorityIssueInvariantTests
- EvaluationIssueInvariantTests
- DomainResultInvariantTests
- ProfileApplicabilityTests
- RequirementCoverageTests
- PolicyAggregatorTests
- RemediationLineageTests
- ComparisonEligibilityTests
- FullReleaseEligibilityTests
- ImmutabilityTests

Critical scenarios:
- wrong Source Design
- execution-surface drift
- invented Finding code
- Finding without Evidence
- foreign Candidate Evidence
- invalid Severity
- missing Integration Authority
- fake success
- tool failure
- APPLICABLE + NONE + CONFORMING invalid
- missing integration must not become N/A
- Comparison MINOR ALLOW
- Comparison MAJOR BLOCK
- Full Release placeholder override BLOCK
- Full Release missing title override BLOCK
- allowed Full Release MINOR
- materially unfulfilled must BLOCK
- missing could creates no Finding
- known unknown materialized
- supported paraphrase
- unsupported factual expansion
- allowed Design variation
- material Design deviation
- remediation RESOLVED/PERSISTS/CHANGED/NOT_EVALUABLE
- cross-variant remediation invalid
- changed Product Authority invalid under normal remediation
- changed Source Design invalid under normal remediation
- 3-variant comparison barrier
- stale comparison result rejected
- Full Release happy path
- PARTIAL cannot PASS
- INVALID cannot PASS
- authority conflict -> ESCALATE/HOLD
- semantic agent attempt to output productionReady/blocking/gateOutcome rejected
- dedup and do-not-overmerge
- regression creates new Finding ID

Core contract tests must not depend on a live LLM.
