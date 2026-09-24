package ai.architech.backend.core.qa.profiles;

import java.util.Map;

/**
 * The Product Authority-derived signals {@link QaDomainApplicabilityResolver} resolves a
 * profile's {@code CONDITIONAL} domains against (AIW-175) - {@code rules}' own "Conditional
 * Localization and Integration applicability is derived from Product Authority, not Agent
 * preference": every field here is a fact about the Candidate's actual authority (how many
 * required customer-visible locales exist, whether an authorized bound integration capability is
 * relevant/applicable, whether real Integration Authority is actually configured), never
 * something the QA Agent itself decides.
 *
 * <p>Deriving these signals from a real {@code CustomerProfile}/{@code WebsiteRequirements}/
 * {@code IntegrationContract} is deliberately out of this ticket's scope - no loader for those
 * artifacts by ref is wired into {@code core.qa} yet (the same class of "build the classifier
 * before its real input source exists" gap {@code CandidateBindingValidator#validateExecutionSurface}
 * already documents for AIW-169) - a caller assembles this record from whatever authority source
 * it has.
 *
 * <p>{@link #conditionSignals()} is keyed by a profile domain's exact {@code when:} condition key
 * (e.g. {@code "AUTHORIZED_BOUND_CAPABILITY_APPLICABLE"}) for every condition other than {@code
 * "MULTIPLE_REQUIRED_CUSTOMER_VISIBLE_LOCALES"}, which this resolver derives structurally from
 * {@link #requiredCustomerVisibleLocaleCount()} instead of trusting a caller-supplied boolean for
 * it.
 */
public record ProductAuthorityContext(
		int requiredCustomerVisibleLocaleCount, Map<String, Boolean> conditionSignals, boolean integrationAuthorityPresent) {}
