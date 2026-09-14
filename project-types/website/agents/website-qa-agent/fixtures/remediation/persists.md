Scenario:
C1 -> Finding F17: mobile menu cannot close.
Workflow authorizes QA_REMEDIATION.
C2 passes Technical Verification.
Re-QA demonstrates the same defect.

Expected:
- New Finding F31 belongs to C2.
- RemediationAssessment.previousFindingRef = F17
- status = PERSISTS
- relatedNewFindingRefs = [F31]
- F17 remains immutable and bound to C1.
