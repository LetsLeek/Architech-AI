package ai.architech.backend.core.documentation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DocumentationPolicyLoaderIT {

	@Autowired
	private DocumentationPolicyLoader loader;

	@Test
	void loadsTheFrozenPolicyWithItsExpectedRef() {
		DocumentationPolicy policy = loader.load();

		assertThat(policy.ref()).isEqualTo("DOCUMENTATION_POLICY@1.0.0");
	}

	@Test
	void securityBlockRequiresFieldMinimizationAndFailsClosed() {
		SecurityPolicy security = loader.load().security();

		assertThat(security.forbidSecretValues()).isTrue();
		assertThat(security.fieldLevelMinimization()).isTrue();
		assertThat(security.preModelSecurityRequired()).isTrue();
		assertThat(security.postGenerationSecurityBeforeSemanticModel()).isTrue();
		assertThat(security.failClosed()).isTrue();
		assertThat(security.audienceClassificationIndependent()).isTrue();
	}

	@Test
	void epistemicBlockForbidsInferenceAndBlocksLineageIncompatibilityPreGeneration() {
		EpistemicPolicy epistemic = loader.load().epistemic();

		assertThat(epistemic.noInference()).isTrue();
		assertThat(epistemic.lineageIncompatibility()).isEqualTo("BLOCK_PRE_GENERATION");
	}

	@Test
	void customerDisclosureRulesRequireFullReleasePassAndBlockOnUnmappedMaterialFindings() {
		CustomerDisclosureRules customer = loader.load().findingDisclosure().customer();

		assertThat(customer.qaRequiredProfile()).isEqualTo("FULL_RELEASE");
		assertThat(customer.qaRequiredGate()).isEqualTo("PASS");
		assertThat(customer.materialCustomerImpact()).isEqualTo("DISCLOSE");
		assertThat(customer.unmappedMaterialFallback()).isEqualTo("BLOCK_DOCUMENT");
	}

	@Test
	void developerDisclosureRulesAllowBothPassAndHoldAndDiscloseAllTechnicallyRelevantCurrentFindings() {
		DeveloperDisclosureRules developer = loader.load().findingDisclosure().developer();

		assertThat(developer.qaAllowedGates()).containsExactly("PASS", "HOLD");
		assertThat(developer.allTechnicallyRelevantCurrent()).isEqualTo("DISCLOSE");
		assertThat(developer.materialSecurityDetail()).isEqualTo("DISCLOSE_REDACTED_IMPACT_ONLY");
	}

	@Test
	void validationBlockRequiresSemanticFactualConsistencyAndBlocksUnsupportedClaims() {
		ValidationPolicy validation = loader.load().validation();

		assertThat(validation.semanticFactualConsistencyRequired()).isTrue();
		assertThat(validation.unsupportedClaimBlocks()).isTrue();
		assertThat(validation.notEvaluableBlocks()).isTrue();
		assertThat(validation.includeBoundedCounterfacts()).isTrue();
	}

	@Test
	void profileSpecificOverridesForbidWeakeningGlobalAuthoritySecretsAndSemanticValidationBypass() {
		ProfileSpecificOverrides overrides = loader.load().profileSpecificOverrides();

		assertThat(overrides.allowWeakeningGlobalAuthority()).isFalse();
		assertThat(overrides.allowSecrets()).isFalse();
		assertThat(overrides.allowBypassSemanticValidation()).isFalse();
	}
}
