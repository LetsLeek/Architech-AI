package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkPolicyCheckerTests {

	private final NetworkPolicyChecker checker = new NetworkPolicyChecker();

	@Test
	void aCleanLocalSiteHasOnlyAllowedSelfOriginFindings() {
		List<ObservedRequest> requests = List.of(
				new ObservedRequest("https://example-project.dev/", "page-load"),
				new ObservedRequest("https://example-project.dev/assets/index.js", "script-load"),
				new ObservedRequest("https://example-project.dev/assets/index.css", "style-load"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", List.of(), requests);

		assertThat(findings).allSatisfy(f -> assertThat(f.outcome()).isEqualTo(NetworkPolicyOutcome.ALLOWED));
		assertThat(NetworkPolicyChecker.anyBlocked(findings)).isFalse();
	}

	@Test
	void allowsAnAuthorizedExternalTargetForItsDeclaredPurpose() {
		List<AuthorizedExternalTarget> authorized =
				List.of(new AuthorizedExternalTarget("maps.googleapis.com", "map-embed"));
		List<ObservedRequest> requests =
				List.of(new ObservedRequest("https://maps.googleapis.com/maps/api/js", "map-embed"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", authorized, requests);

		assertThat(findings).allSatisfy(f -> assertThat(f.outcome()).isEqualTo(NetworkPolicyOutcome.ALLOWED));
	}

	@Test
	void blocksTheSameAuthorizedHostWhenUsedForAnUndeclaredPurpose() {
		// Authorized for map-embed only - a request to the same host for a different purpose
		// (e.g. an undeclared analytics beacon) must not be silently allowed just because the
		// host is on the list.
		List<AuthorizedExternalTarget> authorized =
				List.of(new AuthorizedExternalTarget("maps.googleapis.com", "map-embed"));
		List<ObservedRequest> requests =
				List.of(new ObservedRequest("https://maps.googleapis.com/analytics/collect", "analytics"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", authorized, requests);

		assertThat(findings).allSatisfy(f -> assertThat(f.outcome()).isEqualTo(NetworkPolicyOutcome.BLOCKED));
		assertThat(findings.getFirst().reason()).contains("map-embed").contains("analytics");
	}

	@Test
	void blocksAnUndeclaredGoogleFontsRequest() {
		List<ObservedRequest> requests =
				List.of(new ObservedRequest("https://fonts.googleapis.com/css2?family=Roboto", "font-load"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", List.of(), requests);

		assertThat(NetworkPolicyChecker.anyBlocked(findings)).isTrue();
		assertThat(findings.getFirst().host()).isEqualTo("fonts.googleapis.com");
	}

	@Test
	void blocksAnUndeclaredAnalyticsBeacon() {
		List<ObservedRequest> requests =
				List.of(new ObservedRequest("https://www.google-analytics.com/collect", "analytics"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", List.of(), requests);

		assertThat(NetworkPolicyChecker.anyBlocked(findings)).isTrue();
	}

	@Test
	void blocksAnUndeclaredExternalApiCall() {
		List<ObservedRequest> requests = List.of(new ObservedRequest("https://api.stripe.com/v1/charges", "payment"));

		List<NetworkPolicyFinding> findings = checker.classify("example-project.dev", List.of(), requests);

		assertThat(NetworkPolicyChecker.anyBlocked(findings)).isTrue();
	}

	@Test
	void rejectsAnObservedRequestThatIsNotAValidAbsoluteUrl() {
		List<ObservedRequest> requests = List.of(new ObservedRequest("not-a-url", "page-load"));

		assertThatThrownBy(() -> checker.classify("example-project.dev", List.of(), requests))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
