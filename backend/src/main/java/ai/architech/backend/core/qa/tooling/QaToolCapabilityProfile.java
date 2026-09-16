package ai.architech.backend.core.qa.tooling;

import java.util.List;

/**
 * A versioned, resolved-per-execution Website QA V1 tool capability grant (AIW-171) -
 * {@code QaExecution.toolCapabilityProfileRef} names exactly one of these by {@link #ref()}.
 * Entirely read-only/observational by construction: unlike Developer's own {@code
 * tool-capability-profile.v1.yaml} (which grants filesystem write, project execution and bounded
 * git operations), this type has no field through which any mutation capability could even be
 * expressed - there is no filesystem-write, project-execution or git-mutation section to grant in
 * the first place, which is the structural half of "No unrestricted shell, arbitrary package
 * installation, host filesystem, Docker socket, cloud/deployment credentials or unrestricted
 * outbound networking is available." The other half - that the fields which *can* express a
 * relaxation of this profile's safety contract are never actually relaxed - is checked at load
 * time by {@link QaToolCapabilityProfileLoader}, not merely declared here.
 */
public record QaToolCapabilityProfile(
		String ref,
		List<QaToolCapability> capabilities,
		Browser browser,
		SourceInspection sourceInspection,
		Network network,
		SafeIntegrationTest safeIntegrationTest,
		Security security) {

	public boolean supports(QaToolCapability capability) {
		return capabilities.contains(capability);
	}

	/** "Browser ... access is limited to Candidate surface" - the QA analogue of Developer's own {@code browser.localRuntimeOnly}. */
	public record Browser(boolean candidateSurfaceOnly) {}

	/** {@code rules/target-input-integrity.md}'s "MUST resolve source inspection to the Candidate's exact immutable repository state." */
	public record SourceInspection(List<String> allowed, boolean readOnly) {}

	/** "... network access is limited to Candidate surface and explicit allowlists." */
	public record Network(boolean rawOutbound, boolean allowedOnlyThroughAuthorizedCapabilities, List<String> allowlist) {}

	/** "Safe integration tools encapsulate credentials and authorized test behavior" / "Real external side effects are denied by default." */
	public record SafeIntegrationTest(boolean realSideEffects, boolean credentialEncapsulation) {}

	/** One field per AIW-171's own named prohibition - every field here is required to be {@code false}, enforced by {@link QaToolCapabilityProfileLoader}. */
	public record Security(
			boolean unrestrictedShell,
			boolean arbitraryPackageInstallation,
			boolean hostFilesystem,
			boolean dockerSocket,
			boolean cloudCredentials,
			boolean deploymentCredentials,
			boolean unrestrictedOutboundNetworking) {}
}
