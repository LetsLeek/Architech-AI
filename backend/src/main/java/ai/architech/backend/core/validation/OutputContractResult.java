package ai.architech.backend.core.validation;

import java.util.List;
import java.util.Map;

public record OutputContractResult(boolean valid, List<String> issues, Map<String, String> artifactContentByType) {

	public OutputContractResult {
		issues = List.copyOf(issues);
		artifactContentByType = Map.copyOf(artifactContentByType);
	}
}
