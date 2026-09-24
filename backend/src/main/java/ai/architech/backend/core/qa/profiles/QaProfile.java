package ai.architech.backend.core.qa.profiles;

import java.util.List;
import java.util.Optional;

/**
 * One loaded, versioned frozen QA profile - {@code website-qa-comparison-readiness@1.0.0} or
 * {@code website-qa-full-release@1.0.0} (AIW-175), matching {@code
 * QAExecutionPreflightValidator.VALID_QA_PROFILE_REFS}. {@link #profileType()} is a distinct,
 * required field precisely so {@link QaProfileLoader#resolve} can never silently substitute one
 * scope for the other - a caller asking for {@code COMPARISON_READINESS} and receiving a {@code
 * FULL_RELEASE} profile (or vice versa) is a loader bug, not a possible outcome, since resolution
 * is by exact {@code ref} string, never by type-compatible fallback.
 */
public record QaProfile(
		String ref,
		QaProfileType profileType,
		List<QaViewport> viewports,
		List<String> preconditionChecks,
		List<QaDomainDefinition> domains,
		FindingDispositionPolicy findingDispositionPolicy,
		Optional<RequirementPolicy> requirementPolicy,
		AuthorityIssuePolicy authorityIssuePolicy,
		EvaluationIssuePolicy evaluationIssuePolicy) {

	public Optional<QaDomainDefinition> domain(String domainName) {
		return domains.stream().filter(d -> d.domain().equals(domainName)).findFirst();
	}
}
