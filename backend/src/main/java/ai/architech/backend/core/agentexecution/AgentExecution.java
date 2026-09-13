package ai.architech.backend.core.agentexecution;

import ai.architech.backend.core.ai.AiResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt at running an agent. Project-type-agnostic - any agent, not just the
 * Requirements Agent, gets one of these per attempt. A retry is always a new AgentExecution,
 * never a mutation of a previous one (see AIW-38): this row is itself immutable evidence
 * of what happened during exactly one attempt.
 *
 * <p>Success is intentionally hard to reach: {@link #succeed()} only works from RUNNING, and
 * nothing here ever infers success just because a model call returned - that requires the
 * Runner to have gone all the way through validation and persistence first (see
 * RUNNER_VALIDATION_CONTRACT.md's Success Boundary).
 */
@Entity
@Table(name = "agent_execution")
public class AgentExecution {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "agent_id", nullable = false, updatable = false)
	private String agentId;

	@Column(name = "agent_version", nullable = false, updatable = false)
	private int agentVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private AgentExecutionStatus status;

	@Column(name = "provider")
	private String provider;

	@Column(name = "model")
	private String model;

	@Column(name = "prompt_tokens")
	private Integer promptTokens;

	@Column(name = "completion_tokens")
	private Integer completionTokens;

	@Column(name = "cost_usd")
	private BigDecimal costUsd;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "retry_of_execution_id")
	private UUID retryOfExecutionId;

	@Column(name = "retry_reason_code")
	private String retryReasonCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected AgentExecution() {
		// required by JPA
	}

	public AgentExecution(UUID projectId, String agentId, int agentVersion) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.agentId = agentId;
		this.agentVersion = agentVersion;
		this.status = AgentExecutionStatus.PENDING;
	}

	/**
	 * A Workflow-initiated retry (AIW-149): always a brand new row with its own id/PENDING
	 * status, never a mutation of {@code retryOfExecutionId} - the prior execution stays exactly
	 * as it finished, forever. {@code retryReasonCode} matches the frozen
	 * {@code developer-execution-input.v1} schema's {@code retryContext.retryReasonCode} enum
	 * ({@code RESULT_VALIDATION_FAILURE}/{@code RUNNER_VERIFICATION_FAILURE}) but is stored as a
	 * plain string here, the same way {@link AgentExecutionStatus} itself is - Core, not this
	 * entity, owns interpreting it.
	 */
	public AgentExecution(UUID projectId, String agentId, int agentVersion, UUID retryOfExecutionId, String retryReasonCode) {
		this(projectId, agentId, agentVersion);
		this.retryOfExecutionId = retryOfExecutionId;
		this.retryReasonCode = retryReasonCode;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void start() {
		requireStatus(AgentExecutionStatus.PENDING);
		this.status = AgentExecutionStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	/** Provider/model/token/cost are associable independently of the state machine - call whenever that data becomes known. */
	public void recordModelUsage(
			String provider, String model, Integer promptTokens, Integer completionTokens, BigDecimal costUsd) {
		this.provider = provider;
		this.model = model;
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		this.costUsd = costUsd;
	}

	/** Convenience overload: pulls provider/model/token counts straight from an AI Gateway response. */
	public void recordModelUsage(AiResponse response, BigDecimal costUsd) {
		recordModelUsage(
				response.provider(), response.model(), response.promptTokens(), response.completionTokens(), costUsd);
	}

	public void succeed() {
		requireStatus(AgentExecutionStatus.RUNNING);
		this.status = AgentExecutionStatus.SUCCEEDED;
		this.finishedAt = Instant.now();
	}

	public void fail(String reason) {
		requireNotAlreadyFinished("fail");
		this.status = AgentExecutionStatus.FAILED;
		this.failureReason = reason;
		this.finishedAt = Instant.now();
	}

	/**
	 * A valid semantic {@code BLOCKED} DeveloperAgentResult (AIW-149) - a genuine nonlocal
	 * completion blocker, never a Developer-owned defect ({@link #fail}) - produces no Candidate.
	 * Only reachable from RUNNING, exactly like {@link #succeed}: both require the model to have
	 * actually run and produced a valid semantic result first.
	 */
	public void block(String reason) {
		requireStatus(AgentExecutionStatus.RUNNING);
		this.status = AgentExecutionStatus.BLOCKED;
		this.failureReason = reason;
		this.finishedAt = Instant.now();
	}

	/**
	 * A Runner/sandbox/Core infrastructure malfunction (AIW-149) - distinct from a Developer-
	 * owned defect ({@link #fail}) precisely so a caller never has to guess which one happened
	 * from the status alone, and so {@code ERROR} never triggers speculative source edits the
	 * way a source-level {@code FAILED} correction might. Reachable from PENDING or RUNNING:
	 * infrastructure can fail before the model is ever invoked.
	 */
	public void error(String reason) {
		requireNotAlreadyFinished("error");
		this.status = AgentExecutionStatus.ERROR;
		this.failureReason = reason;
		this.finishedAt = Instant.now();
	}

	private void requireStatus(AgentExecutionStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Expected status " + expected + " but was " + status);
		}
	}

	private void requireNotAlreadyFinished(String action) {
		if (isTerminal()) {
			throw new IllegalStateException("Cannot " + action + " an execution that already finished as " + status);
		}
	}

	private boolean isTerminal() {
		return status == AgentExecutionStatus.SUCCEEDED
				|| status == AgentExecutionStatus.BLOCKED
				|| status == AgentExecutionStatus.FAILED
				|| status == AgentExecutionStatus.ERROR;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getAgentId() {
		return agentId;
	}

	public int getAgentVersion() {
		return agentVersion;
	}

	public AgentExecutionStatus getStatus() {
		return status;
	}

	public String getProvider() {
		return provider;
	}

	public String getModel() {
		return model;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}

	public BigDecimal getCostUsd() {
		return costUsd;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public UUID getRetryOfExecutionId() {
		return retryOfExecutionId;
	}

	public String getRetryReasonCode() {
		return retryReasonCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
