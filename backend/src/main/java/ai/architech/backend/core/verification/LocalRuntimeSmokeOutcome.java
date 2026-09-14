package ai.architech.backend.core.verification;

import java.util.List;

/**
 * The full result of one {@link LocalRuntimeSmokeRunner} run. {@code startedSuccessfully} is
 * {@code false} only for an infrastructure-level failure (the local runtime never became ready,
 * or the browser automation itself broke) - never for anything the generated site's own content
 * did, which is instead reflected in {@link #canonicalRouteLoaded()}, {@link #runtimeIssues()}
 * and {@link #observedRequests()} for the caller to classify.
 */
public record LocalRuntimeSmokeOutcome(
		boolean startedSuccessfully,
		String startupFailureDetail,
		boolean canonicalRouteLoaded,
		boolean wideViewportOverflow,
		boolean narrowViewportOverflow,
		List<ObservedRequest> observedRequests,
		List<BrowserRuntimeIssue> runtimeIssues) {

	static LocalRuntimeSmokeOutcome startupFailed(String detail) {
		return new LocalRuntimeSmokeOutcome(false, detail, false, false, false, List.of(), List.of());
	}
}
