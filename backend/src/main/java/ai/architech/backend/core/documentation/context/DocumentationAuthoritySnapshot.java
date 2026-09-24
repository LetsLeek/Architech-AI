package ai.architech.backend.core.documentation.context;

import java.util.Optional;

/**
 * Mirrors {@code documentation-context.schema.json}'s {@code authoritySnapshot} object
 * (AIW-189, corrected in AIW-190) - every field is a {@link DocumentationArtifactRef}
 * ({@code artifact-ref.schema.json}'s {@code {artifactType, artifactVersionRef}} shape), not a
 * plain ref string. {@code selectionDecisionRef}/{@code approvalRecordRef}/{@code
 * deploymentRecordRef} are always {@link Optional#empty()} in this codebase today - no {@code
 * SelectionDecision}/{@code ApprovalRecord}/{@code DeploymentRecord} Java type exists anywhere, by
 * design (the frozen package's own {@code MISSING_AUTHORITY} context-state mechanism represents
 * their absence - AIW-190's own job).
 */
public record DocumentationAuthoritySnapshot(
		Optional<DocumentationArtifactRef> customerProfileRef,
		DocumentationArtifactRef websiteRequirementsRef,
		DocumentationArtifactRef selectedSourceDesignRef,
		DocumentationArtifactRef implementationCandidateRef,
		DocumentationArtifactRef qaResultRef,
		Optional<DocumentationArtifactRef> selectionDecisionRef,
		Optional<DocumentationArtifactRef> approvalRecordRef,
		Optional<DocumentationArtifactRef> deploymentRecordRef) {}
