package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserSmokeResultTests {

	@Test
	void aCleanSiteWithNoIssuesIsNotBlocked() {
		BrowserSmokeResult result = new BrowserSmokeResult(
				List.of(new NetworkPolicyFinding(
						"https://example-project.dev/", "example-project.dev", NetworkPolicyOutcome.ALLOWED, "self-origin resource")),
				List.of());

		assertThat(result.blocked()).isFalse();
	}

	@Test
	void aFatalBrowserErrorBlocksEvenWithNoNetworkPolicyFindingsAtAll() {
		BrowserSmokeResult result =
				new BrowserSmokeResult(List.of(), List.of(new BrowserRuntimeIssue("pageerror", "Uncaught TypeError: x is not a function")));

		assertThat(result.blocked()).isTrue();
		// Kept in its own list - never merged into networkFindings.
		assertThat(result.networkFindings()).isEmpty();
		assertThat(result.runtimeIssues()).hasSize(1);
	}

	@Test
	void networkPolicyFindingsAndRuntimeIssuesAreCapturedInSeparateLists() {
		BrowserSmokeResult result = new BrowserSmokeResult(
				List.of(new NetworkPolicyFinding(
						"https://cdn.example.com/lib.js", "cdn.example.com", NetworkPolicyOutcome.BLOCKED, "undeclared external runtime target")),
				List.of(new BrowserRuntimeIssue("requestfailed", "critical asset failed to load: /assets/index.js")));

		assertThat(result.blocked()).isTrue();
		assertThat(result.networkFindings()).hasSize(1);
		assertThat(result.runtimeIssues()).hasSize(1);
		// The two categories never share a type - a caller can always tell which is which.
		assertThat(result.networkFindings().getFirst()).isInstanceOf(NetworkPolicyFinding.class);
		assertThat(result.runtimeIssues().getFirst()).isInstanceOf(BrowserRuntimeIssue.class);
	}
}
