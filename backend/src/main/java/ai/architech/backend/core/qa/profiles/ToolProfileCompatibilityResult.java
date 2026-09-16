package ai.architech.backend.core.qa.profiles;

import java.util.List;

public record ToolProfileCompatibilityResult(boolean passed, List<ToolProfileCompatibilityProblem> problems) {

	public static ToolProfileCompatibilityResult compatible() {
		return new ToolProfileCompatibilityResult(true, List.of());
	}

	public static ToolProfileCompatibilityResult incompatible(List<ToolProfileCompatibilityProblem> problems) {
		return new ToolProfileCompatibilityResult(false, List.copyOf(problems));
	}
}
