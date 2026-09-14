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
 * One immutable record of missing, invalid or conflicting Product Authority discovered during a
 * QA execution (AIW-168) - matches {@code authority-issue.schema.json}. Structurally distinct
 * from {@link CandidateFinding}: an AuthorityIssue is never converted into a Candidate defect
 * (missing authority is never the Candidate's fault - {@code rules/authority.md}'s "MUST NOT
 * convert missing authority into a Candidate Finding"). No setters, no update methods.
 */
@Entity
@Table(name = "authority_issue")
public class AuthorityIssue {

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

	@Column(name = "affected_authority_refs_json", nullable = false, updatable = false)
	private String affectedAuthorityRefsJson;

	@Column(name = "expected_authority_types_json", nullable = false, updatable = false)
	private String expectedAuthorityTypesJson;

	@Column(name = "affected_domains_json", nullable = false, updatable = false)
	private String affectedDomainsJson;

	@Column(name = "evidence_refs_json", nullable = false, updatable = false)
	private String evidenceRefsJson;

	@Column(name = "provenance_json", nullable = false, updatable = false)
	private String provenanceJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuthorityIssue() {
		// required by JPA
	}

	public AuthorityIssue(
			UUID qaResultId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			String code,
			String summary,
			String affectedAuthorityRefsJson,
			String expectedAuthorityTypesJson,
			String affectedDomainsJson,
			String evidenceRefsJson,
			String provenanceJson) {
		this.id = UUID.randomUUID();
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.code = Objects.requireNonNull(code);
		this.summary = Objects.requireNonNull(summary);
		this.affectedAuthorityRefsJson = Objects.requireNonNull(affectedAuthorityRefsJson);
		this.expectedAuthorityTypesJson = Objects.requireNonNull(expectedAuthorityTypesJson);
		this.affectedDomainsJson = Objects.requireNonNull(affectedDomainsJson);
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

	public String getAffectedAuthorityRefsJson() {
		return affectedAuthorityRefsJson;
	}

	public String getExpectedAuthorityTypesJson() {
		return expectedAuthorityTypesJson;
	}

	public String getAffectedDomainsJson() {
		return affectedDomainsJson;
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
