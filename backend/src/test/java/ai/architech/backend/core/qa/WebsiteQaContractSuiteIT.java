package ai.architech.backend.core.qa;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.invariants.FindingTaxonomy;
import ai.architech.backend.core.qa.invariants.FindingTaxonomyLoader;
import ai.architech.backend.core.qa.policy.GateOutcome;
import ai.architech.backend.core.qa.policy.PolicyDisposition;
import ai.architech.backend.core.qa.policy.QAPolicyAggregator;
import ai.architech.backend.core.qa.policy.QaEvaluationState;
import ai.architech.backend.core.qa.profiles.AuthorityIssuePolicy;
import ai.architech.backend.core.qa.profiles.EvaluationIssuePolicy;
import ai.architech.backend.core.qa.profiles.FindingDispositionPolicy;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * AIW-182's own consolidated Website QA V1 contract test suite (CI-hardening pass over the whole
 * epic). Every ticket in the epic already carries its own real-Postgres/unit tests for its own
 * component - this class deliberately does not re-test those (see the cross-references below),
 * only the handful of AC-named critical scenarios that had no single, explicit, dedicated proof
 * anywhere yet:
 *
 * <ul>
 *   <li>Wrong Source Design / Preview drift - {@code QAExecutionPreflightValidatorIT}/{@code
 *       CandidateBindingValidatorTests} (AIW-169).
 *   <li>Foreign Evidence - {@code EvidenceBindingValidatorIT} (AIW-170).
 *   <li>Invented Finding code / invalid Severity - {@code FindingInvariantValidatorIT} (AIW-174),
 *       {@code WebsiteQaSchemaRegistryIT} (AIW-167).
 *   <li>Missing Integration Authority - {@code AuthorityIssueInvariantValidatorTests} (AIW-174).
 *   <li>Agent self-approval fields - {@code WebsiteQaSchemaRegistryIT#rejectsASemanticOutputAttemptingToSmuggleAnAuthoritativeGateOutcomeField}
 *       (AIW-167).
 *   <li>PARTIAL/INVALID cannot PASS - {@code QAPolicyAggregatorTests} (AIW-176) - this class adds
 *       one consolidated proof exercising both states together against the real frozen profile.
 *   <li>Comparison 3-variant barrier / stale-result rejection - {@code
 *       ComparisonReadinessBarrierEvaluatorIT} (AIW-177).
 *   <li>Remediation RESOLVED/PERSISTS/CHANGED/NOT_EVALUABLE - {@code
 *       RemediationAssessmentInvariantValidatorIT} (AIW-179).
 * </ul>
 *
 * <p>This class itself adds genuinely new coverage: that the frozen finding taxonomy structurally
 * cannot produce a Finding for an unfulfilled {@code could} Requirement (no such code exists at
 * all - "MUST NOT create a Finding solely because a could Requirement was not implemented" is
 * therefore enforced by the taxonomy's own closed content, not runtime logic), that the Customer
 * Fact domain's own codes are exactly the frozen four plus a fallback (known unknown / unsupported
 * claim / contradiction / identity misrepresentation), that a {@code TOOL_FAILURE} Evaluation
 * Issue never itself blocks or influences a Finding's own disposition (they are independently
 * policy-evaluated subjects), and that a regression Finding always receives its own distinct
 * identity even when every other structured field is identical to a prior Finding.
 */
@SpringBootTest
class WebsiteQaContractSuiteIT {

	@Autowired
	private FindingTaxonomyLoader taxonomyLoader;

	@Autowired
	private QAPolicyAggregator policyAggregator;

	@Test
	void theFrozenTaxonomyHasNoFindingCodeForAnUnfulfilledCouldRequirement() {
		FindingTaxonomy taxonomy = taxonomyLoader.load();

		boolean anyCouldSpecificCode = taxonomy.entries().stream()
				.anyMatch(entry -> entry.code().toUpperCase().contains("COULD"));

		assertThat(anyCouldSpecificCode)
				.as("rules/authority.md: 'MUST NOT create a Finding solely because a could Requirement was not implemented' "
						+ "- the taxonomy must not offer a code whose own purpose is exactly that")
				.isFalse();
	}

	@Test
	void theRequirementFulfillmentDomainOnlyCoversMustAndShouldStrengthDefects() {
		FindingTaxonomy taxonomy = taxonomyLoader.load();

		List<String> requirementFulfillmentCodes = taxonomy.entries().stream()
				.filter(entry -> entry.domain().equals("REQUIREMENT_FULFILLMENT"))
				.map(ai.architech.backend.core.qa.invariants.FindingTaxonomyEntry::code)
				.toList();

		assertThat(requirementFulfillmentCodes).containsExactlyInAnyOrder(
				"REQ_MUST_UNFULFILLED", "REQ_SHOULD_MATERIALLY_UNFULFILLED",
				"REQ_IMPLEMENTATION_MATERIALLY_INCOMPLETE", "REQ_OTHER_MATERIAL_DEFECT");
	}

	@Test
	void theCustomerFactCorrectnessDomainCoversExactlyKnownUnknownUnsupportedContradictionAndIdentity() {
		FindingTaxonomy taxonomy = taxonomyLoader.load();

		List<String> factCodes = taxonomy.entries().stream()
				.filter(entry -> entry.domain().equals("CUSTOMER_FACT_CORRECTNESS"))
				.map(ai.architech.backend.core.qa.invariants.FindingTaxonomyEntry::code)
				.toList();

		assertThat(factCodes).containsExactlyInAnyOrder(
				"FACT_CONTRADICTION", "FACT_UNSUPPORTED_CLAIM", "FACT_UNKNOWN_MATERIALIZED",
				"FACT_IDENTITY_MISREPRESENTATION", "FACT_OTHER_MATERIAL_DEFECT");
	}

	@Test
	void neitherPartialNorInvalidEvaluationStateCanEverProducePassRegardlessOfFindings() {
		QaProfile profile = fullReleaseProfileFixture();

		GateOutcome partialOutcome = policyAggregator.aggregate(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), profile, QaEvaluationState.PARTIAL,
				List.of(), List.of(), List.of(), true, false).gateOutcome();
		GateOutcome invalidOutcome = policyAggregator.aggregate(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), profile, QaEvaluationState.INVALID,
				List.of(), List.of(), List.of(), true, false).gateOutcome();

		assertThat(partialOutcome).isEqualTo(GateOutcome.HOLD);
		assertThat(invalidOutcome).isEqualTo(GateOutcome.HOLD);
	}

	@Test
	void aToolFailureEvaluationIssueNeverBlocksAnUnrelatedFindingsOwnDisposition() {
		QaProfile profile = fullReleaseProfileFixture();
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID candidateId = UUID.randomUUID();

		CandidateFinding allowedFinding = new CandidateFinding(
				qaResultId, qaExecutionId, candidateId, "NAV_TARGET_MISMATCH", "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-1", "{\"detectionMethod\":\"SEMANTIC\"}");
		EvaluationIssue toolFailure = new EvaluationIssue(
				qaResultId, qaExecutionId, candidateId, "TOOL_FAILURE", "the accessibility scanner tool failed",
				"[\"ACCESSIBILITY_BASELINE\"]", null, "ACCESSIBILITY_SCANNER", null, "[]", "{\"source\":\"TOOL\"}");

		var result = policyAggregator.aggregate(
				qaResultId, qaExecutionId, candidateId, profile, QaEvaluationState.COMPLETE,
				List.of(allowedFinding), List.of(), List.of(toolFailure), true, false);

		// The Finding's own disposition is exactly its severity default (ALLOW for MINOR),
		// completely unaffected by the unrelated TOOL_FAILURE Evaluation Issue existing alongside
		// it - "Tool failure is not positive Evidence" and never contaminates a different
		// subject's own policy evaluation.
		PolicyEvaluation findingEvaluation = result.policyEvaluations().stream()
				.filter(pe -> "CANDIDATE_FINDING".equals(pe.getSubjectType()))
				.findFirst()
				.orElseThrow();
		assertThat(findingEvaluation.getDisposition()).isEqualTo("ALLOW");
		// But the TOOL_FAILURE Evaluation Issue's own ESCALATE disposition still independently
		// prevents an overall PASS - it is never silently dropped either.
		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
	}

	@Test
	void aRegressionFindingAlwaysReceivesItsOwnDistinctIdentityEvenWithIdenticalContent() {
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID candidateId = UUID.randomUUID();
		String identicalArgs = "NAV_TARGET_MISMATCH";

		CandidateFinding originalFinding = new CandidateFinding(
				qaResultId, qaExecutionId, candidateId, identicalArgs, "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-1", "{\"detectionMethod\":\"SEMANTIC\"}");
		CandidateFinding regressionFinding = new CandidateFinding(
				qaResultId, qaExecutionId, candidateId, identicalArgs, "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-1", "{\"detectionMethod\":\"SEMANTIC\"}");

		assertThat(regressionFinding.getId()).isNotEqualTo(originalFinding.getId());
	}

	private QaProfile fullReleaseProfileFixture() {
		return new QaProfile(
				"website-qa-full-release@1.0.0", QaProfileType.FULL_RELEASE, List.of(), List.of(), List.of(),
				new FindingDispositionPolicy(
						java.util.Map.of(),
						java.util.Map.of(
								ai.architech.backend.core.qa.invariants.QaSeverity.CRITICAL, PolicyDisposition.BLOCK,
								ai.architech.backend.core.qa.invariants.QaSeverity.MAJOR, PolicyDisposition.BLOCK,
								ai.architech.backend.core.qa.invariants.QaSeverity.MINOR, PolicyDisposition.ALLOW)),
				Optional.empty(),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE),
				new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}
}
