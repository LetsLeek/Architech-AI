# Skill — Represent unknown, missing and conflicting state

Input classification is Core-owned: `KNOWN`, `UNKNOWN`, `NOT_APPLICABLE`, `MISSING_AUTHORITY`, `AUTHORITY_CONFLICT`, `CONFORMANCE_MISMATCH`, `REPRESENTATION_DIVERGENCE` have different meanings. **Do not reclassify.** Follow the policy's DISCLOSE/OMIT/BLOCK rule for the particular category and section.

- `UNKNOWN`: say that no confirmed value is established *within the relevant source*. Do not fill it from common knowledge or implementation.
- `MISSING_AUTHORITY`: limit the statement to *not bound in this context*. Do not assert that no real-world event happened. Use the supplied `CONTEXT_STATE` key.
- `AUTHORITY_CONFLICT`: describe the competing authorized claims and unresolved status; don't pick one by timestamp/majority.
- `NOT_APPLICABLE`: never characterize an inapplicable feature as defective/missing.
- `CONFORMANCE_MISMATCH`: state both known intent and actual divergent implementation where profile and policy require it. Do not call a known mismatch "uncertain".

**Good:** "Für diesen dokumentierten Stand ist kein Deployment-Nachweis gebunden."
**Bad:** "Die Website wurde nie veröffentlicht." when only a DeploymentRecord is missing from this Context.

Related: `DOC-EPI-001`–`005`, `DOC-CTX-006`.
