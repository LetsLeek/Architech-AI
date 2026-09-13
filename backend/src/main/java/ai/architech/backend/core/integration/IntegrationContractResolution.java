package ai.architech.backend.core.integration;

/**
 * The three outcomes {@link IntegrationContractResolver#resolve} can reach, matching AIW-144's
 * (and, downstream, AIW-142's {@code FunctionalBinding} validation) own named semantics:
 * {@code MISSING_INTEGRATION_CONTRACT} when nothing authorizes this {@code contractRef} for this
 * project at all, {@code INVALID_INTEGRATION_CONTRACT} when a row exists but its persisted safe
 * content does not itself satisfy {@code urn:aiw:schema:developer-safe-integration-contract-view:v1},
 * and the authorized case otherwise.
 */
public sealed interface IntegrationContractResolution {

	record Authorized(IntegrationContract contract) implements IntegrationContractResolution {}

	record Missing() implements IntegrationContractResolution {}

	record Invalid(IntegrationContract contract, String validationError) implements IntegrationContractResolution {}
}
