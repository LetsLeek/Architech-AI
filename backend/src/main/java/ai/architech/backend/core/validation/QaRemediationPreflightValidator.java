package ai.architech.backend.core.validation;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AIW-180's own deterministic preflight for a {@code QA_REMEDIATION} {@code
 * developer-execution-input}, run before any AI invocation - same "invalid pre-execution state
 * prevents model invocation" boundary {@link QAExecutionPreflightValidator}/{@link
 * DeveloperExecutionInputValidator} already establish. Delegates the schema/canonical-upstream/
 * target-design checks {@link DeveloperExecutionInputValidator} already performs (unchanged by
 * this ticket, since {@code canonicalUpstream}/{@code targetDesign} keep the exact same shape for
 * both operations), then adds the {@code remediationContext}-specific checks {@code
 * developer-extension/QA_REMEDIATION.md}'s own preconditions name.
 *
 * <p>Every issue message is prefixed with the exact {@code developer-blocker.v1} code it
 * corresponds to ({@code INVALID_SOURCE_CANDIDATE}, {@code MISSING_REMEDIATION_AUTHORITY}, {@code
 * INVALID_FINDING_REFERENCE}, {@code REMEDIATION_SCOPE_INCOMPATIBLE}, {@code
 * REMEDIATION_AUTHORITY_CONFLICT}) - Core rejects these deterministically before ever spending a
 * model call on a request that cannot possibly succeed; the same five codes remain available in
 * the schema for the Developer Agent's own model-side judgment calls this preflight cannot make
 * (e.g. a scope question requiring semantic understanding of the repair itself).
 *
 * <p>A request with no {@code remediationContext} at all (an {@code INITIAL_GENERATION} input) is
 * passed straight through to the base validator's own result unchanged - this class only adds
 * checks, it never narrows what {@link DeveloperExecutionInputValidator} already accepts.
 */
@Component
public class QaRemediationPreflightValidator {

	private final DeveloperExecutionInputValidator baseValidator;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final CandidateFindingRepository candidateFindingRepository;
	private final ObjectMapper objectMapper;

	QaRemediationPreflightValidator(
			DeveloperExecutionInputValidator baseValidator,
			WebsiteImplementationCandidateRepository candidateRepository,
			CandidateFindingRepository candidateFindingRepository,
			ObjectMapper objectMapper) {
		this.baseValidator = baseValidator;
		this.candidateRepository = candidateRepository;
		this.candidateFindingRepository = candidateFindingRepository;
		this.objectMapper = objectMapper;
	}

	public PreExecutionValidationResult validate(UUID projectId, String developerExecutionInputJson) {
		PreExecutionValidationResult baseResult = baseValidator.validate(projectId, developerExecutionInputJson);
		List<PreExecutionValidationIssue> issues = new ArrayList<>(baseResult.issues());

		JsonNode input = objectMapper.readTree(developerExecutionInputJson);
		JsonNode remediationContext = input.path("remediationContext");
		if (remediationContext.isMissingNode()) {
			return new PreExecutionValidationResult(issues);
		}

		Optional<WebsiteImplementationCandidate> sourceCandidate =
				resolveSourceCandidate(remediationContext.path("sourceCandidateRef"), issues);
		if (sourceCandidate.isEmpty()) {
			return new PreExecutionValidationResult(issues);
		}

		validateSameVariantLineage(sourceCandidate.get(), input, issues);
		validateAuthorizedFindings(sourceCandidate.get(), remediationContext.path("authorizedFindingRefs"), issues);

		return new PreExecutionValidationResult(issues);
	}

	private Optional<WebsiteImplementationCandidate> resolveSourceCandidate(
			JsonNode refNode, List<PreExecutionValidationIssue> issues) {
		String ref = refNode.asString(null);
		UUID candidateId;
		try {
			candidateId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/sourceCandidateRef", "INVALID_SOURCE_CANDIDATE: not a resolvable Candidate reference: '" + ref + "'"));
			return Optional.empty();
		}

		Optional<WebsiteImplementationCandidate> candidate = candidateRepository.findById(candidateId);
		if (candidate.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/sourceCandidateRef", "INVALID_SOURCE_CANDIDATE: no Candidate exists for reference '" + ref + "'"));
			return Optional.empty();
		}
		return candidate;
	}

	private void validateSameVariantLineage(
			WebsiteImplementationCandidate sourceCandidate, JsonNode input, List<PreExecutionValidationIssue> issues) {
		String targetProposalLocalRef = input.path("targetDesign").path("targetProposalLocalRef").asString(null);
		if (!sourceCandidate.getSourceDesignProposalLocalRef().equals(targetProposalLocalRef)) {
			issues.add(new PreExecutionValidationIssue(
					"targetDesign/targetProposalLocalRef",
					"REMEDIATION_AUTHORITY_CONFLICT: targets Variant Lineage '" + targetProposalLocalRef
							+ "' but the source Candidate's own Variant Lineage is '" + sourceCandidate.getSourceDesignProposalLocalRef() + "'"));
		}
	}

	private void validateAuthorizedFindings(
			WebsiteImplementationCandidate sourceCandidate, JsonNode findingRefsNode, List<PreExecutionValidationIssue> issues) {
		if (!findingRefsNode.isArray() || findingRefsNode.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/authorizedFindingRefs",
					"MISSING_REMEDIATION_AUTHORITY: at least one Workflow-authorized Candidate Finding ref is required"));
			return;
		}

		int i = 0;
		for (JsonNode findingRefNode : findingRefsNode) {
			validateAuthorizedFinding(sourceCandidate, findingRefNode, i, issues);
			i++;
		}
	}

	private void validateAuthorizedFinding(
			WebsiteImplementationCandidate sourceCandidate, JsonNode findingRefNode, int index, List<PreExecutionValidationIssue> issues) {
		String ref = findingRefNode.asString(null);
		String path = "remediationContext/authorizedFindingRefs[" + index + "]";

		UUID findingId;
		try {
			findingId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(path, "INVALID_FINDING_REFERENCE: not a resolvable Finding reference: '" + ref + "'"));
			return;
		}

		Optional<CandidateFinding> finding = candidateFindingRepository.findById(findingId);
		if (finding.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(path, "INVALID_FINDING_REFERENCE: no Candidate Finding exists for reference '" + ref + "'"));
			return;
		}

		if (!finding.get().getTestedCandidateId().equals(sourceCandidate.getId())) {
			issues.add(new PreExecutionValidationIssue(
					path,
					"REMEDIATION_SCOPE_INCOMPATIBLE: Finding '" + ref + "' belongs to Candidate " + finding.get().getTestedCandidateId()
							+ ", not the source Candidate " + sourceCandidate.getId()));
		}
	}
}
