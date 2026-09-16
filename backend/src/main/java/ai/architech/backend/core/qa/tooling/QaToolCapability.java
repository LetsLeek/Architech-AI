package ai.architech.backend.core.qa.tooling;

/**
 * The bounded, read-only capability domains Website QA V1's tool profile can grant (AIW-171) -
 * one value per {@code registries/qa-checks.yaml}'s own {@code capability:} column value, plus
 * {@code SOURCE_INSPECTION} for the frozen package's own "Read-only exact-Candidate source
 * inspection" scope bullet (no deterministic check in the frozen registry names it directly,
 * since deterministic checks are browser/DOM-driven, but {@code rules/target-input-integrity.md}'s
 * "MUST resolve source inspection to the Candidate's exact immutable repository state" and {@code
 * rules/evidence.md}'s "read-only source references" evidence kind both require it).
 *
 * <p>Deliberately excludes {@code PLATFORM_CORE} - every {@code qa-checks.yaml} check tagged with
 * it (e.g. {@code AUTHORITY_REFERENCE_INTEGRITY}, {@code CANDIDATE_SOURCE_IDENTITY}) is a pure
 * reference-integrity check against already-persisted platform rows (a {@code QaExecution}, a
 * {@code WebsiteImplementationCandidate}), never against anything external a sandboxed capability
 * grant would need to bound.
 *
 * <p>Being a closed, versioned enum is itself part of AIW-171's own "Capability absence/failure
 * yields Evaluation Issue semantics instead of improvised tools" requirement: nothing outside
 * this fixed set can ever be requested as a QA tool capability in the first place.
 */
public enum QaToolCapability {
	BROWSER_AUTOMATION,
	DOM_INSPECTION,
	ACCESSIBILITY_TREE,
	ACCESSIBILITY_SCANNER,
	BROWSER_CONSOLE_INSPECTION,
	NETWORK_INSPECTION,
	LAYOUT_MEASUREMENT,
	METADATA_INSPECTION,
	PERFORMANCE_MEASUREMENT,
	SOURCE_INSPECTION,
	SAFE_INTEGRATION_TEST
}
