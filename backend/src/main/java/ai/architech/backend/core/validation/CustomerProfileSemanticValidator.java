package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Layer-5 (artifact semantic) checks for Customer Profile candidates that the frozen JSON
 * Schema cannot express - cross-field and cross-entity rules from
 * {@code docs/core/REQUIREMENTS_VALIDATOR_CHECKLIST.md}. Assumes the candidate already passed
 * {@link ArtifactSchemaValidator} (schema-required fields/types/enums are trusted here); this
 * class only adds the semantic layer on top. localRef uniqueness and evidence-snapshot
 * sourceRef membership are separate concerns, covered by {@link LocalRefUniquenessValidator}
 * and {@link SourceRefValidator} respectively - not duplicated here. Detects and reports
 * only: never repairs or mutates the candidate.
 */
@Component
public class CustomerProfileSemanticValidator {

	private static final Set<String> LOCATION_PROVENANCE_FIELDS =
			Set.of("name", "street", "postalCode", "city", "countryCode", "raw");
	private static final Set<String> OFFERING_PROVENANCE_FIELDS = Set.of("name", "description", "category", "price");
	private static final Set<String> OPENING_HOURS_PROVENANCE_FIELDS = Set.of("locationRefs", "schedule", "closedDays", "raw");
	// Both the bare field name ("name") and the singleton-qualified form ("business.name") are
	// accepted - a provenance entry with no targetRef has no other way to disambiguate which
	// singleton a field belongs to when business/contact happen to share a name (they don't
	// currently, but nothing requires that), so "business.name"/"contact.phone" is a
	// legitimate, arguably clearer way for an agent to write this - not a mistake to reject.
	private static final Set<String> SINGLETON_PROVENANCE_FIELDS = Set.of(
			"name",
			"description",
			"languages",
			"email",
			"phone",
			"website",
			"business.name",
			"business.description",
			"business.languages",
			"contact.email",
			"contact.phone",
			"contact.website");
	private static final Set<String> SCHEDULE_DAYS = Set.of("MO", "TU", "WE", "TH", "FR", "SA", "SU");

	private final ObjectMapper objectMapper;

	CustomerProfileSemanticValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public CustomerProfileValidationResult validate(String candidateJson) {
		JsonNode root;
		try {
			root = objectMapper.readTree(candidateJson);
		} catch (RuntimeException e) {
			return new CustomerProfileValidationResult(
					false, List.of(new CustomerProfileValidationIssue("$", "candidate is not valid JSON: " + e.getMessage())));
		}

		List<CustomerProfileValidationIssue> issues = new ArrayList<>();

		Set<String> locationRefs = localRefsOf(root.path("locations"));
		Set<String> offeringRefs = localRefsOf(root.path("offerings"));
		Set<String> openingHoursRefs = localRefsOf(root.path("openingHours"));

		checkOpeningHoursLocationRefs(root.path("openingHours"), locationRefs, issues);
		checkOpeningHoursIntervals(root.path("openingHours"), issues);
		checkProvenance(root.path("provenance"), locationRefs, offeringRefs, openingHoursRefs, issues);
		checkPrices(root.path("offerings"), issues);
		checkAmbiguousUnknowns(root.path("unknowns"), issues);
		checkDistinctConflictStatements(root.path("conflicts"), issues);
		checkPhone(root.path("contact"), issues);

		return issues.isEmpty() ? CustomerProfileValidationResult.passed() : new CustomerProfileValidationResult(false, issues);
	}

	private Set<String> localRefsOf(JsonNode array) {
		Set<String> refs = new HashSet<>();
		if (array.isArray()) {
			for (JsonNode item : array) {
				JsonNode localRef = item.path("localRef");
				if (localRef.isTextual()) {
					refs.add(localRef.asString());
				}
			}
		}
		return refs;
	}

	private void checkOpeningHoursLocationRefs(
			JsonNode openingHours, Set<String> locationRefs, List<CustomerProfileValidationIssue> issues) {
		if (!openingHours.isArray()) {
			return;
		}
		int setIndex = 0;
		for (JsonNode set : openingHours) {
			JsonNode locationRefsNode = set.path("locationRefs");
			if (locationRefsNode.isArray()) {
				int refIndex = 0;
				for (JsonNode ref : locationRefsNode) {
					if (ref.isTextual() && !locationRefs.contains(ref.asString())) {
						issues.add(new CustomerProfileValidationIssue(
								"/openingHours/%d/locationRefs/%d".formatted(setIndex, refIndex),
								"locationRef '" + ref.asString() + "' does not resolve to an existing location"));
					}
					refIndex++;
				}
			}
			setIndex++;
		}
	}

	private void checkOpeningHoursIntervals(JsonNode openingHours, List<CustomerProfileValidationIssue> issues) {
		if (!openingHours.isArray()) {
			return;
		}
		int setIndex = 0;
		for (JsonNode set : openingHours) {
			String setPath = "/openingHours/%d".formatted(setIndex);
			Map<String, List<int[]>> intervalsByDay = new LinkedHashMap<>();
			Set<String> scheduledDays = new HashSet<>();

			JsonNode schedule = set.path("schedule");
			if (schedule.isArray()) {
				int entryIndex = 0;
				for (JsonNode entry : schedule) {
					List<String> days = new ArrayList<>();
					entry.path("days").forEach(day -> days.add(day.asString()));
					scheduledDays.addAll(days);

					JsonNode intervals = entry.path("intervals");
					if (intervals.isArray()) {
						int intervalIndex = 0;
						for (JsonNode interval : intervals) {
							String from = interval.path("from").asString();
							String to = interval.path("to").asString();
							int fromMinutes = minutesOf(from);
							int toMinutes = minutesOf(to);
							if (fromMinutes >= toMinutes) {
								issues.add(new CustomerProfileValidationIssue(
										"%s/schedule/%d/intervals/%d".formatted(setPath, entryIndex, intervalIndex),
										"interval 'from' (" + from + ") must be before 'to' (" + to + ")"));
							} else {
								for (String day : days) {
									intervalsByDay
											.computeIfAbsent(day, d -> new ArrayList<>())
											.add(new int[] {fromMinutes, toMinutes});
								}
							}
							intervalIndex++;
						}
					}
					entryIndex++;
				}
			}

			for (Map.Entry<String, List<int[]>> dayIntervals : intervalsByDay.entrySet()) {
				List<int[]> sorted = dayIntervals.getValue();
				sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
				for (int i = 1; i < sorted.size(); i++) {
					if (sorted.get(i)[0] < sorted.get(i - 1)[1]) {
						issues.add(new CustomerProfileValidationIssue(
								"%s/schedule".formatted(setPath),
								"overlapping intervals on " + dayIntervals.getKey()));
						break;
					}
				}
			}

			Set<String> closedDays = new HashSet<>();
			set.path("closedDays").forEach(day -> closedDays.add(day.asString()));
			closedDays.retainAll(scheduledDays);
			if (!closedDays.isEmpty()) {
				issues.add(new CustomerProfileValidationIssue(
						"%s/closedDays".formatted(setPath),
						"closedDays overlap scheduled days: " + String.join(", ", closedDays)));
			}

			setIndex++;
		}
	}

	private int minutesOf(String hhmm) {
		int hours = Integer.parseInt(hhmm.substring(0, 2));
		int minutes = Integer.parseInt(hhmm.substring(3, 5));
		return hours * 60 + minutes;
	}

	private void checkProvenance(
			JsonNode provenance,
			Set<String> locationRefs,
			Set<String> offeringRefs,
			Set<String> openingHoursRefs,
			List<CustomerProfileValidationIssue> issues) {
		if (!provenance.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode entry : provenance) {
			String path = "/provenance/%d".formatted(index);
			JsonNode targetRefNode = entry.path("targetRef");
			JsonNode fieldNode = entry.path("field");

			if (targetRefNode.isTextual()) {
				String targetRef = targetRefNode.asString();
				Set<String> allowedFields;
				if (locationRefs.contains(targetRef)) {
					allowedFields = LOCATION_PROVENANCE_FIELDS;
				} else if (offeringRefs.contains(targetRef)) {
					allowedFields = OFFERING_PROVENANCE_FIELDS;
				} else if (openingHoursRefs.contains(targetRef)) {
					allowedFields = OPENING_HOURS_PROVENANCE_FIELDS;
				} else {
					issues.add(new CustomerProfileValidationIssue(
							path, "targetRef '" + targetRef + "' does not resolve to a location, offering or opening-hours set"));
					index++;
					continue;
				}
				if (fieldNode.isTextual() && !allowedFields.contains(fieldNode.asString())) {
					issues.add(new CustomerProfileValidationIssue(
							path, "field '" + fieldNode.asString() + "' is not a valid field for its provenance target"));
				}
			} else if (fieldNode.isTextual() && !SINGLETON_PROVENANCE_FIELDS.contains(fieldNode.asString())) {
				issues.add(new CustomerProfileValidationIssue(
						path,
						"field '" + fieldNode.asString()
								+ "' is not a valid business/contact field for a targetRef-less provenance entry"));
			}
			index++;
		}
	}

	private void checkPrices(JsonNode offerings, List<CustomerProfileValidationIssue> issues) {
		if (!offerings.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode offering : offerings) {
			JsonNode price = offering.path("price");
			if (price.isObject()) {
				String path = "/offerings/%d/price".formatted(index);
				checkPriceFieldCombination(price, path, issues);
				checkCurrency(price, path, issues);
			}
			index++;
		}
	}

	private void checkPriceFieldCombination(JsonNode price, String path, List<CustomerProfileValidationIssue> issues) {
		String type = price.path("type").asString();
		boolean hasAmount = price.hasNonNull("amount");
		boolean hasMaxAmount = price.hasNonNull("maxAmount");

		switch (type) {
			case "fixed", "from" -> {
				if (!hasAmount) {
					issues.add(new CustomerProfileValidationIssue(path, "price type '" + type + "' requires 'amount'"));
				}
				if (hasMaxAmount) {
					issues.add(new CustomerProfileValidationIssue(path, "price type '" + type + "' must not have 'maxAmount'"));
				}
			}
			case "range" -> {
				if (!hasAmount || !hasMaxAmount) {
					issues.add(new CustomerProfileValidationIssue(path, "price type 'range' requires both 'amount' and 'maxAmount'"));
				} else if (price.path("maxAmount").asDouble() < price.path("amount").asDouble()) {
					issues.add(new CustomerProfileValidationIssue(path, "'maxAmount' must be >= 'amount' for price type 'range'"));
				}
			}
			case "on-request" -> {
				if (hasAmount || hasMaxAmount) {
					issues.add(new CustomerProfileValidationIssue(
							path, "price type 'on-request' must not have 'amount' or 'maxAmount'"));
				}
			}
			default -> {
				// "other": the checklist leaves amount/maxAmount unconstrained for this type.
			}
		}
	}

	private void checkCurrency(JsonNode price, String path, List<CustomerProfileValidationIssue> issues) {
		JsonNode currency = price.path("currency");
		if (currency.isTextual()) {
			try {
				java.util.Currency.getInstance(currency.asString());
			} catch (IllegalArgumentException e) {
				issues.add(new CustomerProfileValidationIssue(
						path + "/currency", "'" + currency.asString() + "' is not a recognized ISO 4217 currency code"));
			}
		}
	}

	private void checkPhone(JsonNode contact, List<CustomerProfileValidationIssue> issues) {
		JsonNode phone = contact.path("phone");
		if (!phone.isTextual()) {
			return;
		}
		String stripped = phone.asString().replaceAll("[\\s()./-]", "");
		if (!stripped.matches("\\+?\\d{6,20}")) {
			issues.add(new CustomerProfileValidationIssue("/contact/phone", "phone value does not look like a plausible phone number"));
		}
	}

	private void checkAmbiguousUnknowns(JsonNode unknowns, List<CustomerProfileValidationIssue> issues) {
		if (!unknowns.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode unknown : unknowns) {
			if ("ambiguous".equals(unknown.path("kind").asString())) {
				JsonNode sourceRefs = unknown.path("sourceRefs");
				if (!sourceRefs.isArray() || sourceRefs.isEmpty()) {
					issues.add(new CustomerProfileValidationIssue(
							"/unknowns/%d".formatted(index), "ambiguous unknown must contain supporting sourceRefs"));
				}
			}
			index++;
		}
	}

	private void checkDistinctConflictStatements(JsonNode conflicts, List<CustomerProfileValidationIssue> issues) {
		if (!conflicts.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode conflict : conflicts) {
			Set<String> distinctValues = new HashSet<>();
			conflict.path("statements").forEach(statement -> {
				String value = statement.path("value").asString();
				if (value != null) {
					distinctValues.add(value.strip().toLowerCase(Locale.ROOT));
				}
			});
			if (distinctValues.size() < 2) {
				issues.add(new CustomerProfileValidationIssue(
						"/conflicts/%d/statements".formatted(index),
						"conflict must preserve at least two materially distinct statements"));
			}
			index++;
		}
	}
}
