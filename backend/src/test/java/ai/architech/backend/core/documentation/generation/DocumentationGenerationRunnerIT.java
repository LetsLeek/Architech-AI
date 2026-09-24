package ai.architech.backend.core.documentation.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextAssembler;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.runner.RunnerResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-194's own single-attempt invocation wiring. No test here activates
 * the opt-in {@code real-ai} Spring profile ({@code SPRING_PROFILES_ACTIVE=real-ai} is never set)
 * - the platform's default {@code documentation-reasoning} model profile is {@code provider:
 * mock}, and the one place this class needs to inspect what was actually sent to a "model" mocks
 * {@link AiGateway} itself with Mockito (zero network, zero cost), the same "mock the boundary
 * you need to inspect, not the AI Gateway's own already-safe default" idiom {@code
 * DesignerAgentRunnerIT} uses for its own semantic reviewer.
 */
@SpringBootTest
@Transactional
class DocumentationGenerationRunnerIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private DocumentationProfileLoader profileLoader;

	@Autowired
	private DocumentationContextAssembler contextAssembler;

	@Autowired
	private DocumentationGenerationRunner generationRunner;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void invokesTheAgentOnceAgainstTheFrozenContextAndRecordsTheAttemptAsRunning() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		when(aiGateway.invoke(any())).thenReturn(new AiResponse(
				"mock", "mock-model", "{}", "correlation", null, null, null, null));

		RunnerResult result = generationRunner.generate(projectId, context);

		assertThat(result.execution().getAgentId()).isEqualTo("website-documentation-agent");
		assertThat(result.execution().getAgentVersion()).isEqualTo(1);
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
		assertThat(result.candidateOutput()).isEqualTo("{}");
		assertThat(agentExecutionRepository.findById(result.execution().getId())).isPresent();

		ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
		verify(aiGateway).invoke(requestCaptor.capture());
		AiRequest request = requestCaptor.getValue();
		assertThat(request.modelProfile()).isEqualTo("documentation-reasoning");
		String userMessage = request.messages().stream()
				.filter(m -> "user".equals(m.role()))
				.map(AiMessage::content)
				.findFirst()
				.orElseThrow();
		assertThat(userMessage).contains(context.getContentJson());
	}

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private AgentExecution seedSucceededExecution(UUID projectId, String agentId) {
		AgentExecution execution = new AgentExecution(projectId, agentId, 1);
		execution.start();
		execution.succeed();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private void seedCanonicalArtifact(UUID projectId, String type) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = seedSucceededExecution(projectId, type + "-agent");
		artifactVersionRepository.saveAndFlush(
				new ArtifactVersion(artifact.getId(), 1, execution.getId(), "{\"content\":\"fixture\"}"));
	}

	private WebsiteImplementationCandidate seedCandidateWithDesign(UUID projectId, String proposalLocalRef) {
		Artifact designArtifact = artifactRepository
				.findByProjectIdAndType(projectId, "design-proposal-set")
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set")));
		int nextVersion = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(designArtifact.getId()).size() + 1;
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		ArtifactVersion designVersion = artifactVersionRepository.saveAndFlush(new ArtifactVersion(
				designArtifact.getId(),
				nextVersion,
				designExecution.getId(),
				"{\"proposals\":[{\"localRef\":\"" + proposalLocalRef + "\"}]}"));

		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private QaResult seedQaResult(WebsiteImplementationCandidate candidate, String gateOutcome) {
		AgentExecution qaAgentExecution = seedSucceededExecution(candidate.getProjectId(), "website-qa-agent");
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", "preview-42", "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot =
				qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{\"qaExecutionRef\": \"fixture\"}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(),
				qaExecution.getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				inputSnapshot.getId(),
				"COMPLETE",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				gateOutcome,
				"[]",
				evidenceManifest.getId(),
				"{}"));
	}
}
