package ai.architech.backend.core.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable, authoritative outcome of one QA execution (AIW-168) - matches {@code
 * qa-result.schema.json}. {@code findingRefs}/{@code authorityIssueRefs}/{@code
 * evaluationIssueRefs}/{@code policyEvaluationRefs}/{@code remediationAssessmentRefs} are stored
 * exactly as the frozen schema itself models them - JSON arrays of id-ref strings pointing at the
 * already-independently-persisted {@link CandidateFinding}/{@link AuthorityIssue}/{@link
 * EvaluationIssue}/{@link PolicyEvaluation}/{@code RemediationAssessment} rows - never as
 * duplicated copies of that content on this row (AIW-168's own "QAResult references Findings/
 * Issues/Policy Evaluations rather than duplicating authoritative records" acceptance criterion).
 * {@code domainResults} is the one exception: the frozen schema embeds full {@code DomainResult}
 * objects inline (not refs), so it is stored here as a JSON blob rather than a separate table -
 * AIW-168's own scope does not name a standalone DomainResult entity.
 *
 * <p>{@code gateOutcome}/{@code holdReasons} are the Core-owned {@code PASS|HOLD} aggregation
 * ({@code rules/gate-semantics.md}: "the semantic QA Agent never determines an authoritative QA
 * Gate outcome") - produced by the deterministic {@code QAPolicyAggregator} (AIW-176), which this
 * ticket's persistence model exists ahead of but does not itself compute. No setters, no update
 * methods: a QA conclusion is permanent evidence, never revised in place.
 *
 * <p>This row is history, not a "current" pointer: which {@code QaResult} is workflow-relevant
 * for a given Variant Lineage/release path is a separate, later concern (AIW-177/178's own
 * Comparison-Readiness/Full-Release workflow pointers) - deliberately not modeled here, matching
 * AIW-168's own "workflow-current pointers remain separate from historical QA artifacts"
 * acceptance criterion.
 */
@Entity
@Table(name = "qa_result")
public class QaResult {

	@Id
	private UUID id;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "qa_profile_ref", nullable = false, updatable = false)
	private String qaProfileRef;

	@Column(name = "input_snapshot_id", nullable = false, updatable = false)
	private UUID inputSnapshotId;

	@Column(name = "evaluation_state", nullable = false, updatable = false)
	private String evaluationState;

	@Column(name = "domain_results_json", nullable = false, updatable = false)
	private String domainResultsJson;

	@Column(name = "finding_refs_json", nullable = false, updatable = false)
	private String findingRefsJson;

	@Column(name = "authority_issue_refs_json", nullable = false, updatable = false)
	private String authorityIssueRefsJson;

	@Column(name = "evaluation_issue_refs_json", nullable = false, updatable = false)
	private String evaluationIssueRefsJson;

	@Column(name = "policy_evaluation_refs_json", nullable = false, updatable = false)
	private String policyEvaluationRefsJson;

	@Column(name = "remediation_assessment_refs_json", nullable = false, updatable = false)
	private String remediationAssessmentRefsJson;

	@Column(name = "gate_outcome", nullable = false, updatable = false)
	private String gateOutcome;

	@Column(name = "hold_reasons_json", nullable = false, updatable = false)
	private String holdReasonsJson;

	@Column(name = "evidence_manifest_id", nullable = false, updatable = false)
	private UUID evidenceManifestId;

	@Column(name = "provenance_json", nullable = false, updatable = false)
	private String provenanceJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QaResult() {
		// required by JPA
	}

	/**
	 * {@code id} is caller-supplied rather than self-generated (the one deliberate exception to
	 * this package's usual "the constructor generates its own id" idiom): the frozen schema makes
	 * {@code QaResult} and its Finding/Issue/PolicyEvaluation children mutually reference each
	 * other by id ({@code findingRefs} etc. here, {@code qaResultRef} required on every child) -
	 * genuinely circular, not resolvable by insert order alone. The caller decides this row's id
	 * up front (via {@code UUID.randomUUID()}, exactly what every other entity's constructor does
	 * internally), builds every child object in memory with that id already known, harvests each
	 * child's own self-generated id for these ref fields, then saves this row before saving the
	 * children (satisfying their own foreign keys back to this one).
	 */
	public QaResult(
			UUID id,
			UUID qaExecutionId,
			UUID testedCandidateId,
			String qaProfileRef,
			UUID inputSnapshotId,
			String evaluationState,
			String domainResultsJson,
			String findingRefsJson,
			String authorityIssueRefsJson,
			String evaluationIssueRefsJson,
			String policyEvaluationRefsJson,
			String remediationAssessmentRefsJson,
			String gateOutcome,
			String holdReasonsJson,
			UUID evidenceManifestId,
			String provenanceJson) {
		this.id = Objects.requireNonNull(id);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.qaProfileRef = Objects.requireNonNull(qaProfileRef);
		this.inputSnapshotId = Objects.requireNonNull(inputSnapshotId);
		this.evaluationState = Objects.requireNonNull(evaluationState);
		this.domainResultsJson = Objects.requireNonNull(domainResultsJson);
		this.findingRefsJson = Objects.requireNonNull(findingRefsJson);
		this.authorityIssueRefsJson = Objects.requireNonNull(authorityIssueRefsJson);
		this.evaluationIssueRefsJson = Objects.requireNonNull(evaluationIssueRefsJson);
		this.policyEvaluationRefsJson = Objects.requireNonNull(policyEvaluationRefsJson);
		this.remediationAssessmentRefsJson = Objects.requireNonNull(remediationAssessmentRefsJson);
		this.gateOutcome = Objects.requireNonNull(gateOutcome);
		this.holdReasonsJson = Objects.requireNonNull(holdReasonsJson);
		this.evidenceManifestId = Objects.requireNonNull(evidenceManifestId);
		this.provenanceJson = Objects.requireNonNull(provenanceJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getQaExecutionId() {
		return qaExecutionId;
	}

	public UUID getTestedCandidateId() {
		return testedCandidateId;
	}

	public String getQaProfileRef() {
		return qaProfileRef;
	}

	public UUID getInputSnapshotId() {
		return inputSnapshotId;
	}

	public String getEvaluationState() {
		return evaluationState;
	}

	public String getDomainResultsJson() {
		return domainResultsJson;
	}

	public String getFindingRefsJson() {
		return findingRefsJson;
	}

	public String getAuthorityIssueRefsJson() {
		return authorityIssueRefsJson;
	}

	public String getEvaluationIssueRefsJson() {
		return evaluationIssueRefsJson;
	}

	public String getPolicyEvaluationRefsJson() {
		return policyEvaluationRefsJson;
	}

	public String getRemediationAssessmentRefsJson() {
		return remediationAssessmentRefsJson;
	}

	public String getGateOutcome() {
		return gateOutcome;
	}

	public String getHoldReasonsJson() {
		return holdReasonsJson;
	}

	public UUID getEvidenceManifestId() {
		return evidenceManifestId;
	}

	public String getProvenanceJson() {
		return provenanceJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
