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
 * One immutable record of a required evaluation that could not be reliably completed (AIW-168) -
 * matches {@code evaluation-issue.schema.json}. Structurally distinct from {@link
 * CandidateFinding}: tool failure or execution-surface drift is never treated as a Candidate
 * defect ({@code rules/evidence.md}'s "Tool failure is not positive Evidence"). No setters, no
 * update methods.
 */
@Entity
@Table(name = "evaluation_issue")
public class EvaluationIssue {

	@Id
	private UUID id;

	@Column(name = "qa_result_id", nullable = false, updatable = false)
	private UUID qaResultId;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "code", nullable = false, updatable = false)
	private String code;

	@Column(name = "summary", nullable = false, updatable = false)
	private String summary;

	@Column(name = "affected_domains_json", nullable = false, updatable = false)
	private String affectedDomainsJson;

	@Column(name = "required_check_ref", updatable = false)
	private String requiredCheckRef;

	@Column(name = "tool_capability_ref", updatable = false)
	private String toolCapabilityRef;

	@Column(name = "execution_surface_ref", updatable = false)
	private String executionSurfaceRef;

	@Column(name = "evidence_refs_json", nullable = false, updatable = false)
	private String evidenceRefsJson;

	@Column(name = "provenance_json", nullable = false, updatable = false)
	private String provenanceJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EvaluationIssue() {
		// required by JPA
	}

	public EvaluationIssue(
			UUID qaResultId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			String code,
			String summary,
			String affectedDomainsJson,
			String requiredCheckRef,
			String toolCapabilityRef,
			String executionSurfaceRef,
			String evidenceRefsJson,
			String provenanceJson) {
		this.id = UUID.randomUUID();
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.code = Objects.requireNonNull(code);
		this.summary = Objects.requireNonNull(summary);
		this.affectedDomainsJson = Objects.requireNonNull(affectedDomainsJson);
		this.requiredCheckRef = requiredCheckRef;
		this.toolCapabilityRef = toolCapabilityRef;
		this.executionSurfaceRef = executionSurfaceRef;
		this.evidenceRefsJson = Objects.requireNonNull(evidenceRefsJson);
		this.provenanceJson = Objects.requireNonNull(provenanceJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getQaResultId() {
		return qaResultId;
	}

	public UUID getQaExecutionId() {
		return qaExecutionId;
	}

	public UUID getTestedCandidateId() {
		return testedCandidateId;
	}

	public String getCode() {
		return code;
	}

	public String getSummary() {
		return summary;
	}

	public String getAffectedDomainsJson() {
		return affectedDomainsJson;
	}

	public String getRequiredCheckRef() {
		return requiredCheckRef;
	}

	public String getToolCapabilityRef() {
		return toolCapabilityRef;
	}

	public String getExecutionSurfaceRef() {
		return executionSurfaceRef;
	}

	public String getEvidenceRefsJson() {
		return evidenceRefsJson;
	}

	public String getProvenanceJson() {
		return provenanceJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
