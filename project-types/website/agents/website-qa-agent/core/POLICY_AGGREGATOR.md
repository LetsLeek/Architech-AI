# Website QA V1 — Deterministic Policy Aggregator

The `QAPolicyAggregator` MUST NOT use generative AI.

Inputs:
- exact tested Candidate ref
- active immutable QA Profile
- validated Candidate Findings
- validated Authority Issues
- validated Evaluation Issues
- validated Domain Results

For Candidate Findings, precedence is:
1. exact finding-code rule
2. applicable requirement-specific rule
3. severity default

Authority/Evaluation issues use their own profile policy.

Outputs are immutable `PolicyEvaluation` records:
- BLOCK
- ALLOW
- ESCALATE

Gate aggregation:
PASS only if:
- evaluationState == COMPLETE
- all required applicable Domains meet coverage policy
- no unresolved BLOCK
- no unresolved ESCALATE
- no profile-specific invariant prevents PASS

Otherwise HOLD.

Hold reasons are Core-derived:
- BLOCKING_CANDIDATE_FINDING
- AUTHORITY_RESOLUTION_REQUIRED
- EVALUATION_INCOMPLETE
- HUMAN_REVIEW_REQUIRED
- EXECUTION_INVALID

`FULL_RELEASE` materially unfulfilled `must` Requirements always prevent PASS.

A PASS qualifies only the exact Candidate under the exact Profile/version and only permits the next stage defined by Workflow.
