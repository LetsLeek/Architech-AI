package ai.architech.backend.core.documentation.context;

import ai.architech.backend.core.verification.SecretPatterns;
import java.util.Map;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * Pre-model secret scan over an about-to-be-frozen {@code DocumentationContext}'s {@code
 * resolvedFacts}/{@code authorityCatalog} arrays (AIW-191, {@code DOC-SEC-003}/{@code DOC-SEC-005}
 * - "filtered before context freezing", "security checked pre-generation").
 *
 * <p>Reuses {@link SecretPatterns#CONTENT_PATTERNS} - the same platform-owned content patterns
 * {@code SecretScanGate}/{@code ToolExecutionDiagnostics}/{@code EvidenceRecord} already use -
 * rather than a second, drifting definition of what a secret looks like. Detects and blocks only
 * (mirrors {@code SecretScanGate}'s "detect and report" idiom, not {@code
 * ToolExecutionDiagnostics}'s "detect and replace" one): a match anywhere throws immediately, since
 * this content may end up verbatim in a customer-facing document, unlike an internal diagnostic
 * log where a {@code [REDACTED:...]} placeholder is an acceptable substitute.
 *
 * <p>Walks every textual leaf of each fact's {@code value} subtree (covers the {@code STRING},
 * {@code STRING_LIST} and {@code FIELD_LIST} {@code safe-value} variants generically, without
 * special-casing {@code valueType}) and every {@code authorityCatalog} entry's {@code
 * authorityRef.locator.value}.
 */
@Component
public class DocumentationSecretScanner {

	public void scan(ArrayNode resolvedFacts, ArrayNode authorityCatalog) {
		for (JsonNode fact : resolvedFacts) {
			String factKey = fact.path("factKey").asString("<unknown fact>");
			JsonNode value = fact.get("value");
			if (value != null) {
				scanNode(value, "resolvedFacts entry '" + factKey + "'");
			}
		}
		for (JsonNode entry : authorityCatalog) {
			String key = entry.path("key").asString("<unknown catalog entry>");
			JsonNode locatorValue = entry.path("authorityRef").path("locator").get("value");
			if (locatorValue != null) {
				scanNode(locatorValue, "authorityCatalog entry '" + key + "' locator");
			}
		}
	}

	private void scanNode(JsonNode node, String location) {
		if (node.isTextual()) {
			checkText(node.asString(), location);
		} else if (node.isArray()) {
			for (JsonNode element : node) {
				scanNode(element, location);
			}
		} else if (node.isObject()) {
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				scanNode(property.getValue(), location);
			}
		}
	}

	private void checkText(String text, String location) {
		for (SecretPatterns.NamedPattern namedPattern : SecretPatterns.CONTENT_PATTERNS) {
			Matcher matcher = namedPattern.pattern().matcher(text);
			if (matcher.find()) {
				throw new DocumentationSecretLeakageDetectedException(namedPattern.name(), location);
			}
		}
	}
}
