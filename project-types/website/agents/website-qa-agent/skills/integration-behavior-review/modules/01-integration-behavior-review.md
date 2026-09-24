# Skill — Integration Behavior Review

Applicable only to authorized externally bound functionality.

Use exact Integration Contracts only; never invent provider/API/endpoint/auth semantics.
Require safe-test capability before external side effects.
Potential codes: INTEGRATION_BOUND_OPERATION_FAILED, INTEGRATION_RESPONSE_MISREPRESENTED, INTEGRATION_CONTRACT_BEHAVIOR_MISMATCH.
Missing Integration Authority -> Authority Issue, not Candidate Finding.
Unavailable safe test -> Evaluation Issue.
Never request/expose raw credentials in semantic context.
