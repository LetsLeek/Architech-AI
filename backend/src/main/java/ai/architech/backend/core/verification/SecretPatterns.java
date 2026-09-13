package ai.architech.backend.core.verification;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The fixed, platform-owned set of content patterns {@link SecretScanGate} looks for. Not
 * configurable from anywhere a repository file could reach - matching AIW-158's own "Developer
 * cannot disable the scan through repository changes" requirement, since there is no mechanism
 * anywhere in this class that reads pattern/allowlist configuration from the scanned repository
 * itself.
 */
final class SecretPatterns {

	record NamedPattern(String name, Pattern pattern) {}

	static final List<NamedPattern> CONTENT_PATTERNS = List.of(
			new NamedPattern(
					"PRIVATE_KEY", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")),
			new NamedPattern("AWS_ACCESS_KEY", Pattern.compile("AKIA[0-9A-Z]{16}")),
			new NamedPattern("GITHUB_TOKEN", Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}")),
			new NamedPattern("SLACK_TOKEN", Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}")),
			new NamedPattern(
					"GENERIC_ASSIGNED_SECRET",
					Pattern.compile(
							"(?i)(secret|api[_-]?key|password|access[_-]?key|private[_-]?key)\\s*[:=]\\s*[\"']([A-Za-z0-9+/_\\-]{16,})[\"']")));

	/** File names that are prohibited secret-bearing files regardless of their content. */
	static boolean isProhibitedSecretFile(String relativePath) {
		String fileName = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
		if (fileName.equals(".env.example") || fileName.equals(".env.template") || fileName.equals(".env.sample")) {
			return false; // conventional, content-free templates - not a real secret-bearing file
		}
		return fileName.equals(".env") || fileName.endsWith(".env");
	}

	private SecretPatterns() {}
}
