package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CustomerProfileSemanticValidatorTests {

	private final CustomerProfileSemanticValidator validator = new CustomerProfileSemanticValidator(new ObjectMapper());

	private static final String VALID_PROFILE =
			"""
			{
			  "business": {"name": "Acme"},
			  "contact": {"phone": "+43 660 1234567"},
			  "locations": [{"localRef": "loc-1", "name": "HQ"}],
			  "offerings": [{"localRef": "off-1", "name": "Haircut", "price": {"type": "range", "amount": 10, "maxAmount": 20, "currency": "EUR", "raw": "10-20 EUR"}}],
			  "openingHours": [{"localRef": "oh-1", "raw": "Mon 9-12", "locationRefs": ["loc-1"], "schedule": [{"days": ["MO"], "intervals": [{"from": "09:00", "to": "12:00"}]}], "closedDays": ["SU"]}],
			  "socialLinks": [],
			  "providedClaims": [],
			  "unknowns": [{"kind": "ambiguous", "field": "business.name", "description": "unclear", "sourceRefs": ["SRC-1"]}],
			  "conflicts": [{"field": "contact.phone", "description": "conflict", "statements": [{"value": "A", "sourceRefs": ["SRC-1"]}, {"value": "B", "sourceRefs": ["SRC-2"]}]}],
			  "provenance": [{"targetRef": "loc-1", "field": "name", "sourceRefs": ["SRC-1"]}]
			}
			""";

	@Test
	void acceptsAFullyValidProfile() {
		CustomerProfileValidationResult result = validator.validate(VALID_PROFILE);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsAnUnresolvedOpeningHoursLocationRef() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [{"localRef": "loc-1", "name": "HQ"}],
				  "offerings": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": [],
				  "openingHours": [{"localRef": "oh-1", "raw": "x", "locationRefs": ["loc-does-not-exist"]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("does not resolve to an existing location"));
	}

	@Test
	void rejectsARangePriceWhereMaxAmountIsBelowAmount() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "socialLinks": [], "providedClaims": [],
				  "unknowns": [], "conflicts": [], "provenance": [], "openingHours": [],
				  "offerings": [{"localRef": "off-1", "name": "x", "price": {"type": "range", "amount": 20, "maxAmount": 10, "raw": "x"}}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("maxAmount"));
	}

	@Test
	void rejectsAnOnRequestPriceThatHasAnAmount() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "socialLinks": [], "providedClaims": [],
				  "unknowns": [], "conflicts": [], "provenance": [], "openingHours": [],
				  "offerings": [{"localRef": "off-1", "name": "x", "price": {"type": "on-request", "amount": 5, "raw": "x"}}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("on-request"));
	}

	@Test
	void rejectsAnUnrecognizedCurrencyCode() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "socialLinks": [], "providedClaims": [],
				  "unknowns": [], "conflicts": [], "provenance": [], "openingHours": [],
				  "offerings": [{"localRef": "off-1", "name": "x", "price": {"type": "fixed", "amount": 5, "currency": "ZZZ", "raw": "x"}}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("ISO 4217"));
	}

	@Test
	void rejectsAnImplausiblePhoneNumber() {
		String json =
				"""
				{
				  "business": {}, "contact": {"phone": "call us maybe"}, "locations": [], "offerings": [],
				  "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": [], "openingHours": []
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("phone"));
	}

	@Test
	void rejectsAnOpeningHoursIntervalWhereFromIsNotBeforeTo() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": [],
				  "openingHours": [{"localRef": "oh-1", "raw": "x", "schedule": [{"days": ["MO"], "intervals": [{"from": "12:00", "to": "09:00"}]}]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("must be before"));
	}

	@Test
	void rejectsOverlappingIntervalsOnTheSameDay() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": [],
				  "openingHours": [{"localRef": "oh-1", "raw": "x", "schedule": [
				    {"days": ["MO"], "intervals": [{"from": "09:00", "to": "13:00"}]},
				    {"days": ["MO"], "intervals": [{"from": "12:00", "to": "17:00"}]}
				  ]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("overlapping"));
	}

	@Test
	void rejectsAClosedDayThatOverlapsAScheduledDay() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": [],
				  "openingHours": [{"localRef": "oh-1", "raw": "x",
				    "schedule": [{"days": ["MO"], "intervals": [{"from": "09:00", "to": "12:00"}]}],
				    "closedDays": ["MO"]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("closedDays overlap"));
	}

	@Test
	void rejectsAProvenanceTargetRefThatDoesNotResolve() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "openingHours": [],
				  "provenance": [{"targetRef": "does-not-exist", "sourceRefs": ["SRC-1"]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("does not resolve to a location, offering"));
	}

	@Test
	void rejectsASingletonProvenanceFieldThatIsNotABusinessOrContactField() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "openingHours": [],
				  "provenance": [{"field": "notARealField", "sourceRefs": ["SRC-1"]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("not a valid business/contact field"));
	}

	@Test
	void acceptsASingletonProvenanceFieldQualifiedWithItsSingletonName() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "conflicts": [], "openingHours": [],
				  "provenance": [
				    {"field": "business.name", "sourceRefs": ["SRC-1"]},
				    {"field": "contact.phone", "sourceRefs": ["SRC-1"]}
				  ]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsAnAmbiguousUnknownWithoutSourceRefs() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "conflicts": [], "provenance": [], "openingHours": [],
				  "unknowns": [{"kind": "ambiguous", "field": "x", "description": "unclear"}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("ambiguous unknown"));
	}

	@Test
	void rejectsConflictStatementsThatAreNotMateriallyDistinct() {
		String json =
				"""
				{
				  "business": {}, "contact": {}, "locations": [], "offerings": [], "socialLinks": [],
				  "providedClaims": [], "unknowns": [], "provenance": [], "openingHours": [],
				  "conflicts": [{"field": "x", "description": "x", "statements": [
				    {"value": "Open Monday", "sourceRefs": ["SRC-1"]},
				    {"value": "open monday", "sourceRefs": ["SRC-2"]}
				  ]}]
				}
				""";

		CustomerProfileValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("materially distinct"));
	}

	@Test
	void reportsUnparseableCandidateAsAFailureRatherThanThrowing() {
		CustomerProfileValidationResult result = validator.validate("not json {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
