package ai.architech.backend.core.documentation.context;

import ai.architech.backend.core.verification.SecretPatterns;
import java.util.Map;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * Secret scan covering both sides of {@code DECISION_LOG.md} point 9's "Security ordering":
 * {@link #scan} runs pre-model, over an about-to-be-frozen {@code DocumentationContext}'s {@code
 * resolvedFacts}/{@code authorityCatalog} arrays (AIW-191, {@code DOC-SEC-003}/{@code DOC-SEC-005}
 * - "filtered before context freezing", "security checked pre-generation"); {@link #scanCandidate}
 * runs post-model, over a raw {@code semantic-documentation-candidate:v1} output, immediately after
 * schema parsing (AIW-195) and strictly before any model-backed evaluator sees the candidate
 * (AIW-197 - "post-generation parse/schema, immediately security-scan parsed/raw output before any
 * model-backed evaluation").
 *
 * <p>Reuses {@link SecretPatterns#CONTENT_PATTERNS} - the same platform-owned content patterns
 * {@code SecretScanGate}/{@code ToolExecutionDiagnostics}/{@code EvidenceRecord} already use -
 * rather than a second, drifting definition of what a secret looks like. Detects and blocks only
 * (mirrors {@code SecretScanGate}'s "detect and report" idiom, not {@code
 * ToolExecutionDiagnostics}'s "detect and replace" one): a match anywhere throws immediately, since
 * this content may end up verbatim in a customer-facing document, unlike an internal diagnostic
 * log where a {@code [REDACTED:...]} placeholder is an acceptable substitute.
 *
 * <p>{@link #scan} walks every textual leaf of each fact's {@code value} subtree (covers the {@code
 * STRING}, {@code STRING_LIST} and {@code FIELD_LIST} {@code safe-value} variants generically,
 * without special-casing {@code valueType}) and every {@code authorityCatalog} entry's {@code
 * authorityRef.locator.value}.
 *
 * <p>{@link #scanCandidate} walks the entire parsed candidate tree rather than selectively
 * extracting {@code documentation-claim-candidate.schema.json}'s {@code text} field: {@code text}
 * (1-1800 chars) is the only genuinely free-text field anywhere in {@code
 * semantic-documentation-candidate.schema.json}'s whole tree - every other field is an enum or a
 * {@code ^[A-Z][A-Z0-9_-]{1,79}$}-patterned identifier, so a whole-tree walk finds exactly the same
 * matches a {@code text}-only walk would, without needing to know the candidate's own nested shape
 * (a full walk is also then unaffected if a future schema revision adds another free-text field
 * elsewhere). This is also why "Audience-Disclosure" adds no separate mechanism here beyond this
 * scan: AIW-191 already minimized the context by audience before generation (dropping {@code
 * SENSITIVE_DOCUMENTABLE} facts and pruning their keys from {@code authorityCatalog} entries' own
 * {@code safeFactKeys}) and AIW-196 already verifies every claim's {@code authorityKeys} resolve to
 * a real, still-present catalog entry - so the model structurally cannot cite an authority it was
 * never shown. The only genuinely new, checkable surface at this stage is the model's own novel
 * prose potentially fabricating or restating something secret-pattern-shaped regardless of source.
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

	/**
	 * Post-generation counterpart to {@link #scan} (AIW-197) - walks the entire raw {@code
	 * semantic-documentation-candidate:v1} tree for any {@code SecretPatterns.CONTENT_PATTERNS}
	 * shape. Call this on the already schema-parsed candidate ({@code
	 * DocumentationCandidateStructureValidator}, AIW-195) and strictly before any model-backed
	 * evaluator (the {@code FACTUAL_CONSISTENCY} semantic validator, not built yet) ever sees it.
	 */
	public void scanCandidate(JsonNode candidate) {
		scanNode(candidate, "candidate");
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
