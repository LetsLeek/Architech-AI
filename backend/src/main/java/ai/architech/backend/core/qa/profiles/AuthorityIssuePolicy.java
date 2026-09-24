package ai.architech.backend.core.qa.profiles;

import ai.architech.backend.core.qa.policy.PolicyDisposition;

/** One profile's own {@code authorityIssuePolicy} block (AIW-176) - "Authority/Evaluation issues use their own profile policy." */
public record AuthorityIssuePolicy(PolicyDisposition gateRelevantIssueDisposition) {}
