package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-189's own preflight validator against a realistic Candidate/QaResult
 * graph, per {@code validators/context/PREFLIGHT.md} steps 1-2: lineage, project isolation,
 * profile-required-root presence, and the per-profile QA profile/gate precondition (Customer only
 * accepts {@code PASS}, Technical accepts {@code PASS} or {@code HOLD}).
 */
@SpringBootTest
@Transactional
class DocumentationContextPreflightValidatorIT {

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
	private DocumentationContextPreflightValidator validator;

	@Test
	void acceptsATechnicalHandoverScenarioWithAHoldGate() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "website-qa-full-release@1.0.0", "HOLD");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResult.getId(), technicalHandover);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsACustomerHandoverScenarioWithAHoldGate() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "customer-profile");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "website-qa-full-release@1.0.0", "HOLD");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResult.getId(), customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("qaState/gate"));
	}

	@Test
	void rejectsACandidateBelongingToAnotherProject() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		UUID otherProjectId = seedProject();
		seedCanonicalArtifact(otherProjectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(otherProjectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "website-qa-full-release@1.0.0", "PASS");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResult.getId(), technicalHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("candidateRef"));
	}

	@Test
	void rejectsAQaResultThatWasNotRunAgainstExactlyTheGivenCandidate() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		WebsiteImplementationCandidate otherCandidate = seedCandidateWithDesign(projectId, "prop-b");
		QaResult qaResultForOtherCandidate = seedQaResult(otherCandidate, "website-qa-full-release@1.0.0", "PASS");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResultForOtherCandidate.getId(), technicalHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("qaResultRef"));
	}

	@Test
	void rejectsAMissingRequiredRootWhenWebsiteRequirementsHasNoCanonicalArtifact() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "website-qa-full-release@1.0.0", "PASS");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResult.getId(), technicalHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("requiredRoots"));
	}

	@Test
	void rejectsWhenTheSourceDesignProposalLocalRefDoesNotExistInTheDesignProposalSet() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithMismatchedProposalRef(projectId);
		QaResult qaResult = seedQaResult(candidate, "website-qa-full-release@1.0.0", "PASS");

		PreExecutionValidationResult result =
				validator.validate(projectId, candidate.getId(), qaResult.getId(), technicalHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.anySatisfy(issue -> assertThat(issue.path()).isEqualTo("authoritySnapshot/selectedSourceDesignRef"));
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

	private WebsiteImplementationCandidate seedCandidateWithMismatchedProposalRef(UUID projectId) {
		Artifact designArtifact = artifactRepository
				.findByProjectIdAndType(projectId, "design-proposal-set")
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set")));
		int nextVersion = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(designArtifact.getId()).size() + 1;
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		ArtifactVersion designVersion = artifactVersionRepository.saveAndFlush(
				new ArtifactVersion(designArtifact.getId(), nextVersion, designExecution.getId(), "{\"proposals\":[{\"localRef\":\"prop-real\"}]}"));

		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				"prop-does-not-exist",
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private QaResult seedQaResult(WebsiteImplementationCandidate candidate, String qaProfileRef, String gateOutcome) {
		AgentExecution qaAgentExecution = seedSucceededExecution(candidate.getProjectId(), "website-qa-agent");
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), qaProfileRef, "preview-42", "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot =
				qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{\"qaExecutionRef\": \"fixture\"}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(),
				qaExecution.getId(),
				candidate.getId(),
				qaProfileRef,
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
