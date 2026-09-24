package ai.architech.backend.core.documentation.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real-classpath proof of AIW-188's own "both frozen Documentation profile YAMLs are loaded and
 * versioned", mirroring {@code QaProfileLoaderIT}.
 */
@SpringBootTest
class DocumentationProfileLoaderIT {

	@Autowired
	private DocumentationProfileLoader loader;

	@Test
	void resolvesTheCustomerHandoverProfile() {
		DocumentationProfile profile = loader.resolve("CUSTOMER_HANDOVER@1.0.0");

		assertThat(profile.ref()).isEqualTo("CUSTOMER_HANDOVER@1.0.0");
		assertThat(profile.profileType()).isEqualTo(DocumentationProfileType.CUSTOMER_HANDOVER);
		assertThat(profile.primaryAudience()).isEqualTo("CUSTOMER");
		assertThat(profile.compositionSkill()).isEqualTo("customer-handover-composition");
	}

	@Test
	void resolvesTheTechnicalHandoverProfile() {
		DocumentationProfile profile = loader.resolve("TECHNICAL_HANDOVER@1.0.0");

		assertThat(profile.ref()).isEqualTo("TECHNICAL_HANDOVER@1.0.0");
		assertThat(profile.profileType()).isEqualTo(DocumentationProfileType.TECHNICAL_HANDOVER);
		assertThat(profile.primaryAudience()).isEqualTo("DEVELOPER");
		assertThat(profile.compositionSkill()).isEqualTo("technical-handover-composition");
	}

	@Test
	void customerHandoverAllowsOnlyThePassGateWhileTechnicalHandoverAlsoAllowsHold() {
		DocumentationProfile customer = loader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationProfile technical = loader.resolve("TECHNICAL_HANDOVER@1.0.0");

		assertThat(customer.qaPreconditions().qaProfile()).isEqualTo("FULL_RELEASE");
		assertThat(customer.qaPreconditions().allowedGates()).containsExactly("PASS");
		assertThat(technical.qaPreconditions().allowedGates()).containsExactly("PASS", "HOLD");
	}

	@Test
	void onlyTechnicalHandoverDeclaresACandidateSafeProjection() {
		DocumentationProfile customer = loader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationProfile technical = loader.resolve("TECHNICAL_HANDOVER@1.0.0");

		assertThat(customer.candidateSafeProjectionRequired()).isEmpty();
		assertThat(technical.candidateSafeProjectionRequired()).isPresent();
		assertThat(technical.candidateSafeProjectionRequired().get()).hasSize(6).contains("RUNTIME_PROFILE", "ROUTE_MANIFEST");
	}

	@Test
	void bothProfilesShareTheSameFourteenAllowedClaimTypes() {
		DocumentationProfile customer = loader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationProfile technical = loader.resolve("TECHNICAL_HANDOVER@1.0.0");

		assertThat(customer.allowedClaimTypes()).hasSize(14).containsExactlyInAnyOrderElementsOf(technical.allowedClaimTypes());
	}

	@Test
	void customerHandoverHasNoDeterministicReportsWhileTechnicalHandoverRequiresFive() {
		DocumentationProfile customer = loader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationProfile technical = loader.resolve("TECHNICAL_HANDOVER@1.0.0");

		assertThat(customer.deterministicReports()).isEmpty();
		assertThat(technical.deterministicReports()).hasSize(5);
		assertThat(technical.deterministicReports()).allMatch(r -> r.deliveryDisposition().equals("DEVELOPER_VISIBLE"));
	}

	@Test
	void rejectsAnUnknownProfileRef() {
		assertThatThrownBy(() -> loader.resolve("MADE_UP_PROFILE@1.0.0")).isInstanceOf(DocumentationProfileNotFoundException.class);
	}
}
