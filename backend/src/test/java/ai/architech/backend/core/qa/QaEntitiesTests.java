package ai.architech.backend.core.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain unit coverage for every {@code core.qa} entity's constructor/getters (AIW-168) -
 * including the nullable-field variants ({@code diagnosticDetails}/{@code contextJson}/{@code
 * executionSurfaceRef}/{@code requiredCheckRef}/{@code toolCapabilityRef}/{@code reasonCode}/
 * {@code notes}) that {@link QaPersistenceModelIT}'s own real-Postgres scenarios don't each
 * individually exercise.
 */
class QaEntitiesTests {

	@Test
	void qaExecutionExposesEveryConstructorArgumentIncludingANullExecutionSurfaceRef() {
		UUID agentExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		QaExecution execution = new QaExecution(agentExecutionId, testedCandidateId, "profile-ref", null, "tool-profile-ref");

		assertThat(execution.getId()).isNotNull();
		assertThat(execution.getAgentExecutionId()).isEqualTo(agentExecutionId);
		assertThat(execution.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(execution.getQaProfileRef()).isEqualTo("profile-ref");
		assertThat(execution.getExecutionSurfaceRef()).isNull();
		assertThat(execution.getToolCapabilityProfileRef()).isEqualTo("tool-profile-ref");

		QaExecution withSurface = new QaExecution(agentExecutionId, testedCandidateId, "profile-ref", "surface-ref", "tool-profile-ref");
		assertThat(withSurface.getExecutionSurfaceRef()).isEqualTo("surface-ref");
	}

	@Test
	void qaInputSnapshotExposesItsFields() {
		UUID qaExecutionId = UUID.randomUUID();

		QaInputSnapshot snapshot = new QaInputSnapshot(qaExecutionId, "{\"a\":1}");

		assertThat(snapshot.getId()).isNotNull();
		assertThat(snapshot.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(snapshot.getInputJson()).isEqualTo("{\"a\":1}");
	}

	@Test
	void evidenceManifestExposesItsFields() {
		UUID qaExecutionId = UUID.randomUUID();

		EvidenceManifest manifest = new EvidenceManifest(qaExecutionId);

		assertThat(manifest.getId()).isNotNull();
		assertThat(manifest.getQaExecutionId()).isEqualTo(qaExecutionId);
	}

	@Test
	void candidateFindingExposesEveryFieldIncludingNullDiagnosticDetailsAndContext() {
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		CandidateFinding finding = new CandidateFinding(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				"NAV_PRIMARY_FLOW_BROKEN",
				"NAVIGATION",
				"MAJOR",
				"[]",
				"summary",
				null,
				null,
				"[]",
				"fingerprint",
				"{}");

		assertThat(finding.getId()).isNotNull();
		assertThat(finding.getQaResultId()).isEqualTo(qaResultId);
		assertThat(finding.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(finding.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(finding.getFindingCode()).isEqualTo("NAV_PRIMARY_FLOW_BROKEN");
		assertThat(finding.getPrimaryDomain()).isEqualTo("NAVIGATION");
		assertThat(finding.getSeverity()).isEqualTo("MAJOR");
		assertThat(finding.getNormativeBasisJson()).isEqualTo("[]");
		assertThat(finding.getSummary()).isEqualTo("summary");
		assertThat(finding.getDiagnosticDetails()).isNull();
		assertThat(finding.getContextJson()).isNull();
		assertThat(finding.getEvidenceRefsJson()).isEqualTo("[]");
		assertThat(finding.getFingerprint()).isEqualTo("fingerprint");
		assertThat(finding.getProvenanceJson()).isEqualTo("{}");

		CandidateFinding withOptionalFields = new CandidateFinding(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				"NAV_PRIMARY_FLOW_BROKEN",
				"NAVIGATION",
				"MAJOR",
				"[]",
				"summary",
				"diagnostic details",
				"{\"route\":\"/\"}",
				"[]",
				"fingerprint",
				"{}");
		assertThat(withOptionalFields.getDiagnosticDetails()).isEqualTo("diagnostic details");
		assertThat(withOptionalFields.getContextJson()).isEqualTo("{\"route\":\"/\"}");
	}

	@Test
	void authorityIssueExposesEveryField() {
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		AuthorityIssue issue = new AuthorityIssue(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				"MISSING_INTEGRATION_AUTHORITY",
				"summary",
				"[]",
				"[]",
				"[]",
				"[]",
				"{}");

		assertThat(issue.getId()).isNotNull();
		assertThat(issue.getQaResultId()).isEqualTo(qaResultId);
		assertThat(issue.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(issue.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(issue.getCode()).isEqualTo("MISSING_INTEGRATION_AUTHORITY");
		assertThat(issue.getSummary()).isEqualTo("summary");
		assertThat(issue.getAffectedAuthorityRefsJson()).isEqualTo("[]");
		assertThat(issue.getExpectedAuthorityTypesJson()).isEqualTo("[]");
		assertThat(issue.getAffectedDomainsJson()).isEqualTo("[]");
		assertThat(issue.getEvidenceRefsJson()).isEqualTo("[]");
		assertThat(issue.getProvenanceJson()).isEqualTo("{}");
	}

	@Test
	void evaluationIssueExposesEveryFieldIncludingItsThreeNullableRefs() {
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		EvaluationIssue issue =
				new EvaluationIssue(qaResultId, qaExecutionId, testedCandidateId, "TOOL_FAILURE", "summary", "[]", null, null, null, "[]", "{}");

		assertThat(issue.getId()).isNotNull();
		assertThat(issue.getQaResultId()).isEqualTo(qaResultId);
		assertThat(issue.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(issue.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(issue.getCode()).isEqualTo("TOOL_FAILURE");
		assertThat(issue.getSummary()).isEqualTo("summary");
		assertThat(issue.getAffectedDomainsJson()).isEqualTo("[]");
		assertThat(issue.getRequiredCheckRef()).isNull();
		assertThat(issue.getToolCapabilityRef()).isNull();
		assertThat(issue.getExecutionSurfaceRef()).isNull();
		assertThat(issue.getEvidenceRefsJson()).isEqualTo("[]");
		assertThat(issue.getProvenanceJson()).isEqualTo("{}");

		EvaluationIssue withRefs = new EvaluationIssue(
				qaResultId, qaExecutionId, testedCandidateId, "TOOL_FAILURE", "summary", "[]", "check-ref", "capability-ref", "surface-ref", "[]", "{}");
		assertThat(withRefs.getRequiredCheckRef()).isEqualTo("check-ref");
		assertThat(withRefs.getToolCapabilityRef()).isEqualTo("capability-ref");
		assertThat(withRefs.getExecutionSurfaceRef()).isEqualTo("surface-ref");
	}

	@Test
	void policyEvaluationExposesEveryFieldIncludingANullReasonCode() {
		UUID qaResultId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		PolicyEvaluation evaluation = new PolicyEvaluation(
				qaResultId, qaExecutionId, testedCandidateId, "profile-ref", "CANDIDATE_FINDING", "subject-ref", "rule-ref", "BLOCK", null);

		assertThat(evaluation.getId()).isNotNull();
		assertThat(evaluation.getQaResultId()).isEqualTo(qaResultId);
		assertThat(evaluation.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(evaluation.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(evaluation.getQaProfileRef()).isEqualTo("profile-ref");
		assertThat(evaluation.getSubjectType()).isEqualTo("CANDIDATE_FINDING");
		assertThat(evaluation.getSubjectRef()).isEqualTo("subject-ref");
		assertThat(evaluation.getPolicyRuleRef()).isEqualTo("rule-ref");
		assertThat(evaluation.getDisposition()).isEqualTo("BLOCK");
		assertThat(evaluation.getReasonCode()).isNull();

		PolicyEvaluation withReason = new PolicyEvaluation(
				qaResultId, qaExecutionId, testedCandidateId, "profile-ref", "CANDIDATE_FINDING", "subject-ref", "rule-ref", "BLOCK", "reason-1");
		assertThat(withReason.getReasonCode()).isEqualTo("reason-1");
	}

	@Test
	void qaResultExposesEveryField() {
		UUID id = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();
		UUID inputSnapshotId = UUID.randomUUID();
		UUID evidenceManifestId = UUID.randomUUID();

		QaResult result = new QaResult(
				id,
				qaExecutionId,
				testedCandidateId,
				"profile-ref",
				inputSnapshotId,
				"COMPLETE",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				"PASS",
				"[]",
				evidenceManifestId,
				"{}");

		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(result.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(result.getQaProfileRef()).isEqualTo("profile-ref");
		assertThat(result.getInputSnapshotId()).isEqualTo(inputSnapshotId);
		assertThat(result.getEvaluationState()).isEqualTo("COMPLETE");
		assertThat(result.getDomainResultsJson()).isEqualTo("[]");
		assertThat(result.getFindingRefsJson()).isEqualTo("[]");
		assertThat(result.getAuthorityIssueRefsJson()).isEqualTo("[]");
		assertThat(result.getEvaluationIssueRefsJson()).isEqualTo("[]");
		assertThat(result.getPolicyEvaluationRefsJson()).isEqualTo("[]");
		assertThat(result.getRemediationAssessmentRefsJson()).isEqualTo("[]");
		assertThat(result.getGateOutcome()).isEqualTo("PASS");
		assertThat(result.getHoldReasonsJson()).isEqualTo("[]");
		assertThat(result.getEvidenceManifestId()).isEqualTo(evidenceManifestId);
		assertThat(result.getProvenanceJson()).isEqualTo("{}");
	}

	@Test
	void remediationAssessmentExposesEveryFieldIncludingANullNotes() {
		UUID previousFindingId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID qaResultId = UUID.randomUUID();

		RemediationAssessment assessment = new RemediationAssessment(
				previousFindingId, testedCandidateId, qaExecutionId, qaResultId, "RESOLVED", "[]", "[]", "[]", null, "{}");

		assertThat(assessment.getId()).isNotNull();
		assertThat(assessment.getPreviousFindingId()).isEqualTo(previousFindingId);
		assertThat(assessment.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(assessment.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(assessment.getQaResultId()).isEqualTo(qaResultId);
		assertThat(assessment.getStatus()).isEqualTo("RESOLVED");
		assertThat(assessment.getEvidenceRefsJson()).isEqualTo("[]");
		assertThat(assessment.getRelatedNewFindingRefsJson()).isEqualTo("[]");
		assertThat(assessment.getEvaluationIssueRefsJson()).isEqualTo("[]");
		assertThat(assessment.getNotes()).isNull();
		assertThat(assessment.getProvenanceJson()).isEqualTo("{}");

		RemediationAssessment withNotes = new RemediationAssessment(
				previousFindingId, testedCandidateId, qaExecutionId, qaResultId, "PERSISTS", "[]", "[]", "[]", "still broken", "{}");
		assertThat(withNotes.getNotes()).isEqualTo("still broken");
	}
}
