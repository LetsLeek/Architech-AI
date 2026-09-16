package ai.architech.backend.core.qa.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.policy.PolicyDisposition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QaDomainApplicabilityResolverTests {

	private final QaDomainApplicabilityResolver resolver = new QaDomainApplicabilityResolver();

	@Test
	void aRequiredDomainAlwaysResolvesToRequired() {
		QaProfile profile = profileWith(new QaDomainDefinition("NAVIGATION", QaDomainApplicability.REQUIRED, null, List.of("CHECK_A"), List.of()));

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, emptyContext());

		assertThat(resolved).containsExactly(
				new ResolvedDomainApplicabilityResult("NAVIGATION", ResolvedDomainApplicability.REQUIRED, List.of("CHECK_A")));
	}

	@Test
	void aNotApplicableDomainAlwaysResolvesToNotApplicable() {
		QaProfile profile = profileWith(new QaDomainDefinition("PERFORMANCE_BASELINE", QaDomainApplicability.NOT_APPLICABLE, null, List.of(), List.of()));

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, emptyContext());

		assertThat(resolved).containsExactly(
				new ResolvedDomainApplicabilityResult("PERFORMANCE_BASELINE", ResolvedDomainApplicability.NOT_APPLICABLE, List.of()));
	}

	@Test
	void localizationIsNotApplicableWithOnlyOneRequiredLocale() {
		QaProfile profile = profileWith(new QaDomainDefinition(
				"LOCALIZATION", QaDomainApplicability.CONDITIONAL, "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES", List.of("LOCALE_CHECK"), List.of()));
		ProductAuthorityContext context = new ProductAuthorityContext(1, Map.of(), true);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		assertThat(resolved).containsExactly(
				new ResolvedDomainApplicabilityResult("LOCALIZATION", ResolvedDomainApplicability.NOT_APPLICABLE, List.of()));
	}

	@Test
	void localizationIsApplicableWithMultipleRequiredLocales() {
		QaProfile profile = profileWith(new QaDomainDefinition(
				"LOCALIZATION", QaDomainApplicability.CONDITIONAL, "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES", List.of("LOCALE_CHECK"), List.of()));
		ProductAuthorityContext context = new ProductAuthorityContext(3, Map.of(), true);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		assertThat(resolved).containsExactly(
				new ResolvedDomainApplicabilityResult("LOCALIZATION", ResolvedDomainApplicability.APPLICABLE, List.of("LOCALE_CHECK")));
	}

	@Test
	void localizationApplicabilityIsDrivenByTheLocaleCountNotAConditionSignalOverride() {
		// Even if a caller mistakenly places a signal under the locale condition key, the
		// structural locale-count derivation is authoritative - "derived from Product Authority,
		// not Agent preference" means this resolver trusts its own deterministic count logic.
		QaProfile profile = profileWith(new QaDomainDefinition(
				"LOCALIZATION", QaDomainApplicability.CONDITIONAL, "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES", List.of("LOCALE_CHECK"), List.of()));
		ProductAuthorityContext context =
				new ProductAuthorityContext(1, Map.of("MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES", true), true);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		assertThat(resolved.get(0).outcome()).isEqualTo(ResolvedDomainApplicability.NOT_APPLICABLE);
	}

	@Test
	void integrationBehaviorIsNotApplicableWhenItsConditionIsFalseRegardlessOfAuthority() {
		QaProfile profile = profileWith(new QaDomainDefinition(
				"INTEGRATION_BEHAVIOR", QaDomainApplicability.CONDITIONAL, "AUTHORIZED_BOUND_CAPABILITY_APPLICABLE",
				List.of("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY"), List.of()));
		ProductAuthorityContext context =
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", false), false);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		assertThat(resolved.get(0).outcome()).isEqualTo(ResolvedDomainApplicability.NOT_APPLICABLE);
	}

	@Test
	void integrationBehaviorIsApplicableWhenRelevantAndAuthorityIsPresent() {
		QaProfile profile = profileWith(new QaDomainDefinition(
				"INTEGRATION_BEHAVIOR", QaDomainApplicability.CONDITIONAL, "AUTHORIZED_BOUND_CAPABILITY_APPLICABLE",
				List.of("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY"), List.of()));
		ProductAuthorityContext context =
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", true), true);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		assertThat(resolved.get(0).outcome()).isEqualTo(ResolvedDomainApplicability.APPLICABLE);
	}

	@Test
	void integrationBehaviorIsMissingRequiredAuthorityWhenRelevantButNoAuthorityIsConfigured() {
		QaProfile profile = profileWith(new QaDomainDefinition(
				"INTEGRATION_BEHAVIOR", QaDomainApplicability.CONDITIONAL, "AUTHORIZED_BOUND_CAPABILITY_APPLICABLE",
				List.of("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY"), List.of()));
		ProductAuthorityContext context =
				new ProductAuthorityContext(1, Map.of("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE", true), false);

		List<ResolvedDomainApplicabilityResult> resolved = resolver.resolve(profile, context);

		// Never silently NOT_APPLICABLE - the required check codes are still carried so a caller
		// can raise an Authority Issue against exactly what was needed.
		assertThat(resolved.get(0).outcome()).isEqualTo(ResolvedDomainApplicability.MISSING_REQUIRED_AUTHORITY);
		assertThat(resolved.get(0).requiredChecks()).containsExactly("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY");
	}

	private QaProfile profileWith(QaDomainDefinition... domains) {
		return new QaProfile(
				"test-profile@1.0.0", QaProfileType.FULL_RELEASE, List.of(), List.of(), List.of(domains),
				new FindingDispositionPolicy(Map.of(), Map.of()), java.util.Optional.empty(),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE), new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}

	private ProductAuthorityContext emptyContext() {
		return new ProductAuthorityContext(1, Map.of(), false);
	}
}
