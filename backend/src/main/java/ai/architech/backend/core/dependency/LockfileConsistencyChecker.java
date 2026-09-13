package ai.architech.backend.core.dependency;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Detects manifest/lockfile drift for AIW-140's {@code lockfileRequired: true} /
 * {@code opportunisticUpgradeAllowed: false} requirements: every manifest-declared dependency
 * must resolve to a real, versioned entry in the lockfile's {@code packages} map (npm v3
 * lockfile format), or the install is not reproducible from manifest+lockfile alone. Reports
 * drift only - never regenerates or edits the lockfile itself.
 *
 * <p>Deliberately checks presence/resolved-version only, not full semver-range satisfaction -
 * this project has no semver-range-parsing dependency, and reproducibility (can {@code npm ci}
 * install from exactly what's committed) is what AIW-140 actually requires, not re-verifying
 * npm's own range resolution.
 */
@Component
public class LockfileConsistencyChecker {

	public List<String> findMismatches(Map<String, String> manifestDependencies, JsonNode lockfilePackages) {
		return manifestDependencies.keySet().stream()
				.filter(name -> !hasResolvedLockEntry(lockfilePackages, name))
				.map(name -> "'" + name + "' is declared in the manifest but has no resolved version in the lockfile")
				.toList();
	}

	private boolean hasResolvedLockEntry(JsonNode lockfilePackages, String packageName) {
		JsonNode entry = lockfilePackages.get("node_modules/" + packageName);
		return entry != null && entry.get("version") != null;
	}
}
