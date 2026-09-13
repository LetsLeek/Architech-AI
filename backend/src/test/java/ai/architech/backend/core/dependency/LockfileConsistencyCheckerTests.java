package ai.architech.backend.core.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LockfileConsistencyCheckerTests {

	private final LockfileConsistencyChecker checker = new LockfileConsistencyChecker();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void findsNoMismatchWhenEveryManifestDependencyIsResolvedInTheLockfile() {
		JsonNode lockfilePackages = objectMapper.readTree(
				"""
				{
				  "node_modules/react": {"version": "19.2.8"},
				  "node_modules/react-dom": {"version": "19.2.8"}
				}
				""");

		List<String> mismatches = checker.findMismatches(
				Map.of("react", "^19.2.8", "react-dom", "^19.2.8"), lockfilePackages);

		assertThat(mismatches).isEmpty();
	}

	@Test
	void reportsAManifestDependencyMissingFromTheLockfile() {
		JsonNode lockfilePackages = objectMapper.readTree(
				"""
				{
				  "node_modules/react": {"version": "19.2.8"}
				}
				""");

		List<String> mismatches = checker.findMismatches(Map.of("react", "^19.2.8", "left-pad", "^1.3.0"), lockfilePackages);

		assertThat(mismatches).hasSize(1);
		assertThat(mismatches.getFirst()).contains("left-pad");
	}

	@Test
	void reportsAManifestDependencyWithNoResolvedVersionInTheLockfile() {
		JsonNode lockfilePackages = objectMapper.readTree(
				"""
				{
				  "node_modules/react": {}
				}
				""");

		List<String> mismatches = checker.findMismatches(Map.of("react", "^19.2.8"), lockfilePackages);

		assertThat(mismatches).hasSize(1);
		assertThat(mismatches.getFirst()).contains("react");
	}
}
