package ai.architech.backend.core.qa.checks;

import ai.architech.backend.core.validation.CandidateBindingResult;
import ai.architech.backend.core.validation.CandidateBindingValidator;
import ai.architech.backend.core.validation.PreExecutionValidationIssue;
import ai.architech.backend.core.validation.PreExecutionValidationResult;
import ai.architech.backend.core.validation.QAExecutionPreflightValidator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Executes one registered {@link QaCheckRegistry} check by code (AIW-172). Rejects unknown check
 * codes ({@link UnknownQaCheckCodeException}) before dispatching to any implementation.
 *
 * <p><b>Precondition checks</b> ({@code CANDIDATE_SOURCE_IDENTITY}, {@code
 * CANDIDATE_VERIFICATION_PROVENANCE}, {@code AUTHORITY_REFERENCE_INTEGRITY}) delegate to the
 * already-real {@link QAExecutionPreflightValidator} (AIW-169) rather than re-implementing its
 * lookups. That validator reports Candidate-resolution and Technical-Verification-PASS-provenance
 * issues under the same {@code "target/candidateRef"} path (they are evaluated together there),
 * so {@code CANDIDATE_SOURCE_IDENTITY} and {@code CANDIDATE_VERIFICATION_PROVENANCE} deliberately
 * share that one signal rather than fragile-parsing the issue message text to split it further -
 * a real, documented simplification, not an invented distinction. {@code
 * EXECUTION_SURFACE_CANDIDATE_BINDING} delegates to {@link CandidateBindingValidator#validateExecutionSurface}
 * directly.
 *
 * <p><b>Browser-driven checks</b> use {@link QaPlaywrightDriver} for real observations.
 *
 * <p><b>Everything else</b> in the registry (including {@code FUNCTIONAL_BINDING_REFERENCE_INTEGRITY}
 * and {@code INTEGRATION_CONTRACT_REFERENCE_INTEGRITY}, which would require resolving each
 * Candidate's original Requirements/Design artifacts by ref - plumbing this ticket does not build)
 * is registered and versioned (satisfying "unknown check codes are rejected") but not yet wired to
 * a real implementation - it returns {@code ERROR} with an explicit "not yet implemented"
 * diagnostic, which is the structurally correct outcome per this ticket's own AC ("ERROR creates
 * evaluation semantics rather than automatic Candidate failure"), never a fabricated PASS or FAIL.
 */
@Component
public class QaCheckRunner {

	private final QaCheckRegistryLoader registryLoader;
	private final QaPlaywrightDriver driver;
	private final QAExecutionPreflightValidator preflightValidator;
	private final CandidateBindingValidator bindingValidator;

	QaCheckRunner(
			QaCheckRegistryLoader registryLoader,
			QaPlaywrightDriver driver,
			QAExecutionPreflightValidator preflightValidator,
			CandidateBindingValidator bindingValidator) {
		this.registryLoader = registryLoader;
		this.driver = driver;
		this.preflightValidator = preflightValidator;
		this.bindingValidator = bindingValidator;
	}

	public QaCheckResult run(String checkCode, QaCheckExecutionContext context) {
		if (registryLoader.load().resolve(checkCode).isEmpty()) {
			throw new UnknownQaCheckCodeException(checkCode);
		}
		return switch (checkCode) {
			case "CANDIDATE_SOURCE_IDENTITY" -> candidateIdentityOrProvenance(checkCode, context);
			case "CANDIDATE_VERIFICATION_PROVENANCE" -> candidateIdentityOrProvenance(checkCode, context);
			case "AUTHORITY_REFERENCE_INTEGRITY" -> authorityReferenceIntegrity(context);
			case "EXECUTION_SURFACE_CANDIDATE_BINDING" -> executionSurfaceCandidateBinding(context);
			case "CANONICAL_ROUTE_REACHABILITY" -> canonicalRouteReachability(context);
			case "FATAL_BROWSER_ERROR_SCAN" -> fatalBrowserErrorScan(context);
			case "BROKEN_ASSET_SCAN" -> brokenAssetScan(context);
			case "HORIZONTAL_OVERFLOW_SCAN" -> horizontalOverflowScan(context);
			case "A11Y_ACCESSIBILITY_TREE_CAPTURE" -> accessibilityTreeCapture(context);
			case "DOCUMENT_TITLE_PRESENT" -> documentTitlePresent(context);
			case "INITIAL_RENDER_COMPLETION" -> initialRenderCompletion(context);
			case "PRIMARY_LOCAL_INTERACTION_SMOKE" -> primaryLocalInteractionSmoke(context);
			case "REQUIRED_TEXT_PRESENCE" -> requiredTextPresence(context);
			case "REQUIRED_LOCALE_ENTRYPOINTS" -> requiredLocaleEntrypoints(context);
			default -> QaCheckResult.error(checkCode, "check '" + checkCode + "' is registered but not yet implemented");
		};
	}

	private QaCheckResult candidateIdentityOrProvenance(String checkCode, QaCheckExecutionContext context) {
		PreExecutionValidationResult result = preflightValidator.validate(context.projectId(), context.qaExecutionInputJson());
		List<PreExecutionValidationIssue> candidateIssues =
				result.issues().stream().filter(issue -> issue.path().equals("target/candidateRef")).toList();
		return candidateIssues.isEmpty()
				? QaCheckResult.pass(checkCode, "target.candidateRef resolves to a real, project-owned, Verification-PASSed Candidate", List.of())
				: QaCheckResult.fail(checkCode, summarize(candidateIssues), List.of());
	}

	private QaCheckResult authorityReferenceIntegrity(QaCheckExecutionContext context) {
		PreExecutionValidationResult result = preflightValidator.validate(context.projectId(), context.qaExecutionInputJson());
		List<PreExecutionValidationIssue> authorityIssues =
				result.issues().stream().filter(issue -> issue.path().startsWith("qaAuthority/")).toList();
		return authorityIssues.isEmpty()
				? QaCheckResult.pass("AUTHORITY_REFERENCE_INTEGRITY", "every qaAuthority ref is known and supported", List.of())
				: QaCheckResult.fail("AUTHORITY_REFERENCE_INTEGRITY", summarize(authorityIssues), List.of());
	}

	private QaCheckResult executionSurfaceCandidateBinding(QaCheckExecutionContext context) {
		CandidateBindingResult result =
				bindingValidator.validateExecutionSurface(context.claimedExecutionSurfaceRef(), context.observedExecutionSurfaceRef());
		return result.matched()
				? QaCheckResult.pass("EXECUTION_SURFACE_CANDIDATE_BINDING", "observed execution surface matches the claimed binding", List.of())
				: QaCheckResult.fail("EXECUTION_SURFACE_CANDIDATE_BINDING", result.integrityProblem(), List.of());
	}

	private QaCheckResult canonicalRouteReachability(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		return observation.responseOk()
				? QaCheckResult.pass("CANONICAL_ROUTE_REACHABILITY", "canonical route responded with status " + observation.responseStatus(), List.of())
				: QaCheckResult.fail("CANONICAL_ROUTE_REACHABILITY", "canonical route responded with status " + observation.responseStatus(), List.of());
	}

	private QaCheckResult fatalBrowserErrorScan(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		boolean hasFatalError = !observation.consoleErrors().isEmpty() || !observation.pageErrors().isEmpty();
		return hasFatalError
				? QaCheckResult.fail("FATAL_BROWSER_ERROR_SCAN",
						observation.consoleErrors().size() + " console error(s), " + observation.pageErrors().size() + " page error(s) observed",
						List.of())
				: QaCheckResult.pass("FATAL_BROWSER_ERROR_SCAN", "no console or page errors observed", List.of());
	}

	private QaCheckResult brokenAssetScan(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		return observation.brokenAssetResponses().isEmpty()
				? QaCheckResult.pass("BROKEN_ASSET_SCAN", "no broken asset responses observed", List.of())
				: QaCheckResult.fail("BROKEN_ASSET_SCAN", "broken assets: " + observation.brokenAssetResponses(), List.of());
	}

	private QaCheckResult horizontalOverflowScan(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		return observation.horizontalOverflow()
				? QaCheckResult.fail("HORIZONTAL_OVERFLOW_SCAN",
						"horizontal overflow at viewport " + context.viewportWidth() + "x" + context.viewportHeight(), List.of())
				: QaCheckResult.pass("HORIZONTAL_OVERFLOW_SCAN", "no horizontal overflow observed", List.of());
	}

	private QaCheckResult accessibilityTreeCapture(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		// EVIDENCE category - captures the observation, does not itself judge PASS/FAIL; only
		// whether the capture itself succeeded is this check's own concern.
		return observation.accessibilityTreeSnapshot() != null && !observation.accessibilityTreeSnapshot().isBlank()
				? QaCheckResult.pass("A11Y_ACCESSIBILITY_TREE_CAPTURE", "accessibility tree captured", List.of())
				: QaCheckResult.error("A11Y_ACCESSIBILITY_TREE_CAPTURE", "accessibility tree capture returned no content");
	}

	private QaCheckResult documentTitlePresent(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		return observation.documentTitle() != null && !observation.documentTitle().isBlank()
				? QaCheckResult.pass("DOCUMENT_TITLE_PRESENT", "document title: '" + observation.documentTitle() + "'", List.of())
				: QaCheckResult.fail("DOCUMENT_TITLE_PRESENT", "document has no title", List.of());
	}

	private QaCheckResult initialRenderCompletion(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		return observation.loadCompletedWithinTimeout()
				? QaCheckResult.pass("INITIAL_RENDER_COMPLETION", "initial render completed in " + observation.renderTimeMillis() + "ms", List.of())
				: QaCheckResult.fail("INITIAL_RENDER_COMPLETION", "initial render did not complete within the navigation timeout", List.of());
	}

	private QaCheckResult primaryLocalInteractionSmoke(QaCheckExecutionContext context) {
		QaInteractionObservation observation = driver.interact(context.baseUrl(), context.route(), context.interactionSelector());
		if (!observation.elementFound()) {
			return QaCheckResult.error("PRIMARY_LOCAL_INTERACTION_SMOKE",
					"interaction selector '" + context.interactionSelector() + "' did not resolve to any element");
		}
		boolean hasFatalError = !observation.pageErrors().isEmpty() || !observation.consoleErrors().isEmpty();
		return observation.clickSucceeded() && !hasFatalError
				? QaCheckResult.pass("PRIMARY_LOCAL_INTERACTION_SMOKE", "primary interaction completed without a fatal error", List.of())
				: QaCheckResult.fail("PRIMARY_LOCAL_INTERACTION_SMOKE", "primary interaction failed or produced a fatal error", List.of());
	}

	private QaCheckResult requiredTextPresence(QaCheckExecutionContext context) {
		QaRouteObservation observation = observeRoute(context);
		if (context.requiredText() == null || context.requiredText().isBlank()) {
			return QaCheckResult.notApplicable("REQUIRED_TEXT_PRESENCE", "no required text was specified for this claim");
		}
		return observation.accessibilityTreeSnapshot() != null && observation.accessibilityTreeSnapshot().contains(context.requiredText())
				? QaCheckResult.pass("REQUIRED_TEXT_PRESENCE", "required text was found on the page", List.of())
				: QaCheckResult.fail("REQUIRED_TEXT_PRESENCE", "required text '" + context.requiredText() + "' was not found on the page", List.of());
	}

	private QaCheckResult requiredLocaleEntrypoints(QaCheckExecutionContext context) {
		if (context.localeRoutePrefixes() == null || context.localeRoutePrefixes().isEmpty()) {
			return QaCheckResult.notApplicable("REQUIRED_LOCALE_ENTRYPOINTS", "no locale route prefixes apply to this Candidate");
		}
		for (String localeRoute : context.localeRoutePrefixes()) {
			QaRouteObservation observation =
					driver.observe(context.baseUrl(), localeRoute, context.viewportWidth(), context.viewportHeight());
			if (!observation.responseOk()) {
				return QaCheckResult.fail("REQUIRED_LOCALE_ENTRYPOINTS", "locale entrypoint '" + localeRoute + "' did not respond successfully", List.of());
			}
		}
		return QaCheckResult.pass("REQUIRED_LOCALE_ENTRYPOINTS", "every required locale entrypoint responded successfully", List.of());
	}

	private QaRouteObservation observeRoute(QaCheckExecutionContext context) {
		return driver.observe(context.baseUrl(), context.route(), context.viewportWidth(), context.viewportHeight());
	}

	private String summarize(List<PreExecutionValidationIssue> issues) {
		return issues.stream().map(PreExecutionValidationIssue::message).reduce((a, b) -> a + "; " + b).orElse("");
	}
}
