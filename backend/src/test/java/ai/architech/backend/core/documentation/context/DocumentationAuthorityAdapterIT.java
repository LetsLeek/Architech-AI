package ai.architech.backend.core.documentation.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-189's own adapter: builds {@code authoritySnapshot}/{@code qaState}
 * from real persisted rows, exactly matching {@code documentation-context.schema.json}'s shape.
 */
@SpringBootTest
@Transactional
class DocumentationAuthorityAdapterIT {

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
	private DocumentationAuthorityAdapter adapter;

	@Test
	void buildsACompleteAuthoritySnapshotAndQaStateFromRealPersistedRows() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "customer-profile");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		DocumentationAuthoritySnapshot snapshot = adapter.buildAuthoritySnapshot(projectId, candidate, qaResult);

		assertThat(snapshot.customerProfileRef()).isPresent();
		assertThat(snapshot.customerProfileRef().get().artifactType()).isEqualTo("CUSTOMER_PROFILE");
		assertThat(snapshot.websiteRequirementsRef().artifactType()).isEqualTo("WEBSITE_REQUIREMENTS");
		assertThat(snapshot.websiteRequirementsRef().artifactVersionRef()).isNotBlank();
		assertThat(snapshot.selectedSourceDesignRef().artifactType()).isEqualTo("SELECTED_SOURCE_DESIGN");
		assertThat(snapshot.selectedSourceDesignRef().artifactVersionRef()).isEqualTo(candidate.getSourceDesignRef());
		assertThat(snapshot.implementationCandidateRef().artifactType()).isEqualTo("WEBSITE_IMPLEMENTATION_CANDIDATE");
		assertThat(snapshot.implementationCandidateRef().artifactVersionRef()).isEqualTo(candidate.getId().toString());
		assertThat(snapshot.qaResultRef().artifactType()).isEqualTo("QA_RESULT");
		assertThat(snapshot.qaResultRef().artifactVersionRef()).isEqualTo(qaResult.getId().toString());
		assertThat(snapshot.selectionDecisionRef()).isEmpty();
		assertThat(snapshot.approvalRecordRef()).isEmpty();
		assertThat(snapshot.deploymentRecordRef()).isEmpty();

		DocumentationQaState qaState = adapter.buildQaState(candidate, qaResult);
		assertThat(qaState.qaResultRef()).isEqualTo(qaResult.getId().toString());
		assertThat(qaState.candidateRef()).isEqualTo(candidate.getId().toString());
		assertThat(qaState.qaProfile()).isEqualTo("FULL_RELEASE");
		assertThat(qaState.gate()).isEqualTo("PASS");
	}

	@Test
	void leavesCustomerProfileRefAbsentWhenNoCustomerProfileArtifactExistsForTheProject() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		DocumentationAuthoritySnapshot snapshot = adapter.buildAuthoritySnapshot(projectId, candidate, qaResult);

		assertThat(snapshot.customerProfileRef()).isEmpty();
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
