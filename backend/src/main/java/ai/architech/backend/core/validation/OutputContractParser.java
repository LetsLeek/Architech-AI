package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Output Contract Validation, layer 1 of the six-layer pipeline: does the raw candidate
 * text even have the shape a Requirements Agent's output is supposed to have? A single JSON
 * object, one property per required artifact type, nothing else - this envelope shape isn't
 * frozen by any contract, it's this platform's own implementation choice for V1.
 *
 * <p>Known gap: genuinely duplicate JSON object keys in the raw text (not just two
 * artifacts, but the very same key twice) aren't detected - by the time this reads a
 * parsed tree, the JSON parser has already silently kept only the last occurrence. Catching
 * that would need a streaming/raw-text check this V1 implementation doesn't attempt.
 */
@Component
public class OutputContractParser {

	private final ObjectMapper objectMapper;

	OutputContractParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public OutputContractResult parse(String rawOutput, Set<String> requiredArtifactTypes) {
		JsonNode root;
		try {
			root = objectMapper.readTree(rawOutput);
		} catch (RuntimeException e) {
			return new OutputContractResult(
					false, List.of("candidate output is not valid JSON: " + e.getMessage()), Map.of());
		}

		if (!root.isObject()) {
			return new OutputContractResult(false, List.of("candidate output must be a JSON object"), Map.of());
		}

		List<String> issues = new ArrayList<>();
		Map<String, String> artifacts = new LinkedHashMap<>();

		for (String requiredType : requiredArtifactTypes) {
			JsonNode artifactNode = root.get(requiredType);
			if (artifactNode == null) {
				issues.add("missing required artifact: " + requiredType);
			} else {
				artifacts.put(requiredType, artifactNode.toString());
			}
		}

		for (String property : root.propertyNames()) {
			if (!requiredArtifactTypes.contains(property)) {
				issues.add("unexpected output: " + property);
			}
		}

		return new OutputContractResult(issues.isEmpty(), issues, artifacts);
	}
}
