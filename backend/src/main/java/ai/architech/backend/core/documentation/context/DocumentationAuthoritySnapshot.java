package ai.architech.backend.core.documentation.context;

import java.util.Optional;

/**
 * Mirrors {@code documentation-context.schema.json}'s {@code authoritySnapshot} object
 * (AIW-189). {@code selectionDecisionRef}/{@code approvalRecordRef}/{@code deploymentRecordRef}
 * are always {@link Optional#empty()} in this codebase today - no {@code SelectionDecision}/
 * {@code ApprovalRecord}/{@code DeploymentRecord} Java type exists anywhere, by design (the
 * frozen package's own {@code MISSING_AUTHORITY} context-state mechanism represents their
 * absence; building that representation is AIW-190's job, not this one).
 */
public record DocumentationAuthoritySnapshot(
		Optional<String> customerProfileRef,
		String websiteRequirementsRef,
		String selectedSourceDesignRef,
		String implementationCandidateRef,
		String qaResultRef,
		Optional<String> selectionDecisionRef,
		Optional<String> approvalRecordRef,
		Optional<String> deploymentRecordRef) {}
