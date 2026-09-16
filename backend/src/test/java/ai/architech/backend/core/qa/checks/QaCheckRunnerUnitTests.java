package ai.architech.backend.core.qa.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.validation.CandidateBindingValidator;
import ai.architech.backend.core.validation.PreExecutionValidationIssue;
import ai.architech.backend.core.validation.PreExecutionValidationResult;
import ai.architech.backend.core.validation.QAExecutionPreflightValidator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fast, mocked-dependency coverage of every {@link QaCheckRunner} dispatch branch (AIW-172) -
 * {@link QaCheckRunnerIT} separately proves the real Playwright/Postgres wiring end to end; this
 * class exists to cheaply exercise the PASS/FAIL/ERROR/NOT_APPLICABLE outcome logic itself
 * without paying for a real browser or database per scenario.
 */
class QaCheckRunnerUnitTests {

	private QaPlaywrightDriver driver;
	private QAExecutionPreflightValidator preflightValidator;
	private QaCheckRunner runner;

	private static final QaCheckExecutionContext CONTEXT = new QaCheckExecutionContext(
			UUID.randomUUID(), "{}", "preview-1", "preview-1", "http://127.0.0.1:5173", "/", 1440, 900, "hello", "#cta", List.of("/de"));

	@BeforeEach
	void setUp() {
		driver = mock(QaPlaywrightDriver.class);
		preflightValidator = mock(QAExecutionPreflightValidator.class);
		QaCheckRegistryLoader registryLoader = mock(QaCheckRegistryLoader.class);
		when(registryLoader.load()).thenReturn(allChecksRegistry());
		runner = new QaCheckRunner(registryLoader, driver, preflightValidator, new CandidateBindingValidator());
	}

	@Test
	void rejectsAnUnknownCheckCode() {
		assertThatThrownBy(() -> runner.run("NOT_A_REAL_CODE", CONTEXT)).isInstanceOf(UnknownQaCheckCodeException.class);
	}

	@Test
	void aRegisteredButUnimplementedCheckReturnsError() {
		QaCheckResult result = runner.run("REQUIRED_ASSET_PRESENCE", CONTEXT);

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.ERROR);
		assertThat(result.summary()).contains("not yet implemented");
	}

	@Test
	void candidateSourceIdentityPassesWithNoCandidateRefIssues() {
		when(preflightValidator.validate(any(), any())).thenReturn(new PreExecutionValidationResult(List.of()));

		assertThat(runner.run("CANDIDATE_SOURCE_IDENTITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void candidateSourceIdentityAndProvenanceShareOneFailureSignal() {
		when(preflightValidator.validate(any(), any())).thenReturn(new PreExecutionValidationResult(
				List.of(new PreExecutionValidationIssue("target/candidateRef", "no PASS Runner Verification exists"))));

		assertThat(runner.run("CANDIDATE_SOURCE_IDENTITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
		assertThat(runner.run("CANDIDATE_VERIFICATION_PROVENANCE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void authorityReferenceIntegrityPassesWithNoAuthorityIssues() {
		when(preflightValidator.validate(any(), any())).thenReturn(new PreExecutionValidationResult(List.of()));

		assertThat(runner.run("AUTHORITY_REFERENCE_INTEGRITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void authorityReferenceIntegrityFailsOnAQaAuthorityIssue() {
		when(preflightValidator.validate(any(), any())).thenReturn(new PreExecutionValidationResult(
				List.of(new PreExecutionValidationIssue("qaAuthority/qaProfileRef", "unknown value"))));

		assertThat(runner.run("AUTHORITY_REFERENCE_INTEGRITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void executionSurfaceBindingPassesWhenClaimedAndObservedMatch() {
		assertThat(runner.run("EXECUTION_SURFACE_CANDIDATE_BINDING", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void executionSurfaceBindingFailsOnDrift() {
		QaCheckExecutionContext drifted = new QaCheckExecutionContext(
				UUID.randomUUID(), "{}", "preview-1", "preview-99", "http://127.0.0.1:5173", "/", 1440, 900, null, null, List.of());

		assertThat(runner.run("EXECUTION_SURFACE_CANDIDATE_BINDING", drifted).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void canonicalRouteReachabilityReflectsTheObservedResponse() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());
		assertThat(runner.run("CANONICAL_ROUTE_REACHABILITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);

		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(notOkObservation(500));
		assertThat(runner.run("CANONICAL_ROUTE_REACHABILITY", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void fatalBrowserErrorScanFailsWhenAConsoleErrorWasObserved() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt()))
				.thenReturn(withConsoleErrors(List.of("TypeError: x is not a function")));

		assertThat(runner.run("FATAL_BROWSER_ERROR_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void fatalBrowserErrorScanPassesWithNoErrors() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("FATAL_BROWSER_ERROR_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void brokenAssetScanFailsWhenABrokenAssetWasObserved() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt()))
				.thenReturn(withBrokenAssets(List.of("/logo.png: 404")));

		assertThat(runner.run("BROKEN_ASSET_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void brokenAssetScanPassesWithNoBrokenAssets() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("BROKEN_ASSET_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void horizontalOverflowScanFailsWhenOverflowWasObserved() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withOverflow(true));

		assertThat(runner.run("HORIZONTAL_OVERFLOW_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void horizontalOverflowScanPassesWithNoOverflow() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("HORIZONTAL_OVERFLOW_SCAN", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void accessibilityTreeCapturePassesWithNonBlankSnapshot() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("A11Y_ACCESSIBILITY_TREE_CAPTURE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void accessibilityTreeCaptureErrorsWithABlankSnapshot() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withAccessibilityTreeSnapshot(""));

		assertThat(runner.run("A11Y_ACCESSIBILITY_TREE_CAPTURE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.ERROR);
	}

	@Test
	void documentTitlePresentFailsWithABlankTitle() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withTitle(""));

		assertThat(runner.run("DOCUMENT_TITLE_PRESENT", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void documentTitlePresentPassesWithANonBlankTitle() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("DOCUMENT_TITLE_PRESENT", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void initialRenderCompletionFailsWhenLoadDidNotCompleteInTime() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withLoadCompleted(false));

		assertThat(runner.run("INITIAL_RENDER_COMPLETION", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void initialRenderCompletionPassesWhenLoadCompletedInTime() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("INITIAL_RENDER_COMPLETION", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void primaryLocalInteractionSmokeErrorsWhenTheSelectorIsNotFound() {
		when(driver.interact(anyString(), anyString(), anyString()))
				.thenReturn(new QaInteractionObservation(false, false, List.of(), List.of()));

		assertThat(runner.run("PRIMARY_LOCAL_INTERACTION_SMOKE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.ERROR);
	}

	@Test
	void primaryLocalInteractionSmokePassesOnASuccessfulClickWithNoErrors() {
		when(driver.interact(anyString(), anyString(), anyString()))
				.thenReturn(new QaInteractionObservation(true, true, List.of(), List.of()));

		assertThat(runner.run("PRIMARY_LOCAL_INTERACTION_SMOKE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void primaryLocalInteractionSmokeFailsWhenTheClickProducedAFatalError() {
		when(driver.interact(anyString(), anyString(), anyString()))
				.thenReturn(new QaInteractionObservation(true, true, List.of("boom"), List.of()));

		assertThat(runner.run("PRIMARY_LOCAL_INTERACTION_SMOKE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void requiredTextPresenceIsNotApplicableWithNoRequiredText() {
		QaCheckExecutionContext noText = new QaCheckExecutionContext(
				UUID.randomUUID(), "{}", "preview-1", "preview-1", "http://127.0.0.1:5173", "/", 1440, 900, null, null, List.of());

		assertThat(runner.run("REQUIRED_TEXT_PRESENCE", noText).outcome()).isEqualTo(QaCheckOutcome.NOT_APPLICABLE);
	}

	@Test
	void requiredTextPresencePassesWhenTheSnapshotContainsTheText() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withAccessibilityTreeSnapshot("... hello world ..."));

		assertThat(runner.run("REQUIRED_TEXT_PRESENCE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void requiredTextPresenceFailsWhenTheSnapshotDoesNotContainTheText() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(withAccessibilityTreeSnapshot("goodbye"));

		assertThat(runner.run("REQUIRED_TEXT_PRESENCE", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void requiredLocaleEntrypointsIsNotApplicableWithNoLocales() {
		QaCheckExecutionContext noLocales = new QaCheckExecutionContext(
				UUID.randomUUID(), "{}", "preview-1", "preview-1", "http://127.0.0.1:5173", "/", 1440, 900, null, null, List.of());

		assertThat(runner.run("REQUIRED_LOCALE_ENTRYPOINTS", noLocales).outcome()).isEqualTo(QaCheckOutcome.NOT_APPLICABLE);
	}

	@Test
	void requiredLocaleEntrypointsPassesWhenEveryLocaleRespondsOk() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(okObservation());

		assertThat(runner.run("REQUIRED_LOCALE_ENTRYPOINTS", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void requiredLocaleEntrypointsFailsWhenALocaleDoesNotRespondOk() {
		when(driver.observe(anyString(), anyString(), anyInt(), anyInt())).thenReturn(notOkObservation(404));

		assertThat(runner.run("REQUIRED_LOCALE_ENTRYPOINTS", CONTEXT).outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	private QaCheckRegistry allChecksRegistry() {
		List<String> codes = List.of(
				"CANDIDATE_SOURCE_IDENTITY", "CANDIDATE_VERIFICATION_PROVENANCE", "AUTHORITY_REFERENCE_INTEGRITY",
				"EXECUTION_SURFACE_CANDIDATE_BINDING", "CANONICAL_ROUTE_REACHABILITY", "FATAL_BROWSER_ERROR_SCAN",
				"BROKEN_ASSET_SCAN", "HORIZONTAL_OVERFLOW_SCAN", "A11Y_ACCESSIBILITY_TREE_CAPTURE", "DOCUMENT_TITLE_PRESENT",
				"INITIAL_RENDER_COMPLETION", "PRIMARY_LOCAL_INTERACTION_SMOKE", "REQUIRED_TEXT_PRESENCE",
				"REQUIRED_LOCALE_ENTRYPOINTS", "REQUIRED_ASSET_PRESENCE");
		return new QaCheckRegistry("website-qa-check-registry@1.0.0",
				codes.stream().map(code -> new QaCheckRegistryEntry(code, "RUNTIME_BROWSER", java.util.Optional.empty(), QaCheckCategory.DETERMINISTIC)).toList());
	}

	private QaRouteObservation okObservation() {
		return new QaRouteObservation(true, 200, "A Real Title", "en", List.of(), List.of(), List.of(), List.of(), false, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation notOkObservation(int status) {
		return new QaRouteObservation(false, status, "A Real Title", "en", List.of(), List.of(), List.of(), List.of(), false, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation withConsoleErrors(List<String> errors) {
		return new QaRouteObservation(true, 200, "A Real Title", "en", errors, List.of(), List.of(), List.of(), false, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation withBrokenAssets(List<String> brokenAssets) {
		return new QaRouteObservation(true, 200, "A Real Title", "en", List.of(), List.of(), List.of(), brokenAssets, false, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation withOverflow(boolean overflow) {
		return new QaRouteObservation(true, 200, "A Real Title", "en", List.of(), List.of(), List.of(), List.of(), overflow, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation withAccessibilityTreeSnapshot(String snapshot) {
		return new QaRouteObservation(true, 200, "A Real Title", "en", List.of(), List.of(), List.of(), List.of(), false, snapshot, true, 120L);
	}

	private QaRouteObservation withTitle(String title) {
		return new QaRouteObservation(true, 200, title, "en", List.of(), List.of(), List.of(), List.of(), false, "aria snapshot content", true, 120L);
	}

	private QaRouteObservation withLoadCompleted(boolean completed) {
		return new QaRouteObservation(true, 200, "A Real Title", "en", List.of(), List.of(), List.of(), List.of(), false, "aria snapshot content", completed, 120L);
	}
}
