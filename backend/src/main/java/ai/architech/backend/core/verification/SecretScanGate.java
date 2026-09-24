package ai.architech.backend.core.verification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

/**
 * The Runner-owned secret/credential scan over a Candidate's final frozen state (AIW-158).
 * Scans exactly the files {@code git} tracks in the given repository - never the working tree
 * directly, so gitignored build/dependency output (node_modules, dist, ...) is naturally
 * excluded without needing a maintained exclusion list, and an uncommitted local file can never
 * accidentally block or pass a Candidate that was never going to contain it anyway (only the
 * exact frozen, committed state that Runner Verification actually gates is scanned).
 *
 * <p>Detects and reports only - matching every other validator/classifier in this codebase, this
 * class never removes, replaces, or redacts a file; it returns structured {@link SecretFinding}s
 * for the caller (the authoritative Runner) to act on.
 */
@Component
public class SecretScanGate {

	public SecretScanResult scan(Path repositoryRoot) {
		List<SecretFinding> findings = new ArrayList<>();
		for (String relativePath : trackedFiles(repositoryRoot)) {
			if (SecretPatterns.isProhibitedSecretFile(relativePath)) {
				findings.add(new SecretFinding(relativePath, "PROHIBITED_SECRET_FILE", 0));
				continue; // a prohibited file itself is the finding - its content isn't separately pattern-scanned
			}
			findings.addAll(scanFileContent(repositoryRoot, relativePath));
		}
		return new SecretScanResult(findings);
	}

	private List<SecretFinding> scanFileContent(Path repositoryRoot, String relativePath) {
		Path file = repositoryRoot.resolve(relativePath);
		if (!Files.isRegularFile(file) || isLikelyBinary(file)) {
			return List.of();
		}

		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return List.of(); // unreadable (e.g. genuinely binary despite the extension check) - not a hard failure
		}

		List<SecretFinding> findings = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			for (SecretPatterns.NamedPattern namedPattern : SecretPatterns.CONTENT_PATTERNS) {
				Matcher matcher = namedPattern.pattern().matcher(line);
				if (matcher.find() && !SecretScanAllowlist.isAllowed(matcher.group())) {
					findings.add(new SecretFinding(relativePath, namedPattern.name(), i + 1));
				}
			}
		}
		return findings;
	}

	private boolean isLikelyBinary(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")
				|| name.endsWith(".ico") || name.endsWith(".woff") || name.endsWith(".woff2") || name.endsWith(".ttf");
	}

	private List<String> trackedFiles(Path repositoryRoot) {
		Process process;
		try {
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			// Fixed literal git subcommand, never caller-supplied text - same justification as
			// core.sandbox.ProcessRunner / core.repository.GitCommandRunner.
			process = new ProcessBuilder("git", "ls-files").directory(repositoryRoot.toFile()).start();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to list tracked files in " + repositoryRoot, e);
		}

		String output;
		try {
			output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			process.getErrorStream().readAllBytes();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Failed to read tracked file list for " + repositoryRoot, e);
		}

		return output.lines().filter(line -> !line.isBlank()).toList();
	}
}
