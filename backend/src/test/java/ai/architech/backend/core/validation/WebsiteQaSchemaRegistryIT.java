package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

/**
 * Proves AIW-167's actual point: the frozen Website QA V1 schema family resolves correctly
 * through {@link WebsiteQaSchemaRegistry} once every schema carries a real {@code
 * urn:aiw:schema:*:v1} {@code $id} and the one genuine cross-file reference
 * ({@code qa-result} -&gt; {@code domain-result}) is fixed - mirrors {@link
 * DeveloperSchemaRegistryIT}'s own emphasis on proving the composition, not just the individual
 * schemas in isolation.
 *
 * <p>The frozen package's own two "invalid" fixtures ({@code source-design-mismatch.json},
 * {@code invented-finding-code.json}) are deliberately <em>not</em> asserted invalid here: both
 * are schema-valid JSON (a wrong ref value, an invented-but-pattern-matching finding code) - the
 * package's own {@code fixtures/README.md} lists them as scenarios for later Core
 * validators/registries (AIW-169's {@code CandidateBindingValidator}, AIW-174's {@code
 * FindingInvariantValidator}), not for JSON Schema validation. Asserting them schema-valid here
 * documents that boundary explicitly rather than leaving it implicit.
 */
@SpringBootTest
class WebsiteQaSchemaRegistryIT {

	private static final String FULL_RELEASE_BASIC_FIXTURE = "classpath:fixtures/website-qa-agent/full-release-basic.json";
	private static final String NAVIGATION_MAJOR_FIXTURE = "classpath:fixtures/website-qa-agent/navigation-major.json";
	private static final String SOURCE_DESIGN_MISMATCH_FIXTURE =
			"classpath:fixtures/website-qa-agent/source-design-mismatch.json";
	private static final String INVENTED_FINDING_CODE_FIXTURE =
			"classpath:fixtures/website-qa-agent/invented-finding-code.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private WebsiteQaSchemaRegistry registry;

	@Test
	void acceptsTheFrozenFullReleaseBasicExecutionInputFixture() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:qa-execution-input:v1", readClasspath(FULL_RELEASE_BASIC_FIXTURE));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void aWrongSourceDesignRefIsSchemaValidEvenThoughItIsASemanticBindingDefect() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:qa-execution-input:v1", readClasspath(SOURCE_DESIGN_MISMATCH_FIXTURE));

		assertThat(result.valid()).isTrue();
	}

	@Test
	void acceptsTheFrozenNavigationMajorSemanticOutputFixture() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:semantic-qa-review-output:v1", readClasspath(NAVIGATION_MAJOR_FIXTURE));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void anInventedFindingCodeIsSchemaValidEvenThoughItIsNotInTheTaxonomyRegistry() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:semantic-qa-review-output:v1", readClasspath(INVENTED_FINDING_CODE_FIXTURE));

		assertThat(result.valid()).isTrue();
	}

	@Test
	void rejectsASemanticOutputAttemptingToSmuggleAnAuthoritativeGateOutcomeField() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-101",
				  "findingCandidates": [],
				  "authorityIssueCandidates": [],
				  "evaluationIssueCandidates": [],
				  "semanticReviewCoverage": [],
				  "gateOutcome": "PASS"
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:semantic-qa-review-output:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void resolvesTheQaResultCrossFileReferenceIntoDomainResult() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-101",
				  "evaluationState": "COMPLETE",
				  "domainResults": [
				    {
				      "domain": "NAVIGATION",
				      "applicability": "APPLICABLE",
				      "coverage": "COMPLETE",
				      "assessment": "NON_CONFORMING",
				      "findingRefs": ["finding-1"],
				      "authorityIssueRefs": [],
				      "evaluationIssueRefs": [],
				      "executedCheckRefs": ["check-nav-1"],
				      "semanticReviewRefs": ["navigation-review-1"]
				    }
				  ],
				  "findingRefs": ["finding-1"],
				  "authorityIssueRefs": [],
				  "evaluationIssueRefs": [],
				  "policyEvaluationRefs": ["policy-eval-1"],
				  "remediationAssessmentRefs": [],
				  "aggregation": {
				    "gateOutcome": "HOLD",
				    "holdReasons": ["BLOCKING_CANDIDATE_FINDING"]
				  },
				  "evidenceManifestRef": "evidence-manifest-1",
				  "provenance": {
				    "qaSystemVersion": "1.0.0",
				    "qaAgentVersion": "1.0.0",
				    "ruleSetRef": "website-qa-rules@1.0.0",
				    "skillSetRef": "website-qa-skills@1.0.0",
				    "validatorSetRef": "website-qa-validator-set@1.0.0",
				    "toolCapabilityProfileRef": "website-qa-tools@1.0.0",
				    "findingTaxonomyRef": "website-qa-finding-taxonomy@1.0.0",
				    "checkRegistryRef": "website-qa-check-registry@1.0.0"
				  }
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:qa-result:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsAQaResultWhoseDomainResultHasAnInvalidApplicability() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-101",
				  "evaluationState": "COMPLETE",
				  "domainResults": [
				    {
				      "domain": "NAVIGATION",
				      "applicability": "MAYBE_APPLICABLE",
				      "findingRefs": [],
				      "authorityIssueRefs": [],
				      "evaluationIssueRefs": [],
				      "executedCheckRefs": [],
				      "semanticReviewRefs": []
				    }
				  ],
				  "findingRefs": [],
				  "authorityIssueRefs": [],
				  "evaluationIssueRefs": [],
				  "policyEvaluationRefs": [],
				  "remediationAssessmentRefs": [],
				  "aggregation": {"gateOutcome": "PASS", "holdReasons": []},
				  "evidenceManifestRef": "evidence-manifest-1",
				  "provenance": {
				    "qaSystemVersion": "1.0.0",
				    "qaAgentVersion": "1.0.0",
				    "ruleSetRef": "website-qa-rules@1.0.0",
				    "skillSetRef": "website-qa-skills@1.0.0",
				    "validatorSetRef": "website-qa-validator-set@1.0.0",
				    "toolCapabilityProfileRef": "website-qa-tools@1.0.0",
				    "findingTaxonomyRef": "website-qa-finding-taxonomy@1.0.0",
				    "checkRegistryRef": "website-qa-check-registry@1.0.0"
				  }
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:qa-result:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsAMinimalValidCandidateFinding() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "findingId": "finding-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "findingCode": "NAV_PRIMARY_FLOW_BROKEN",
				  "primaryDomain": "NAVIGATION",
				  "severity": "MAJOR",
				  "normativeBasis": [{"type": "SOURCE_DESIGN", "ref": "design-b-3"}],
				  "summary": "The primary mobile navigation cannot be closed at the required narrow viewport.",
				  "evidenceRefs": ["evidence-1"],
				  "fingerprint": "fingerprint-1",
				  "provenance": {"detectionMethod": "SEMANTIC"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-candidate-finding:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsACandidateFindingWithAnInvalidSeverity() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "findingId": "finding-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "findingCode": "NAV_PRIMARY_FLOW_BROKEN",
				  "primaryDomain": "NAVIGATION",
				  "severity": "BLOCKER",
				  "normativeBasis": [{"type": "SOURCE_DESIGN", "ref": "design-b-3"}],
				  "summary": "Invalid severity value.",
				  "evidenceRefs": ["evidence-1"],
				  "fingerprint": "fingerprint-1",
				  "provenance": {"detectionMethod": "SEMANTIC"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-candidate-finding:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsAMinimalValidAuthorityIssue() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "authorityIssueId": "authority-issue-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "code": "MISSING_INTEGRATION_AUTHORITY",
				  "summary": "No authorized Integration Contract exists for the bound payment capability.",
				  "affectedAuthorityRefs": ["integration-contract-ref-1"],
				  "expectedAuthorityTypes": ["INTEGRATION_CONTRACT"],
				  "affectedDomains": ["INTEGRATION_BEHAVIOR"],
				  "evidenceRefs": [],
				  "provenance": {"detectionMethod": "DETERMINISTIC"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-authority-issue:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsAnAuthorityIssueWithAnUnknownCode() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "authorityIssueId": "authority-issue-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "code": "SOMETHING_IS_WRONG",
				  "summary": "Not a real code.",
				  "affectedAuthorityRefs": [],
				  "expectedAuthorityTypes": [],
				  "affectedDomains": ["INTEGRATION_BEHAVIOR"],
				  "evidenceRefs": [],
				  "provenance": {"detectionMethod": "DETERMINISTIC"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-authority-issue:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsAMinimalValidEvaluationIssue() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "evaluationIssueId": "evaluation-issue-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "code": "TOOL_FAILURE",
				  "summary": "The accessibility scanner tool failed to run.",
				  "affectedDomains": ["ACCESSIBILITY_BASELINE"],
				  "evidenceRefs": [],
				  "provenance": {"source": "TOOL"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-evaluation-issue:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void acceptsAMinimalValidPolicyEvaluation() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "policyEvaluationId": "policy-eval-1",
				  "qaResultRef": "qa-result-1",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "subjectType": "CANDIDATE_FINDING",
				  "subjectRef": "finding-1",
				  "policyRuleRef": "severity-default",
				  "disposition": "BLOCK"
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-policy-evaluation:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void acceptsAMinimalValidRemediationAssessment() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "assessmentId": "assessment-1",
				  "previousFindingRef": "finding-1",
				  "testedCandidateRef": "candidate-43",
				  "qaExecutionRef": "qa-exec-102",
				  "qaResultRef": "qa-result-2",
				  "status": "RESOLVED",
				  "evidenceRefs": ["evidence-2"],
				  "relatedNewFindingRefs": [],
				  "evaluationIssueRefs": [],
				  "provenance": {"assessmentMethod": "SEMANTIC"}
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-qa-remediation-assessment:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	private String readClasspath(String location) {
		try {
			return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
