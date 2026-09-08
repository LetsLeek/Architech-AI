package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

@SpringBootTest
class ArtifactSchemaValidatorTests {

	private static final String CUSTOMER_PROFILE_SCHEMA_PATH =
			"classpath:project-types/website/schemas/customer-profile.schema.json";
	private static final String WEBSITE_REQUIREMENTS_SCHEMA_PATH =
			"classpath:project-types/website/schemas/website-requirements.schema.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private ArtifactSchemaValidator validator;

	@Test
	void acceptsAMinimalValidCustomerProfile() {
		String json =
				"""
				{
				  "business": {},
				  "contact": {},
				  "locations": [],
				  "offerings": [],
				  "openingHours": [],
				  "socialLinks": [],
				  "providedClaims": [],
				  "unknowns": [],
				  "conflicts": [],
				  "provenance": []
				}
				""";

		SchemaValidationResult result = validator.validate(customerProfileSchema(), json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsACustomerProfileMissingARequiredField() {
		String json =
				"""
				{
				  "business": {},
				  "contact": {},
				  "locations": [],
				  "offerings": [],
				  "openingHours": [],
				  "socialLinks": [],
				  "providedClaims": [],
				  "unknowns": [],
				  "conflicts": []
				}
				""";

		SchemaValidationResult result = validator.validate(customerProfileSchema(), json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void rejectsACustomerProfileWithAnUnknownField() {
		String json =
				"""
				{
				  "business": {},
				  "contact": {},
				  "locations": [],
				  "offerings": [],
				  "openingHours": [],
				  "socialLinks": [],
				  "providedClaims": [],
				  "unknowns": [],
				  "conflicts": [],
				  "provenance": [],
				  "somethingNotInTheSchema": true
				}
				""";

		SchemaValidationResult result = validator.validate(customerProfileSchema(), json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsAMinimalValidWebsiteRequirements() {
		String json =
				"""
				{
				  "goals": [],
				  "targetAudiences": [],
				  "contentRequirements": [],
				  "functionalRequirements": [],
				  "languages": [],
				  "constraints": [],
				  "unknowns": [],
				  "conflicts": []
				}
				""";

		SchemaValidationResult result = validator.validate(websiteRequirementsSchema(), json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void reportsUnparseableCandidateAsAFailureRatherThanThrowing() {
		SchemaValidationResult result = validator.validate(customerProfileSchema(), "this is not json at all {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	private String customerProfileSchema() {
		return readSchema(CUSTOMER_PROFILE_SCHEMA_PATH);
	}

	private String websiteRequirementsSchema() {
		return readSchema(WEBSITE_REQUIREMENTS_SCHEMA_PATH);
	}

	private String readSchema(String classpathLocation) {
		try {
			return resourceLoader.getResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
