package ai.architech.backend.core.qa.profiles;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministically resolves every domain in a {@link QaProfile} against real {@link
 * ProductAuthorityContext} signals (AIW-175) - never against any Agent preference or model
 * output. {@code REQUIRED}/{@code NOT_APPLICABLE} domains resolve unconditionally; {@code
 * CONDITIONAL} domains evaluate their own {@code when:} condition key.
 */
@Component
public class QaDomainApplicabilityResolver {

	private static final String MULTIPLE_LOCALES_CONDITION = "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES";
	private static final String INTEGRATION_BEHAVIOR_DOMAIN = "INTEGRATION_BEHAVIOR";

	public List<ResolvedDomainApplicabilityResult> resolve(QaProfile profile, ProductAuthorityContext context) {
		return profile.domains().stream().map(domain -> resolveOne(domain, context)).toList();
	}

	private ResolvedDomainApplicabilityResult resolveOne(QaDomainDefinition domain, ProductAuthorityContext context) {
		return switch (domain.applicability()) {
			case REQUIRED -> new ResolvedDomainApplicabilityResult(domain.domain(), ResolvedDomainApplicability.REQUIRED, domain.requiredChecks());
			case NOT_APPLICABLE -> new ResolvedDomainApplicabilityResult(domain.domain(), ResolvedDomainApplicability.NOT_APPLICABLE, List.of());
			case CONDITIONAL -> resolveConditional(domain, context);
		};
	}

	private ResolvedDomainApplicabilityResult resolveConditional(QaDomainDefinition domain, ProductAuthorityContext context) {
		boolean conditionTrue = resolveCondition(domain.conditionKey(), context);
		if (!conditionTrue) {
			return new ResolvedDomainApplicabilityResult(domain.domain(), ResolvedDomainApplicability.NOT_APPLICABLE, List.of());
		}
		if (INTEGRATION_BEHAVIOR_DOMAIN.equals(domain.domain()) && !context.integrationAuthorityPresent()) {
			return new ResolvedDomainApplicabilityResult(
					domain.domain(), ResolvedDomainApplicability.MISSING_REQUIRED_AUTHORITY, domain.requiredChecks());
		}
		return new ResolvedDomainApplicabilityResult(domain.domain(), ResolvedDomainApplicability.APPLICABLE, domain.requiredChecks());
	}

	private boolean resolveCondition(String conditionKey, ProductAuthorityContext context) {
		if (MULTIPLE_LOCALES_CONDITION.equals(conditionKey)) {
			return context.requiredCustomerVisibleLocaleCount() > 1;
		}
		return Boolean.TRUE.equals(context.conditionSignals().get(conditionKey));
	}
}
