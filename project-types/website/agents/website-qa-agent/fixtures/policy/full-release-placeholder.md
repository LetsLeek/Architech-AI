Scenario:
- Finding: CONTENT_PLACEHOLDER_LEAK
- Severity: MINOR
- Evaluation complete; all other required domains covered.

Expected:
- exact finding-code override wins over MINOR default.
- PolicyEvaluation.disposition = BLOCK
- QAResult.gateOutcome = HOLD
- holdReasons contains BLOCKING_CANDIDATE_FINDING
