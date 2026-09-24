package ai.architech.backend.core.verification;

import java.util.List;

/**
 * The full result of one local route/navigation browser smoke check: network-policy findings
 * and browser/runtime issues are kept as two distinct lists throughout (never merged into one
 * generic "problems" list), so a caller can always tell which category any single finding came
 * from without inspecting its content.
 */
public record BrowserSmokeResult(List<NetworkPolicyFinding> networkFindings, List<BrowserRuntimeIssue> runtimeIssues) {

	public boolean blocked() {
		return NetworkPolicyChecker.anyBlocked(networkFindings) || !runtimeIssues.isEmpty();
	}
}
