package ai.architech.backend.core.qa.checks;

import java.util.List;

/**
 * Everything {@link QaPlaywrightDriver#observe} actually saw at one route/viewport (AIW-172) -
 * never a judgment about whether any of it constitutes a defect; that stays each individual
 * check's own job, matching {@code LocalRuntimeSmokeRunner}'s own observe-vs-classify split.
 */
public record QaRouteObservation(
		boolean responseOk,
		int responseStatus,
		String documentTitle,
		String documentLanguage,
		List<String> consoleErrors,
		List<String> pageErrors,
		List<String> failedRequests,
		List<String> brokenAssetResponses,
		boolean horizontalOverflow,
		String accessibilityTreeSnapshot,
		boolean loadCompletedWithinTimeout,
		long renderTimeMillis) {}
