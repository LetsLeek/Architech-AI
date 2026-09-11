package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Layer-5 (artifact semantic) checks for Website Requirements candidates that the frozen
 * JSON Schema cannot express - cross-field and cross-entity rules from
 * {@code docs/core/REQUIREMENTS_VALIDATOR_CHECKLIST.md}. Assumes the candidate already passed
 * {@link ArtifactSchemaValidator}. localRef uniqueness and evidence-snapshot sourceRef
 * membership are separate concerns, covered by {@link LocalRefUniquenessValidator} and
 * {@link SourceRefValidator} respectively - not duplicated here. Detects and reports only:
 * never repairs or mutates the candidate.
 */
@Component
public class WebsiteRequirementsSemanticValidator {

	private final ObjectMapper objectMapper;

	WebsiteRequirementsSemanticValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public WebsiteRequirementsValidationResult validate(String candidateJson) {
		JsonNode root;
		try {
			root = objectMapper.readTree(candidateJson);
		} catch (RuntimeException e) {
			return new WebsiteRequirementsValidationResult(
					false,
					List.of(new WebsiteRequirementsValidationIssue("$", "candidate is not valid JSON: " + e.getMessage())));
		}

		List<WebsiteRequirementsValidationIssue> issues = new ArrayList<>();

		Set<String> localRefs = new HashSet<>();
		localRefs.addAll(localRefsOf(root.path("goals")));
		localRefs.addAll(localRefsOf(root.path("targetAudiences")));
		localRefs.addAll(localRefsOf(root.path("contentRequirements")));
		localRefs.addAll(localRefsOf(root.path("functionalRequirements")));
		localRefs.addAll(localRefsOf(root.path("constraints")));

		checkAffects(root.path("unknowns"), "/unknowns", localRefs, issues);
		checkAffects(root.path("conflicts"), "/conflicts", localRefs, issues);
		checkLanguages(root.path("languages"), issues);
		checkCustomType(root.path("contentRequirements"), "/contentRequirements", issues);
		checkCustomType(root.path("functionalRequirements"), "/functionalRequirements", issues);
		checkAmbiguousUnknowns(root.path("unknowns"), issues);

		return issues.isEmpty()
				? WebsiteRequirementsValidationResult.passed()
				: new WebsiteRequirementsValidationResult(false, issues);
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

	private void checkAffects(
			JsonNode entries, String basePath, Set<String> localRefs, List<WebsiteRequirementsValidationIssue> issues) {
		if (!entries.isArray()) {
			return;
		}
		int entryIndex = 0;
		for (JsonNode entry : entries) {
			JsonNode affects = entry.path("affects");
			if (affects.isArray()) {
				int refIndex = 0;
				for (JsonNode ref : affects) {
					if (ref.isTextual() && !localRefs.contains(ref.asString())) {
						issues.add(new WebsiteRequirementsValidationIssue(
								"%s/%d/affects/%d".formatted(basePath, entryIndex, refIndex),
								"affects reference '" + ref.asString() + "' does not resolve to an existing requirement item"));
					}
					refIndex++;
				}
			}
			entryIndex++;
		}
	}

	private void checkLanguages(JsonNode languages, List<WebsiteRequirementsValidationIssue> issues) {
		if (!languages.isArray()) {
			return;
		}
		Set<String> seen = new HashSet<>();
		int index = 0;
		for (JsonNode language : languages) {
			String code = language.path("code").asString();
			String path = "/languages/%d/code".formatted(index);
			if (code != null) {
				try {
					new Locale.Builder().setLanguageTag(code).build();
				} catch (IllformedLocaleException e) {
					issues.add(new WebsiteRequirementsValidationIssue(path, "'" + code + "' is not a valid BCP 47 language tag"));
				}
				if (!seen.add(code.toLowerCase(Locale.ROOT))) {
					issues.add(new WebsiteRequirementsValidationIssue(path, "language code '" + code + "' is duplicated"));
				}
			}
			index++;
		}
	}

	private void checkCustomType(JsonNode requirements, String basePath, List<WebsiteRequirementsValidationIssue> issues) {
		if (!requirements.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode requirement : requirements) {
			String type = requirement.path("type").asString();
			boolean hasCustomType = requirement.hasNonNull("customType");
			String path = "%s/%d".formatted(basePath, index);
			if ("custom".equals(type) && !hasCustomType) {
				issues.add(new WebsiteRequirementsValidationIssue(path, "type 'custom' requires 'customType'"));
			} else if (!"custom".equals(type) && hasCustomType) {
				issues.add(new WebsiteRequirementsValidationIssue(path, "type '" + type + "' must not have 'customType'"));
			}
			index++;
		}
	}

	private void checkAmbiguousUnknowns(JsonNode unknowns, List<WebsiteRequirementsValidationIssue> issues) {
		if (!unknowns.isArray()) {
			return;
		}
		int index = 0;
		for (JsonNode unknown : unknowns) {
			if ("ambiguous".equals(unknown.path("kind").asString())) {
				JsonNode sourceRefs = unknown.path("sourceRefs");
				if (!sourceRefs.isArray() || sourceRefs.isEmpty()) {
					issues.add(new WebsiteRequirementsValidationIssue(
							"/unknowns/%d".formatted(index), "ambiguous unknown must contain supporting sourceRefs"));
				}
			}
			index++;
		}
	}
}
