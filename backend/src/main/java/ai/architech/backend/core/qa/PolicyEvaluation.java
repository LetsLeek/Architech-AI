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
 * One immutable Core-computed disposition ({@code BLOCK|ALLOW|ESCALATE}) for exactly one
 * {@link CandidateFinding}/{@link AuthorityIssue}/{@link EvaluationIssue} subject (AIW-168) -
 * matches {@code policy-evaluation.schema.json}. Produced only by the deterministic {@code
 * QAPolicyAggregator} (AIW-176, "MUST NOT use generative AI") - never by the semantic Agent - and
 * never deleted or downgraded once persisted ({@code rules/gate-semantics.md}'s "Allowed Findings
 * remain persisted and are not deleted/downgraded"). No setters, no update methods.
 */
@Entity
@Table(name = "policy_evaluation")
public class PolicyEvaluation {

	@Id
	private UUID id;

	@Column(name = "qa_result_id", nullable = false, updatable = false)
	private UUID qaResultId;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "qa_profile_ref", nullable = false, updatable = false)
	private String qaProfileRef;

	@Column(name = "subject_type", nullable = false, updatable = false)
	private String subjectType;

	@Column(name = "subject_ref", nullable = false, updatable = false)
	private String subjectRef;

	@Column(name = "policy_rule_ref", nullable = false, updatable = false)
	private String policyRuleRef;

	@Column(name = "disposition", nullable = false, updatable = false)
	private String disposition;

	@Column(name = "reason_code", updatable = false)
	private String reasonCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PolicyEvaluation() {
		// required by JPA
	}

	public PolicyEvaluation(
			UUID qaResultId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			String qaProfileRef,
			String subjectType,
			String subjectRef,
			String policyRuleRef,
			String disposition,
			String reasonCode) {
		this.id = UUID.randomUUID();
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.qaProfileRef = Objects.requireNonNull(qaProfileRef);
		this.subjectType = Objects.requireNonNull(subjectType);
		this.subjectRef = Objects.requireNonNull(subjectRef);
		this.policyRuleRef = Objects.requireNonNull(policyRuleRef);
		this.disposition = Objects.requireNonNull(disposition);
		this.reasonCode = reasonCode;
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

	public String getQaProfileRef() {
		return qaProfileRef;
	}

	public String getSubjectType() {
		return subjectType;
	}

	public String getSubjectRef() {
		return subjectRef;
	}

	public String getPolicyRuleRef() {
		return policyRuleRef;
	}

	public String getDisposition() {
		return disposition;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
