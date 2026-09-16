package ai.architech.backend.core.qa.profiles;

import ai.architech.backend.core.qa.checks.QaCheckRegistry;
import ai.architech.backend.core.qa.checks.QaCheckRegistryEntry;
import ai.architech.backend.core.qa.checks.QaCheckRegistryLoader;
import ai.architech.backend.core.qa.checks.UnknownQaCheckCodeException;
import ai.architech.backend.core.qa.tooling.QaToolCapabilityProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "ToolProfileCompatibilityValidator detects required missing capabilities before expensive
 * execution begins" (AIW-175) - checks every {@code REQUIRED}/{@code APPLICABLE} resolved
 * domain's required check codes against a bound {@link QaToolCapabilityProfile}, so an execution
 * that cannot possibly complete its applicable scope is rejected before any check, browser
 * session, or semantic Agent call ever runs.
 */
@Component
public class ToolProfileCompatibilityValidator {

	private static final Set<ResolvedDomainApplicability> SCOPE_REQUIRING_TOOLS =
			Set.of(ResolvedDomainApplicability.REQUIRED, ResolvedDomainApplicability.APPLICABLE);

	private final QaCheckRegistryLoader registryLoader;

	ToolProfileCompatibilityValidator(QaCheckRegistryLoader registryLoader) {
		this.registryLoader = registryLoader;
	}

	public ToolProfileCompatibilityResult validate(
			QaToolCapabilityProfile toolProfile, List<ResolvedDomainApplicabilityResult> resolvedDomains) {
		QaCheckRegistry registry = registryLoader.load();
		List<ToolProfileCompatibilityProblem> problems = new ArrayList<>();

		for (ResolvedDomainApplicabilityResult resolved : resolvedDomains) {
			if (!SCOPE_REQUIRING_TOOLS.contains(resolved.outcome())) {
				continue;
			}
			for (String checkCode : resolved.requiredChecks()) {
				QaCheckRegistryEntry entry = registry.resolve(checkCode).orElseThrow(() -> new UnknownQaCheckCodeException(checkCode));
				entry.capability().ifPresent(capability -> {
					if (!toolProfile.supports(capability)) {
						problems.add(new ToolProfileCompatibilityProblem(checkCode, capability));
					}
				});
			}
		}

		return problems.isEmpty() ? ToolProfileCompatibilityResult.compatible() : ToolProfileCompatibilityResult.incompatible(problems);
	}
}
