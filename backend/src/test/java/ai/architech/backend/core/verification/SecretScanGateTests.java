package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real local git repository, exactly like AIW-153's own tests - {@code git ls-files} needs real tracked state. */
class SecretScanGateTests {

	@TempDir
	Path root;

	private final SecretScanGate gate = new SecretScanGate();

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
	}

	@Test
	void aCleanRepositoryHasNoFindings() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isFalse();
		assertThat(result.findings()).isEmpty();
	}

	@Test
	void detectsAPrivateKey() throws IOException, InterruptedException {
		writeAndTrack("keys/id_rsa", "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----\n");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("PRIVATE_KEY"));
	}

	@Test
	void detectsAGitHubTokenAsAGitCredential() throws IOException, InterruptedException {
		writeAndTrack("scripts/deploy.sh", "curl -H \"Authorization: token ghp_1234567890abcdefghijklmnopqrstuvwxyz\"");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("GITHUB_TOKEN"));
	}

	@Test
	void detectsARealAwsAccessKeyAsACloudCredential() throws IOException, InterruptedException {
		writeAndTrack("src/config.ts", "const key = \"AKIAABCDEFGHIJKLMNOP\";");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("AWS_ACCESS_KEY"));
	}

	@Test
	void detectsAGenericApiKeyAssignment() throws IOException, InterruptedException {
		writeAndTrack("src/config.ts", "const apiKey = \"sk_live_abcdefghijklmnopqrstuvwx\";");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("GENERIC_ASSIGNED_SECRET"));
	}

	@Test
	void detectsATrackedDotEnvFileRegardlessOfContent() throws IOException, InterruptedException {
		writeAndTrack(".env", "SOME_VAR=whatever\n");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("PROHIBITED_SECRET_FILE"));
	}

	@Test
	void doesNotFlagAConventionalEnvExampleTemplate() throws IOException, InterruptedException {
		writeAndTrack(".env.example", "SOME_VAR=replace-me\n");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isFalse();
	}

	@Test
	void doesNotFlagAKnownSafeDocumentationPlaceholder() throws IOException, InterruptedException {
		// AWS's own widely-published "this is not a real key" example.
		writeAndTrack("README.md", "Example: const key = \"AKIAIOSFODNN7EXAMPLE\";");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isFalse();
	}

	@Test
	void findingsNeverContainTheMatchedSecretValueItself() throws IOException, InterruptedException {
		writeAndTrack("src/config.ts", "const key = \"AKIAABCDEFGHIJKLMNOP\";");

		SecretScanResult result = gate.scan(root);

		// SecretFinding's own fields (filePath, patternName, lineNumber) structurally cannot
		// carry the matched value - there is no field to put it in.
		assertThat(result.findings()).allSatisfy(f -> {
			assertThat(f.patternName()).doesNotContain("AKIAABCDEFGHIJKLMNOP");
			assertThat(f.filePath()).doesNotContain("AKIAABCDEFGHIJKLMNOP");
		});
	}

	@Test
	void aRepositoryLevelAllowlistAttemptHasNoEffectOnDetection() throws IOException, InterruptedException {
		// A "bypass attempt": committing a config file that pretends to allowlist the secret.
		// SecretPatterns/SecretScanAllowlist read nothing from the scanned repository at all, so
		// this file has zero effect on the outcome - the real secret is still detected.
		writeAndTrack(".secretscan-allowlist", "AKIAABCDEFGHIJKLMNOP\n");
		writeAndTrack("src/config.ts", "const key = \"AKIAABCDEFGHIJKLMNOP\";");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isTrue();
		assertThat(result.findings()).anySatisfy(f -> assertThat(f.patternName()).isEqualTo("AWS_ACCESS_KEY"));
	}

	@Test
	void anUntrackedFileIsNeverScanned() throws IOException {
		// Only committed/tracked state is a Candidate's actual frozen state (REPOSITORY-LIFECYCLE.md)
		// - an uncommitted local file was never going to be part of what Runner Verification gates.
		Files.writeString(root.resolve("untracked-secret.txt"), "AKIAABCDEFGHIJKLMNOP");

		SecretScanResult result = gate.scan(root);

		assertThat(result.blocked()).isFalse();
	}

	private void writeAndTrack(String relativePath, String content) throws IOException, InterruptedException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		run(root, "git", "add", relativePath);
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
