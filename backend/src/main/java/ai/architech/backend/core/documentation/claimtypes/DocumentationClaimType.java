package ai.architech.backend.core.documentation.claimtypes;

import java.util.List;

/**
 * One {@code registries/claim-types.yaml} entry (AIW-196). {@code domainMinimum} is, per the
 * registry's own {@code note} field on every entry, "a starting precheck; statement-specific
 * sufficiency requires bounded semantic evaluation" - the deeper semantic check is AIW-198's
 * job, this record only carries the structural minimum-domain-presence data.
 */
public record DocumentationClaimType(String id, List<String> domainMinimum) {}
