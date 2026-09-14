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
 * The Website QA V1 identity/binding row for exactly one QA {@code AgentExecution} attempt
 * (AIW-168) - the generic platform execution lifecycle (RUNNING/SUCCEEDED/FAILED/BLOCKED/ERROR)
 * stays on {@code AgentExecution} itself (reused unchanged, the same way it already is for
 * Requirements/Designer/Developer); this row carries only what is specific to a QA attempt and
 * absent from the generic entity - the exact immutable {@link
 * ai.architech.backend.core.candidate.WebsiteImplementationCandidate} being tested, and the
 * QA-specific profile/tool-capability bindings a QA execution's own {@code
 * qa-execution-input.v1} carries.
 *
 * <p>Immutable after creation - no setters, no update methods - matching every other evidence
 * row in this codebase ({@code ToolExecution}, {@code RunnerVerificationRun}, {@code
 * WebsiteImplementationCandidate}: correlated by a plain {@code agentExecutionId} column, never a
 * JPA relationship.
 */
@Entity
@Table(name = "qa_execution")
public class QaExecution {

	@Id
	private UUID id;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Column(name = "qa_profile_ref", nullable = false, updatable = false)
	private String qaProfileRef;

	@Column(name = "execution_surface_ref", updatable = false)
	private String executionSurfaceRef;

	@Column(name = "tool_capability_profile_ref", nullable = false, updatable = false)
	private String toolCapabilityProfileRef;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QaExecution() {
		// required by JPA
	}

	public QaExecution(
			UUID agentExecutionId,
			UUID testedCandidateId,
			String qaProfileRef,
			String executionSurfaceRef,
			String toolCapabilityProfileRef) {
		this.id = UUID.randomUUID();
		this.agentExecutionId = Objects.requireNonNull(agentExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.qaProfileRef = Objects.requireNonNull(qaProfileRef);
		this.executionSurfaceRef = executionSurfaceRef;
		this.toolCapabilityProfileRef = Objects.requireNonNull(toolCapabilityProfileRef);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getAgentExecutionId() {
		return agentExecutionId;
	}

	public UUID getTestedCandidateId() {
		return testedCandidateId;
	}

	public String getQaProfileRef() {
		return qaProfileRef;
	}

	public String getExecutionSurfaceRef() {
		return executionSurfaceRef;
	}

	public String getToolCapabilityProfileRef() {
		return toolCapabilityProfileRef;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
