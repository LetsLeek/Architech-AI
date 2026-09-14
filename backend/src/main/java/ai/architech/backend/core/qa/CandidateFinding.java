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
 * One immutable, evidence-backed Candidate defect (AIW-168) - matches {@code
 * candidate-finding.schema.json} exactly (this row's own {@code id} is the schema's {@code
 * findingId}). No setters and no update methods: a Finding can never be moved to another
 * Candidate or mutated to "resolved" in place - correcting a defect always produces a {@code
 * RemediationAssessment} relating this same, unchanged row to a later Candidate's own new
 * Finding (or lack thereof), never an edit here (AIW-168's own "Findings cannot be moved to
 * another Candidate or mutated to resolved" acceptance criterion, and {@code rules/findings.md}'s
 * "Findings are immutable and Candidate-bound").
 *
 * <p>Structured substructure ({@code normativeBasis}, {@code context}, {@code evidenceRefs},
 * {@code provenance}) is stored as JSON text, the same idiom {@code
 * WebsiteImplementationCandidate}'s own {@code implementationAnchors}/{@code functionalBindings}
 * already use - only the fields a later validator/policy ticket needs to filter or compare on
 * ({@code findingCode}, {@code primaryDomain}, {@code severity}, {@code fingerprint}) are their
 * own typed columns.
 */
@Entity
@Table(name = "candidate_finding")
public class CandidateFinding {

	@Id
	private UUID id;

	@Column(name = "qa_result_id", nullable = false, updatable = false)
	private UUID qaResultId;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "finding_code", nullable = false, updatable = false)
	private String findingCode;

	@Column(name = "primary_domain", nullable = false, updatable = false)
	private String primaryDomain;

	@Column(name = "severity", nullable = false, updatable = false)
	private String severity;

	@Column(name = "normative_basis_json", nullable = false, updatable = false)
	private String normativeBasisJson;

	@Column(name = "summary", nullable = false, updatable = false)
	private String summary;

	@Column(name = "diagnostic_details", updatable = false)
	private String diagnosticDetails;

	@Column(name = "context_json", updatable = false)
	private String contextJson;

	@Column(name = "evidence_refs_json", nullable = false, updatable = false)
	private String evidenceRefsJson;

	@Column(name = "fingerprint", nullable = false, updatable = false)
	private String fingerprint;

	@Column(name = "provenance_json", nullable = false, updatable = false)
	private String provenanceJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CandidateFinding() {
		// required by JPA
	}

	public CandidateFinding(
			UUID qaResultId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			String findingCode,
			String primaryDomain,
			String severity,
			String normativeBasisJson,
			String summary,
			String diagnosticDetails,
			String contextJson,
			String evidenceRefsJson,
			String fingerprint,
			String provenanceJson) {
		this.id = UUID.randomUUID();
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.findingCode = Objects.requireNonNull(findingCode);
		this.primaryDomain = Objects.requireNonNull(primaryDomain);
		this.severity = Objects.requireNonNull(severity);
		this.normativeBasisJson = Objects.requireNonNull(normativeBasisJson);
		this.summary = Objects.requireNonNull(summary);
		this.diagnosticDetails = diagnosticDetails;
		this.contextJson = contextJson;
		this.evidenceRefsJson = Objects.requireNonNull(evidenceRefsJson);
		this.fingerprint = Objects.requireNonNull(fingerprint);
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

	public String getFindingCode() {
		return findingCode;
	}

	public String getPrimaryDomain() {
		return primaryDomain;
	}

	public String getSeverity() {
		return severity;
	}

	public String getNormativeBasisJson() {
		return normativeBasisJson;
	}

	public String getSummary() {
		return summary;
	}

	public String getDiagnosticDetails() {
		return diagnosticDetails;
	}

	public String getContextJson() {
		return contextJson;
	}

	public String getEvidenceRefsJson() {
		return evidenceRefsJson;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public String getProvenanceJson() {
		return provenanceJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
