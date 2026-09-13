package ai.architech.backend.core.verification;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The Website Developer V1 browser/runtime network policy checker used by authoritative Runner
 * Verification (AIW-157). Classifies each {@link ObservedRequest} a local route/navigation smoke
 * produced: self-origin resources are always allowed; an external target is allowed only when it
 * is both an authorized host AND authorized for the exact purpose the request is actually for.
 * Everything else - an undeclared CDN, font host, analytics/tracking endpoint, or API - is a
 * blocking finding.
 *
 * <p>Capturing real browser network events (a headless browser during local route/navigation
 * smoke) is Runner-integration scope, not this class's - it is a pure classifier over already-
 * observed requests, exactly like {@link ai.architech.backend.core.dependency.DependencyPolicyClassifier}
 * is a pure classifier over already-parsed manifest entries rather than a package-installer
 * itself.
 */
@Component
public class NetworkPolicyChecker {

	public List<NetworkPolicyFinding> classify(
			String selfOriginHost, List<AuthorizedExternalTarget> authorizedTargets, List<ObservedRequest> requests) {
		return requests.stream().map(request -> classifyOne(selfOriginHost, authorizedTargets, request)).toList();
	}

	private NetworkPolicyFinding classifyOne(
			String selfOriginHost, List<AuthorizedExternalTarget> authorizedTargets, ObservedRequest request) {
		String host = hostOf(request.url());

		if (host.equalsIgnoreCase(selfOriginHost)) {
			return new NetworkPolicyFinding(request.url(), host, NetworkPolicyOutcome.ALLOWED, "self-origin resource");
		}

		boolean authorizedForThisPurpose = authorizedTargets.stream()
				.anyMatch(target -> target.host().equalsIgnoreCase(host) && target.authorizedPurpose().equals(request.purpose()));
		if (authorizedForThisPurpose) {
			return new NetworkPolicyFinding(
					request.url(),
					host,
					NetworkPolicyOutcome.ALLOWED,
					"authorized Integration Contract/asset target for '" + request.purpose() + "'");
		}

		boolean hostAuthorizedForADifferentPurpose =
				authorizedTargets.stream().anyMatch(target -> target.host().equalsIgnoreCase(host));
		if (hostAuthorizedForADifferentPurpose) {
			String authorizedPurposes = authorizedTargets.stream()
					.filter(target -> target.host().equalsIgnoreCase(host))
					.map(AuthorizedExternalTarget::authorizedPurpose)
					.distinct()
					.reduce((a, b) -> a + ", " + b)
					.orElse("");
			return new NetworkPolicyFinding(
					request.url(),
					host,
					NetworkPolicyOutcome.BLOCKED,
					"'" + host + "' is authorized only for [" + authorizedPurposes + "], not '" + request.purpose() + "'");
		}

		return new NetworkPolicyFinding(
				request.url(),
				host,
				NetworkPolicyOutcome.BLOCKED,
				"undeclared external runtime target '" + host + "' - not self-origin, not an authorized Runtime "
						+ "Profile/asset/Integration Contract target");
	}

	public static boolean anyBlocked(List<NetworkPolicyFinding> findings) {
		return findings.stream().anyMatch(finding -> finding.outcome() == NetworkPolicyOutcome.BLOCKED);
	}

	private String hostOf(String url) {
		String host = URI.create(url).getHost();
		if (host == null) {
			throw new IllegalArgumentException("Not a valid absolute URL (no host): " + url);
		}
		return host;
	}
}
