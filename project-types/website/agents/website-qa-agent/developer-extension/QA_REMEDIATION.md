# Website Developer Agent V1 — QA_REMEDIATION Extension

New supported operation: `QA_REMEDIATION`.

Purpose: repair validated Candidate defects under unchanged Product Authority.

Preconditions:
- exact immutable `sourceCandidateRef`
- exact source repository state
- valid originating QA Result
- one or more Workflow-authorized Candidate Finding refs
- same Variant Lineage
- unchanged Product Authority baseline
- unchanged Source Design
- compatible Runtime
- Workflow authorization / remaining remediation budget

Inputs conceptually include:
- operation = QA_REMEDIATION
- sourceCandidateRef / sourceRepositoryStateRef
- Customer Profile / Website Requirements / Source Design / Runtime / Integration Contract refs
- sourceQAResultRef
- authorizedFindingRefs
- relevantEvidenceRefs
- Development Base / Dependency / Verification / Tool policies
- immutable agent/rule/skill versions

Rules:
- Findings provide bounded repair authority, not new Product Authority.
- Start from the exact source Candidate state, never latest main/HEAD/another variant.
- Make the smallest technically sufficient repair.
- May modify implementation source, styles, routing, local behavior, responsive/a11y/content rendering, valid integration binding and regression tests.
- Must not add unrelated features, facts, redesign, provider changes, Runtime replacement, opportunistic dependency upgrades or unrelated refactors.
- If repair requires changing Product Authority/Source Design, stop normal remediation and route to an authorized revision/authority workflow.
- Developer decides technical repair; QA does not prescribe implementation.
- Developer may report repair attempt / changed areas / tests, but must not declare the Finding resolved or QA passed.

Execution result remains:
- IMPLEMENTATION_READY
- BLOCKED

Recommended remediation blocker codes:
- REMEDIATION_AUTHORITY_CONFLICT
- MISSING_REMEDIATION_AUTHORITY
- INVALID_FINDING_REFERENCE
- INVALID_SOURCE_CANDIDATE
- REMEDIATION_SCOPE_INCOMPATIBLE
- MISSING_INTEGRATION_CONTRACT
- INVALID_INTEGRATION_CONTRACT
- MISSING_UPSTREAM_INFORMATION

Every source change requires full Technical Verification.
Only Verification PASS may produce a new Candidate.
The new Candidate inherits no QA qualification.
Re-QA determines RESOLVED | PERSISTS | CHANGED | NOT_EVALUABLE.
