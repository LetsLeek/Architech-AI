package ai.architech.backend.core.qa.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.repository.ScaffoldMaterializer;
import ai.architech.backend.core.sandbox.ProjectExecutionCapability;
import ai.architech.backend.core.sandbox.ProjectExecutionTask;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real end-to-end proof of AIW-172's own representative checks against AIW-138's actual
 * Development Base scaffold - real {@code npm ci}, a real {@code npm run dev} server, and a real
 * headless Chromium via {@link QaPlaywrightDriver}, the same infrastructure {@code
 * AuthoritativeRunnerVerifierIT} already uses for Developer's own Runner Verification.
 *
 * <p>The scaffold and dev server are started once for the whole class ({@code @BeforeAll}, not
 * per test) - {@code npm ci} is real work, and nothing about the running server is mutated by any
 * individual check here, so there is no reason to pay for it once per test method.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class QaCheckRunnerIT {

	private static final String BASE_URL = "http://127.0.0.1:5173";

	@TempDir
	static Path root;

	@Autowired
	private ScaffoldMaterializer scaffoldMaterializer;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private QaCheckRunner checkRunner;

	private Process devServer;

	@BeforeAll
	void startRealLocalRuntime() throws IOException, InterruptedException {
		scaffoldMaterializer.materializeInto(root);
		Workspace workspace = new Workspace(root);
		new ProjectExecutionCapability(workspace).run(ProjectExecutionTask.INSTALL);
		devServer = new ProjectExecutionCapability(workspace).startLocalRuntime();
		boolean ready = waitUntilReady(BASE_URL, Duration.ofSeconds(30));
		if (!ready) {
			throw new IllegalStateException("local runtime did not become ready in time");
		}
	}

	@AfterAll
	void stopLocalRuntime() {
		if (devServer != null) {
			devServer.descendants().forEach(ProcessHandle::destroyForcibly);
			devServer.destroyForcibly();
		}
	}

	@Test
	void unknownCheckCodesAreRejected() {
		assertThatThrownBy(() -> checkRunner.run("MADE_UP_CHECK_CODE", context()))
				.isInstanceOf(UnknownQaCheckCodeException.class);
	}

	@Test
	void aCheckThatIsRegisteredButNotYetWiredReportsError() {
		QaCheckResult result = checkRunner.run("REQUIRED_ASSET_PRESENCE", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.ERROR);
		assertThat(result.summary()).contains("not yet implemented");
	}

	@Test
	void candidatePreconditionChecksPassAgainstAWellFormedVerifiedCandidate() {
		QaCheckExecutionContext context = context();

		assertThat(checkRunner.run("CANDIDATE_SOURCE_IDENTITY", context).outcome()).isEqualTo(QaCheckOutcome.PASS);
		assertThat(checkRunner.run("CANDIDATE_VERIFICATION_PROVENANCE", context).outcome()).isEqualTo(QaCheckOutcome.PASS);
		assertThat(checkRunner.run("AUTHORITY_REFERENCE_INTEGRITY", context).outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void candidatePreconditionChecksFailForAnUnknownCandidate() {
		QaCheckExecutionContext context = contextWithInput(inputFor(UUID.randomUUID()));

		QaCheckResult result = checkRunner.run("CANDIDATE_SOURCE_IDENTITY", context);

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void executionSurfaceCandidateBindingPassesWhenClaimedAndObservedMatch() {
		QaCheckResult result = checkRunner.run("EXECUTION_SURFACE_CANDIDATE_BINDING", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void executionSurfaceCandidateBindingFailsOnDrift() {
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate();
		QaCheckExecutionContext context = new QaCheckExecutionContext(
				candidate.getProjectId(), inputFor(candidate.getId()), "preview-42", "preview-99",
				BASE_URL, "/", 1440, 900, null, null, List.of());

		QaCheckResult result = checkRunner.run("EXECUTION_SURFACE_CANDIDATE_BINDING", context);

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	@Test
	void canonicalRouteReachabilityPassesAgainstTheRealRunningScaffold() {
		QaCheckResult result = checkRunner.run("CANONICAL_ROUTE_REACHABILITY", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void fatalBrowserErrorScanPassesAgainstAnUnmodifiedScaffold() {
		QaCheckResult result = checkRunner.run("FATAL_BROWSER_ERROR_SCAN", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void brokenAssetScanPassesAgainstAnUnmodifiedScaffold() {
		QaCheckResult result = checkRunner.run("BROKEN_ASSET_SCAN", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void horizontalOverflowScanPassesAtAStandardDesktopViewport() {
		QaCheckResult result = checkRunner.run("HORIZONTAL_OVERFLOW_SCAN", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void accessibilityTreeCaptureSucceedsAgainstTheRealRunningScaffold() {
		QaCheckResult result = checkRunner.run("A11Y_ACCESSIBILITY_TREE_CAPTURE", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void documentTitlePresentPassesAgainstTheRealRunningScaffold() {
		QaCheckResult result = checkRunner.run("DOCUMENT_TITLE_PRESENT", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void initialRenderCompletionPassesAgainstTheRealRunningScaffold() {
		QaCheckResult result = checkRunner.run("INITIAL_RENDER_COMPLETION", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.PASS);
	}

	@Test
	void primaryLocalInteractionSmokeErrorsWhenTheSelectorDoesNotResolve() {
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate();
		QaCheckExecutionContext context = new QaCheckExecutionContext(
				candidate.getProjectId(), inputFor(candidate.getId()), "preview-42", "preview-42",
				BASE_URL, "/", 1440, 900, null, "#this-selector-does-not-exist-anywhere", List.of());

		QaCheckResult result = checkRunner.run("PRIMARY_LOCAL_INTERACTION_SMOKE", context);

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.ERROR);
	}

	@Test
	void requiredTextPresenceIsNotApplicableWhenNoTextWasSpecified() {
		QaCheckResult result = checkRunner.run("REQUIRED_TEXT_PRESENCE", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.NOT_APPLICABLE);
	}

	@Test
	void requiredLocaleEntrypointsIsNotApplicableWhenNoLocalesApply() {
		QaCheckResult result = checkRunner.run("REQUIRED_LOCALE_ENTRYPOINTS", context());

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.NOT_APPLICABLE);
	}

	@Test
	void requiredLocaleEntrypointsFailsWhenAnEntrypointDoesNotRespond() {
		// AIW-138's scaffold is a Vite SPA dev server: it falls back to index.html (200) for any
		// path, matched file or not - so a genuine failure needs an entrypoint whose *base URL*
		// nothing is actually listening on, not just an unmatched path on the real server.
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate();
		QaCheckExecutionContext context = new QaCheckExecutionContext(
				candidate.getProjectId(), inputFor(candidate.getId()), "preview-42", "preview-42",
				"http://127.0.0.1:1", "/", 1440, 900, null, null, List.of("/"));

		QaCheckResult result = checkRunner.run("REQUIRED_LOCALE_ENTRYPOINTS", context);

		assertThat(result.outcome()).isEqualTo(QaCheckOutcome.FAIL);
	}

	private QaCheckExecutionContext context() {
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate();
		return contextForCandidate(candidate);
	}

	private QaCheckExecutionContext contextWithInput(String input) {
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate();
		return new QaCheckExecutionContext(
				candidate.getProjectId(), input, "preview-42", "preview-42", BASE_URL, "/", 1440, 900, null, null, List.of());
	}

	private QaCheckExecutionContext contextForCandidate(WebsiteImplementationCandidate candidate) {
		return new QaCheckExecutionContext(
				candidate.getProjectId(), inputFor(candidate.getId()), "preview-42", "preview-42",
				BASE_URL, "/", 1440, 900, null, null, List.of());
	}

	private WebsiteImplementationCandidate seedVerifiedCandidate() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", "prop-a", "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), candidate.getRepositoryStateRef(), VerificationOutcome.PASS));
		return candidate;
	}

	private String inputFor(UUID candidateId) {
		return """
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-fixture",
				  "target": {"candidateRef": "%s"},
				  "productAuthority": {
				    "customerProfileRef": "customer-profile-fixture",
				    "websiteRequirementsRef": "requirements-fixture",
				    "sourceDesignRef": "design-v1:prop-a",
				    "runtimeProfileRef": "runtime-v1",
				    "integrationContractRefs": []
				  },
				  "qaAuthority": {
				    "qaProfileRef": "website-qa-full-release@1.0.0",
				    "ruleSetRef": "website-qa-rules@1.0.0",
				    "skillSetRef": "website-qa-skills@1.0.0",
				    "validatorSetRef": "website-qa-validator-set@1.0.0",
				    "findingTaxonomyRef": "website-qa-finding-taxonomy@1.0.0",
				    "checkRegistryRef": "website-qa-check-registry@1.0.0"
				  },
				  "executionContext": {
				    "toolCapabilityProfileRef": "website-qa-tools@1.0.0"
				  },
				  "provenance": {
				    "inputSnapshotRef": "input-snapshot-fixture",
				    "qaAgentVersion": "1.0.0"
				  }
				}
				"""
				.formatted(candidateId);
	}

	private boolean waitUntilReady(String url, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			try {
				HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
				connection.setConnectTimeout(1000);
				connection.setReadTimeout(1000);
				int status = connection.getResponseCode();
				connection.disconnect();
				if (status >= 200 && status < 500) {
					return true;
				}
			} catch (IOException ignored) {
				// not ready yet - keep polling until the deadline
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}
}
