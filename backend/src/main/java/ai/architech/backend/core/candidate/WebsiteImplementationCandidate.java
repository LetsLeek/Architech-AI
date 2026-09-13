package ai.architech.backend.core.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The Website Developer Agent V1's own immutable acceptance record (AIW-145) - deliberately not
 * stored through the generic {@code core.artifact.ArtifactVersion} mechanism M1/M2 use, since
 * that mechanism auto-increments a new version on every call and has no notion of "exactly one
 * per AgentExecution", while this ticket's own acceptance criteria requires exactly that
 * (idempotent/retry-safe: one successful execution produces exactly one Candidate, ever - see
 * {@link WebsiteImplementationCandidateRepository}'s unique constraint on
 * {@code agentExecutionId}).
 *
 * <p>Contains no preview/QA/selection/approval/deployment lifecycle field, per this ticket's own
 * acceptance criteria - those are a separate, later concern this row deliberately says nothing
 * about. No setters: a correction never edits this row, it always produces a new
 * {@code AgentExecution} attempt (AIW-149) and, if that succeeds, an entirely new Candidate.
 */
@Entity
@Table(name = "website_implementation_candidate")
public class WebsiteImplementationCandidate {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "source_design_artifact_version_ref", nullable = false, updatable = false)
	private String sourceDesignArtifactVersionRef;

	@Column(name = "source_design_proposal_local_ref", nullable = false, updatable = false)
	private String sourceDesignProposalLocalRef;

	@Column(name = "runtime_profile_ref", nullable = false, updatable = false)
	private String runtimeProfileRef;

	@Column(name = "repository_state_ref", nullable = false, updatable = false)
	private String repositoryStateRef;

	@Column(name = "implementation_summary", nullable = false, updatable = false)
	private String implementationSummary;

	@Column(name = "implementation_anchors", nullable = false, updatable = false)
	private String implementationAnchors;

	@Column(name = "functional_bindings", nullable = false, updatable = false)
	private String functionalBindings;

	@Column(name = "unresolved_issues", nullable = false, updatable = false)
	private String unresolvedIssues;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WebsiteImplementationCandidate() {
		// required by JPA
	}

	public WebsiteImplementationCandidate(
			UUID projectId,
			UUID agentExecutionId,
			String sourceDesignArtifactVersionRef,
			String sourceDesignProposalLocalRef,
			String runtimeProfileRef,
			String repositoryStateRef,
			String implementationSummary,
			String implementationAnchors,
			String functionalBindings,
			String unresolvedIssues) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.agentExecutionId = agentExecutionId;
		this.sourceDesignArtifactVersionRef = sourceDesignArtifactVersionRef;
		this.sourceDesignProposalLocalRef = sourceDesignProposalLocalRef;
		this.runtimeProfileRef = runtimeProfileRef;
		this.repositoryStateRef = repositoryStateRef;
		this.implementationSummary = implementationSummary;
		this.implementationAnchors = implementationAnchors;
		this.functionalBindings = functionalBindings;
		this.unresolvedIssues = unresolvedIssues;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getAgentExecutionId() {
		return agentExecutionId;
	}

	public String getSourceDesignArtifactVersionRef() {
		return sourceDesignArtifactVersionRef;
	}

	public String getSourceDesignProposalLocalRef() {
		return sourceDesignProposalLocalRef;
	}

	public String getRuntimeProfileRef() {
		return runtimeProfileRef;
	}

	public String getRepositoryStateRef() {
		return repositoryStateRef;
	}

	public String getImplementationSummary() {
		return implementationSummary;
	}

	public String getImplementationAnchors() {
		return implementationAnchors;
	}

	public String getFunctionalBindings() {
		return functionalBindings;
	}

	public String getUnresolvedIssues() {
		return unresolvedIssues;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
