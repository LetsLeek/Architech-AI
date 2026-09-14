# Skill — Remediation Reassessment

During Re-QA, assess each previous Finding against the new immutable Candidate as:
RESOLVED | PERSISTS | CHANGED | NOT_EVALUABLE.

RESOLVED requires new Candidate Evidence that the original defect no longer exists.
PERSISTS requires a current Finding candidate for the new Candidate.
CHANGED requires one or more current Finding candidates representing the changed current defect.
NOT_EVALUABLE requires an Evaluation Issue candidate.
Historical Findings never mutate.
Previous Findings are remediation context, not Product Authority.
Normal QA_REMEDIATION requires same Variant Lineage, Source Design and Product Authority baseline.
Reassessment does not replace regression review.
