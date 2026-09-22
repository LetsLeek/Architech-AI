package ai.architech.backend.core.validation;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code core/VALIDATORS.md}'s own {@code RemediationLineageValidator}: "Validates previous
 * Finding/Candidate lineage, same Variant Lineage, stable Product Authority/Source Design for
 * normal QA_REMEDIATION, and current Candidate ownership of related Findings/Evidence" (AIW-179).
 * Delegates the base {@code qa-execution-input} checks to the already-real {@link
 * QAExecutionPreflightValidator} (AIW-169) and only adds checks specific to the frozen schema's
 * own optional {@code remediationContext} block ({@code previousCandidateRef}/{@code
 * previousQAResultRef}/{@code previousFindingRefs}, present in {@code qa-execution-input.schema.json}
 * since AIW-167's own frozen-package integration).
 *
 * <p>Product Authority baseline stability (Customer Profile/Website Requirements staying
 * unchanged across a remediation cycle) is not independently re-checkable here: {@link
 * WebsiteImplementationCandidate} tracks its own exact Source Design/Runtime bindings but not
 * which upstream Customer Profile/Website Requirements artifact versions its originating
 * execution used - the same gap {@code DeveloperExecutionInputAssembler#assembleForRemediation}
 * already documents for AIW-180. Source Design and Variant Lineage stability *are* fully
 * checkable (both are stored directly on {@link WebsiteImplementationCandidate}) and are enforced
 * here.
 */
@Component
public class RemediationLineagePreflightValidator {

	private final QAExecutionPreflightValidator baseValidator;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final QaResultRepository qaResultRepository;
	private final CandidateFindingRepository candidateFindingRepository;
	private final ObjectMapper objectMapper;

	RemediationLineagePreflightValidator(
			QAExecutionPreflightValidator baseValidator,
			WebsiteImplementationCandidateRepository candidateRepository,
			QaResultRepository qaResultRepository,
			CandidateFindingRepository candidateFindingRepository,
			ObjectMapper objectMapper) {
		this.baseValidator = baseValidator;
		this.candidateRepository = candidateRepository;
		this.qaResultRepository = qaResultRepository;
		this.candidateFindingRepository = candidateFindingRepository;
		this.objectMapper = objectMapper;
	}

	public PreExecutionValidationResult validate(UUID projectId, String qaExecutionInputJson) {
		PreExecutionValidationResult baseResult = baseValidator.validate(projectId, qaExecutionInputJson);
		List<PreExecutionValidationIssue> issues = new ArrayList<>(baseResult.issues());

		JsonNode input = objectMapper.readTree(qaExecutionInputJson);
		JsonNode remediationContext = input.path("remediationContext");
		if (remediationContext.isMissingNode()) {
			return new PreExecutionValidationResult(issues);
		}

		Optional<WebsiteImplementationCandidate> previousCandidate =
				resolveCandidate(remediationContext.path("previousCandidateRef"), "remediationContext/previousCandidateRef", issues);
		resolveQaResult(remediationContext.path("previousQAResultRef"), issues);
		validatePreviousFindings(remediationContext.path("previousFindingRefs"), previousCandidate.orElse(null), issues);

		// Target Candidate resolution issues are already surfaced by the base validator
		// (QAExecutionPreflightValidator) under "target/candidateRef" - this is a quiet re-lookup
		// purely to compare it against the previous Candidate, not a second validation of the ref.
		Optional<WebsiteImplementationCandidate> targetCandidate = quietlyResolveCandidate(input.path("target").path("candidateRef"));
		if (targetCandidate.isPresent() && previousCandidate.isPresent()) {
			validateSameVariantLineage(targetCandidate.get(), previousCandidate.get(), issues);
			validateSameSourceDesign(targetCandidate.get(), previousCandidate.get(), issues);
		}

		return new PreExecutionValidationResult(issues);
	}

	private Optional<WebsiteImplementationCandidate> resolveCandidate(
			JsonNode refNode, String path, List<PreExecutionValidationIssue> issues) {
		String ref = refNode.asString(null);
		UUID candidateId;
		try {
			candidateId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(path, "not a resolvable Candidate reference: '" + ref + "'"));
			return Optional.empty();
		}
		Optional<WebsiteImplementationCandidate> candidate = candidateRepository.findById(candidateId);
		if (candidate.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(path, "no Candidate exists for reference '" + ref + "'"));
		}
		return candidate;
	}

	private Optional<WebsiteImplementationCandidate> quietlyResolveCandidate(JsonNode refNode) {
		try {
			return candidateRepository.findById(UUID.fromString(refNode.asString(null)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private void resolveQaResult(JsonNode refNode, List<PreExecutionValidationIssue> issues) {
		String ref = refNode.asString(null);
		UUID qaResultId;
		try {
			qaResultId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/previousQAResultRef", "not a resolvable QA Result reference: '" + ref + "'"));
			return;
		}
		Optional<QaResult> qaResult = qaResultRepository.findById(qaResultId);
		if (qaResult.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/previousQAResultRef", "no QA Result exists for reference '" + ref + "'"));
		}
	}

	private void validatePreviousFindings(
			JsonNode findingRefsNode, WebsiteImplementationCandidate previousCandidate, List<PreExecutionValidationIssue> issues) {
		if (!findingRefsNode.isArray()) {
			return;
		}
		int i = 0;
		for (JsonNode findingRefNode : findingRefsNode) {
			String path = "remediationContext/previousFindingRefs[" + i + "]";
			String ref = findingRefNode.asString(null);
			UUID findingId;
			try {
				findingId = UUID.fromString(ref);
			} catch (IllegalArgumentException e) {
				issues.add(new PreExecutionValidationIssue(path, "not a resolvable Finding reference: '" + ref + "'"));
				i++;
				continue;
			}
			Optional<CandidateFinding> finding = candidateFindingRepository.findById(findingId);
			if (finding.isEmpty()) {
				issues.add(new PreExecutionValidationIssue(path, "no Candidate Finding exists for reference '" + ref + "'"));
			} else if (previousCandidate != null && !finding.get().getTestedCandidateId().equals(previousCandidate.getId())) {
				issues.add(new PreExecutionValidationIssue(
						path,
						"Finding '" + ref + "' belongs to Candidate " + finding.get().getTestedCandidateId()
								+ ", not the referenced previous Candidate " + previousCandidate.getId()));
			}
			i++;
		}
	}

	private void validateSameVariantLineage(
			WebsiteImplementationCandidate targetCandidate,
			WebsiteImplementationCandidate previousCandidate,
			List<PreExecutionValidationIssue> issues) {
		if (!targetCandidate.getSourceDesignProposalLocalRef().equals(previousCandidate.getSourceDesignProposalLocalRef())) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/previousCandidateRef",
					"cross-variant remediation is not allowed: target Candidate's Variant Lineage '"
							+ targetCandidate.getSourceDesignProposalLocalRef() + "' does not match the previous Candidate's own '"
							+ previousCandidate.getSourceDesignProposalLocalRef() + "'"));
		}
	}

	private void validateSameSourceDesign(
			WebsiteImplementationCandidate targetCandidate,
			WebsiteImplementationCandidate previousCandidate,
			List<PreExecutionValidationIssue> issues) {
		if (!targetCandidate.getSourceDesignRef().equals(previousCandidate.getSourceDesignRef())) {
			issues.add(new PreExecutionValidationIssue(
					"remediationContext/previousCandidateRef",
					"normal remediation requires a stable Source Design: target Candidate's own '" + targetCandidate.getSourceDesignRef()
							+ "' does not match the previous Candidate's own '" + previousCandidate.getSourceDesignRef() + "'"));
		}
	}
}
