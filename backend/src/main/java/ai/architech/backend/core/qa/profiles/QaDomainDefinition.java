package ai.architech.backend.core.qa.profiles;

import java.util.List;

/**
 * One {@code domains.<DOMAIN>} entry from a frozen profile (AIW-175). {@link #conditionKey()} is
 * the raw {@code when:} value (e.g. {@code "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES"}) - only
 * present when {@link #applicability()} is {@link QaDomainApplicability#CONDITIONAL}.
 */
public record QaDomainDefinition(
		String domain,
		QaDomainApplicability applicability,
		String conditionKey,
		List<String> requiredChecks,
		List<String> evidenceChecks) {}
