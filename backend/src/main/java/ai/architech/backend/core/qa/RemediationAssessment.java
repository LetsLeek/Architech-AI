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
 * One immutable Re-QA assessment of a prior {@link CandidateFinding} against a new Candidate
 * (AIW-168) - matches {@code remediation-assessment.schema.json}. {@code previousFindingId} is a
 * real foreign key into {@link CandidateFinding} (a genuine, never-mutated Finding from an older
 * {@code QaExecution}/{@code QaResult}); {@code testedCandidateId}/{@code qaExecutionId}/{@code
 * qaResultId} instead identify the <em>new</em> Candidate/execution/result this assessment was
 * produced by. Relating an old Finding to a new Candidate this way - rather than editing the old
 * Finding - is exactly what keeps {@code rules/lineage.md}'s "RemediationAssessment relates an
 * old Finding to a new Candidate without mutating history" true by construction. No setters, no
 * update methods.
 */
@Entity
@Table(name = "remediation_assessment")
public class RemediationAssessment {

	@Id
	private UUID id;

	@Column(name = "previous_finding_id", nullable = false, updatable = false)
	private UUID previousFindingId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "qa_result_id", nullable = false, updatable = false)
	private UUID qaResultId;

	@Column(name = "status", nullable = false, updatable = false)
	private String status;

	@Column(name = "evidence_refs_json", nullable = false, updatable = false)
	private String evidenceRefsJson;

	@Column(name = "related_new_finding_refs_json", nullable = false, updatable = false)
	private String relatedNewFindingRefsJson;

	@Column(name = "evaluation_issue_refs_json", nullable = false, updatable = false)
	private String evaluationIssueRefsJson;

	@Column(name = "notes", updatable = false)
	private String notes;

	@Column(name = "provenance_json", nullable = false, updatable = false)
	private String provenanceJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RemediationAssessment() {
		// required by JPA
	}

	public RemediationAssessment(
			UUID previousFindingId,
			UUID testedCandidateId,
			UUID qaExecutionId,
			UUID qaResultId,
			String status,
			String evidenceRefsJson,
			String relatedNewFindingRefsJson,
			String evaluationIssueRefsJson,
			String notes,
			String provenanceJson) {
		this.id = UUID.randomUUID();
		this.previousFindingId = Objects.requireNonNull(previousFindingId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.status = Objects.requireNonNull(status);
		this.evidenceRefsJson = Objects.requireNonNull(evidenceRefsJson);
		this.relatedNewFindingRefsJson = Objects.requireNonNull(relatedNewFindingRefsJson);
		this.evaluationIssueRefsJson = Objects.requireNonNull(evaluationIssueRefsJson);
		this.notes = notes;
		this.provenanceJson = Objects.requireNonNull(provenanceJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getPreviousFindingId() {
		return previousFindingId;
	}

	public UUID getTestedCandidateId() {
		return testedCandidateId;
	}

	public UUID getQaExecutionId() {
		return qaExecutionId;
	}

	public UUID getQaResultId() {
		return qaResultId;
	}

	public String getStatus() {
		return status;
	}

	public String getEvidenceRefsJson() {
		return evidenceRefsJson;
	}

	public String getRelatedNewFindingRefsJson() {
		return relatedNewFindingRefsJson;
	}

	public String getEvaluationIssueRefsJson() {
		return evaluationIssueRefsJson;
	}

	public String getNotes() {
		return notes;
	}

	public String getProvenanceJson() {
		return provenanceJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
