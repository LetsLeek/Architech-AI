package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.repository.ScaffoldMaterializer;
import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.FileSystemUtils;

/**
 * Real end-to-end proof against AIW-138's actual Development Base scaffold: real {@code npm ci},
 * real {@code tsc -b}/{@code oxlint}/{@code vitest run}/{@code vite build}, real local git.
 *
 * <p>Covers exactly the scenarios this V1 slice's own gates (1, 2, 3-6, 13, 14) can produce: a
 * real source (typecheck) failure, a real secret finding, a broken repository state being caught
 * rather than silently trusted, and a full successful run. This class does NOT cover
 * "unauthorized network request", "route/nav failure" or "responsive overflow" - {@link
 * AuthoritativeRunnerVerifier}'s own javadoc explains those gates (7-12) are out of scope for
 * this slice pending AIW-157.
 *
 * <p>Genuine {@code ERROR} classification (a process failing to even start, or timing out) is
 * not separately re-proven here: {@link AuthoritativeRunnerVerifier}'s gate wrapping is a direct
 * catch of exactly the exception types {@code core.sandbox.ProcessRunnerTests} already proves
 * {@code ProcessRunner} throws for those cases - reasoning about a straightforward catch clause
 * does not need a second, contrived real-infrastructure-breakage IT to be trustworthy.
 */
@SpringBootTest
class AuthoritativeRunnerVerifierIT {

	@TempDir
	Path root;

	@Autowired
	private ScaffoldMaterializer scaffoldMaterializer;

	@Autowired
	private HandoffFreezeGate handoffFreezeGate;

	@Autowired
	private AuthoritativeRunnerVerifier verifier;

	@BeforeEach
	void materializeScaffoldAndInitGit() throws IOException, InterruptedException {
		scaffoldMaterializer.materializeInto(root);
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
		run(root, "git", "add", "-A");
	}

	@Test
	void aCleanUnmodifiedScaffoldPassesEveryImplementedGate() {
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot);

		assertThat(result.passed()).as(result.gates().toString()).isTrue();
		assertThat(result.gates()).hasSize(8);
		assertThat(result.gates()).allSatisfy(gate -> assertThat(gate.outcome()).isEqualTo(VerificationOutcome.PASS));
	}

	@Test
	void aRealTypeErrorFailsTheTypecheckGateAndStopsBeforeLaterGates() throws IOException, InterruptedException {
		Path appFile = root.resolve("src/App.tsx");
		Files.writeString(appFile, Files.readString(appFile) + "\nconst brokenTypeError: number = \"not a number\";\n");
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot);

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("typecheck");
			assertThat(gate.outcome()).isEqualTo(VerificationOutcome.FAIL);
		});
	}

	@Test
	void aPlantedSecretFailsTheSecretScanGate() throws IOException, InterruptedException {
		Path appFile = root.resolve("src/App.tsx");
		Files.writeString(
				appFile, Files.readString(appFile) + "\nexport const leakedKey = \"AKIAABCDEFGHIJKLMNOP\";\n");
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot);

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("secret-credential-scan");
			assertThat(gate.detail()).contains("AWS_ACCESS_KEY");
		});
	}

	@Test
	void aRepositoryThatChangedSinceFreezingIsCaughtByFinalSourceStateIntegrityRatherThanSilentlyTrusted() throws IOException {
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		// The tracked git repository itself disappearing between freezing and verification means
		// the snapshot recomputed for gate 14 can never again match what was frozen - proving this
		// gate does not just trust that nothing happened, it actually recomputes and compares.
		FileSystemUtils.deleteRecursively(root.resolve(".git"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot);

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("final-source-state-integrity");
			assertThat(gate.outcome()).isEqualTo(VerificationOutcome.FAIL);
		});
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
