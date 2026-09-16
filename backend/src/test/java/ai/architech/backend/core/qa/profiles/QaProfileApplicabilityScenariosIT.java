package ai.architech.backend.core.qa.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.tooling.QaToolCapabilityProfile;
import ai.architech.backend.core.qa.tooling.QaToolCapabilityProfileLoader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * AIW-175's own five named scenarios, run against the real frozen {@code
 * website-qa-full-release@1.0.0} profile, the real {@link QaDomainApplicabilityResolver}, and the
 * real {@code website-qa-tools@1.0.0} tool capability profile (AIW-171) via {@link
 * ToolProfileCompatibilityValidator} - single-language, multilingual, no-integration,
 * required-integration-with-contract and required-integration-without-contract.
 */
@SpringBootTest
class QaProfileApplicabilityScenariosIT {

	@Autowired
	private QaProfileLoader profileLoader;

	@Autowired
	private QaToolCapabilityProfileLoader toolCapabilityProfileLoader;

	@Autowired
	private QaDomainApplicabilityResolver resolver;

	@Autowired
	private ToolProfileCompatibilityValidator toolCompatibilityValidator;

	@Test
	void singleLanguageLeavesLocalizationNotApplicable() {
		ResolvedDomainApplicabilityResult localization = resolve(new ProductAuthorityContext(1, Map.of(), true));

		assertThat(localization.outcome()).isEqualTo(ResolvedDomainApplicability.NOT_APPLICABLE);
	}

	@Test
	void multilingualMakesLocalizationApplicableAndToolCompatible() {
		QaProfile fullRelease = profileLoader.resolve("website-qa-full-release@1.0.0");
		ProductAuthorityContext context = new ProductAuthorityContext(2, Map.of(), true);
		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(fullRelease, context);
		ResolvedDomainApplicabilityResult localization =
				resolved.stream().filter(r -> r.domain().equals("LOCALIZATION")).findFirst().orElseThrow();

		assertThat(localization.outcome()).isEqualTo(ResolvedDomainApplicability.APPLICABLE);

		QaToolCapabilityProfile toolProfile = toolCapabilityProfileLoader.resolve("website-qa-tools@1.0.0");
		ToolProfileCompatibilityResult compatibility = toolCompatibilityValidator.validate(toolProfile, List.of(localization));
		assertThat(compatibility.passed()).as(compatibility.problems().toString()).isTrue();
	}

	@Test
	void noIntegrationLeavesIntegrationBehaviorNotApplicable() {
		ResolvedDomainApplicabilityResult integration = resolveIntegrationBehavior(
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", false), false));

		assertThat(integration.outcome()).isEqualTo(ResolvedDomainApplicability.NOT_APPLICABLE);
	}

	@Test
	void requiredIntegrationWithContractIsApplicableAndToolCompatible() {
		ResolvedDomainApplicabilityResult integration = resolveIntegrationBehavior(
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", true), true));

		assertThat(integration.outcome()).isEqualTo(ResolvedDomainApplicability.APPLICABLE);

		QaToolCapabilityProfile toolProfile = toolCapabilityProfileLoader.resolve("website-qa-tools@1.0.0");
		ToolProfileCompatibilityResult compatibility = toolCompatibilityValidator.validate(toolProfile, List.of(integration));
		// INTEGRATION_CONTRACT_REFERENCE_INTEGRITY is PLATFORM_CORE - no bounded tool capability
		// to be incompatible with.
		assertThat(compatibility.passed()).as(compatibility.problems().toString()).isTrue();
	}

	@Test
	void requiredIntegrationWithoutContractYieldsMissingAuthorityNeverNotApplicable() {
		ResolvedDomainApplicabilityResult integration = resolveIntegrationBehavior(
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", true), false));

		assertThat(integration.outcome()).isEqualTo(ResolvedDomainApplicability.MISSING_REQUIRED_AUTHORITY);
		assertThat(integration.requiredChecks()).contains("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY");
	}

	private ResolvedDomainApplicabilityResult resolve(ProductAuthorityContext context) {
		QaProfile fullRelease = profileLoader.resolve("website-qa-full-release@1.0.0");
		return resolver.resolve(fullRelease, context).stream()
				.filter(r -> r.domain().equals("LOCALIZATION"))
				.findFirst()
				.orElseThrow();
	}

	private ResolvedDomainApplicabilityResult resolveIntegrationBehavior(ProductAuthorityContext context) {
		QaProfile fullRelease = profileLoader.resolve("website-qa-full-release@1.0.0");
		return resolver.resolve(fullRelease, context).stream()
				.filter(r -> r.domain().equals("INTEGRATION_BEHAVIOR"))
				.findFirst()
				.orElseThrow();
	}
}
