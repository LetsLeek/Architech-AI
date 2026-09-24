package ai.architech.backend.core.documentation.profiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One loaded, versioned frozen Documentation profile - {@code CUSTOMER_HANDOVER@1.0.0} or {@code
 * TECHNICAL_HANDOVER@1.0.0} (AIW-188). {@link #profileType()} is a distinct, required field for
 * the same reason {@code QaProfile.profileType()} is: resolution is always by exact {@link #ref()}
 * string, never by type-compatible fallback, so a caller can never silently receive the wrong
 * profile.
 *
 * <p>{@link #candidateSafeProjectionRequired()} is present only on {@code TECHNICAL_HANDOVER} in
 * the frozen package - modelled as {@link Optional} rather than an empty list so "profile does not
 * declare this at all" stays distinguishable from "profile declares an empty projection list".
 */
public record DocumentationProfile(
		String ref,
		DocumentationProfileType profileType,
		boolean active,
		String primaryAudience,
		String operation,
		List<String> requiredRoots,
		List<String> optionalRoots,
		DocumentationQaPreconditions qaPreconditions,
		Optional<List<String>> candidateSafeProjectionRequired,
		List<SemanticDocumentSpec> semanticDocuments,
		List<DeterministicReportSpec> deterministicReports,
		Map<String, List<String>> composerDeterministicBlocks,
		List<String> allowedClaimTypes,
		String defaultSkillSet,
		String compositionSkill,
		boolean semanticValidationRequired,
		List<String> supportedLocales,
		String findingPolicyRef) {}
