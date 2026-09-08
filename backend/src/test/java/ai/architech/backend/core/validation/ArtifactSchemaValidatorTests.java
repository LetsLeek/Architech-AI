package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@SpringBootTest
class ArtifactSchemaValidatorTests {

	private static final String CUSTOMER_PROFILE_SCHEMA =
			"classpath:project-types/website/schemas/customer-profile.schema.json";
	private static final String WEBSITE_REQUIREMENTS_SCHEMA =
			"classpath:project-types/website/schemas/website-requirements.schema.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private ArtifactSchemaValidator validator;

	@Test
	void acceptsAMinimalValidCustomerProfile() {
		Resource schema = resourceLoader.getResource(CUSTOMER_PROFILE_SCHEMA);
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

		SchemaValidationResult result = validator.validate(schema, json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsACustomerProfileMissingARequiredField() {
		Resource schema = resourceLoader.getResource(CUSTOMER_PROFILE_SCHEMA);
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

		SchemaValidationResult result = validator.validate(schema, json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void rejectsACustomerProfileWithAnUnknownField() {
		Resource schema = resourceLoader.getResource(CUSTOMER_PROFILE_SCHEMA);
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

		SchemaValidationResult result = validator.validate(schema, json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsAMinimalValidWebsiteRequirements() {
		Resource schema = resourceLoader.getResource(WEBSITE_REQUIREMENTS_SCHEMA);
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

		SchemaValidationResult result = validator.validate(schema, json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void reportsUnparseableCandidateAsAFailureRatherThanThrowing() {
		Resource schema = resourceLoader.getResource(CUSTOMER_PROFILE_SCHEMA);

		SchemaValidationResult result = validator.validate(schema, "this is not json at all {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
