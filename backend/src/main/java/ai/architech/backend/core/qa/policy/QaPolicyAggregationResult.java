package ai.architech.backend.core.qa.policy;

import ai.architech.backend.core.qa.PolicyEvaluation;
import java.util.List;

/** One QA Result's own aggregation outcome (AIW-176) - {@link #policyEvaluations()} is already persisted, immutable audit history. */
public record QaPolicyAggregationResult(List<PolicyEvaluation> policyEvaluations, GateOutcome gateOutcome, List<HoldReason> holdReasons) {}
