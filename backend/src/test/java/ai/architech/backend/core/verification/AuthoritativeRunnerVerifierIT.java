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
 * real {@code tsc -b}/{@code oxlint}/{@code vitest run}/{@code vite build}, real local git, and
 * (gates 7-12) a real headless Chromium via {@link LocalRuntimeSmokeRunner} against the real
 * {@code npm run dev} server.
 *
 * <p>Genuine {@code ERROR} classification for gates 1-6/13/14 (a process failing to even start,
 * or timing out) is not separately re-proven here: {@link AuthoritativeRunnerVerifier}'s gate
 * wrapping is a direct catch of exactly the exception types {@code core.sandbox.ProcessRunnerTests}
 * already proves {@code ProcessRunner} throws for those cases.
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
	void aCleanUnmodifiedScaffoldPassesAllFourteenGates() {
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.passed()).as(result.gates().toString()).isTrue();
		assertThat(result.gates()).hasSize(14);
		assertThat(result.gates()).allSatisfy(gate -> assertThat(gate.outcome()).isEqualTo(VerificationOutcome.PASS));
		// Drift guard: AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES (AIW-155's own persistence
		// layer relies on it) must name exactly these 14 gates, in exactly this order.
		assertThat(result.gates()).extracting(GateResult::gateName).isEqualTo(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES);
	}

	@Test
	void aRealTypeErrorFailsTheTypecheckGateAndStopsBeforeLaterGates() throws IOException, InterruptedException {
		Path appFile = root.resolve("src/App.tsx");
		Files.writeString(appFile, Files.readString(appFile) + "\nconst brokenTypeError: number = \"not a number\";\n");
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("typecheck");
			assertThat(gate.outcome()).isEqualTo(VerificationOutcome.FAIL);
		});
	}

	@Test
	void anUndeclaredGoogleFontsRequestFailsBrowserRuntimeIntegrity() throws IOException, InterruptedException {
		Path indexHtml = root.resolve("index.html");
		Files.writeString(
				indexHtml,
				Files.readString(indexHtml)
						.replace(
								"</head>",
								"<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Roboto\"></head>"));
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).as(result.gates().toString()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("browser-runtime-integrity");
			assertThat(gate.detail()).contains("fonts.googleapis.com");
		});
	}

	@Test
	void anAuthorizedExternalTargetPassesDespiteBeingExternal() throws IOException, InterruptedException {
		Path indexHtml = root.resolve("index.html");
		Files.writeString(
				indexHtml,
				Files.readString(indexHtml)
						.replace(
								"</head>",
								"<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Roboto\"></head>"));
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		List<AuthorizedExternalTarget> authorized =
				List.of(new AuthorizedExternalTarget("fonts.googleapis.com", "stylesheet"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, authorized);

		assertThat(result.passed()).as(result.gates().toString()).isTrue();
	}

	@Test
	void aFatalConsoleErrorFailsBrowserRuntimeIntegrity() throws IOException, InterruptedException {
		Path homePage = root.resolve("src/pages/HomePage.tsx");
		Files.writeString(
				homePage,
				Files.readString(homePage)
						.replace(
								"return <main>Website Development Base</main>",
								"console.error('simulated fatal runtime error')\n  return <main>Website Development Base</main>"));
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("browser-runtime-integrity");
			assertThat(gate.detail()).contains("simulated fatal runtime error");
		});
	}

	@Test
	void aWideElementOverflowsOnlyTheNarrowViewportAndFailsThatResponsiveGate() throws IOException, InterruptedException {
		Path homePage = root.resolve("src/pages/HomePage.tsx");
		Files.writeString(
				homePage,
				Files.readString(homePage)
						.replace(
								"return <main>Website Development Base</main>",
								"return <main style={{ width: '500px' }}>Website Development Base</main>"));
		run(root, "git", "add", "-A");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("narrow-responsive-sanity");
			assertThat(gate.outcome()).isEqualTo(VerificationOutcome.FAIL);
		});
		assertThat(result.gates()).filteredOn(gate -> gate.gateName().contains("wide-responsive-sanity"))
				.singleElement()
				.satisfies(gate -> assertThat(gate.outcome()).isEqualTo(VerificationOutcome.PASS));
	}

	@Test
	void aRepositoryThatChangedSinceFreezingIsCaughtByFinalSourceStateIntegrityRatherThanSilentlyTrusted() throws IOException {
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		// The tracked git repository itself disappearing between freezing and verification means
		// the snapshot recomputed for gate 14 can never again match what was frozen - proving this
		// gate does not just trust that nothing happened, it actually recomputes and compares.
		FileSystemUtils.deleteRecursively(root.resolve(".git"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

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
