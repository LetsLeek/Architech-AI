package ai.architech.backend.core.qa.profiles;

/**
 * One domain's applicability after resolving its profile-declared {@link QaDomainApplicability}
 * against real Product Authority signals (AIW-175). {@link #MISSING_REQUIRED_AUTHORITY} is
 * distinct from {@link #NOT_APPLICABLE}: it means the domain's own condition evaluated true (the
 * domain genuinely applies) but the authority required to actually evaluate it is missing -
 * {@code rules}' own "Missing required Integration Authority cannot be converted to
 * NOT_APPLICABLE; it yields Authority Issue semantics" - never silently skipped.
 */
public enum ResolvedDomainApplicability {
	REQUIRED,
	APPLICABLE,
	NOT_APPLICABLE,
	MISSING_REQUIRED_AUTHORITY
}
