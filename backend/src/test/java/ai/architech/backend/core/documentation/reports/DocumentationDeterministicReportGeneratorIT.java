package ai.architech.backend.core.documentation.reports;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.context.DocumentationAuthorityAdapter;
import ai.architech.backend.core.documentation.context.DocumentationAuthoritySnapshot;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextAssembler;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import ai.architech.backend.core.integration.IntegrationContract;
import ai.architech.backend.core.integration.IntegrationContractRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.validation.DocumentationSchemaRegistry;
import ai.architech.backend.core.validation.SchemaValidationResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real-Postgres proof of AIW-193's {@link DocumentationDeterministicReportGenerator}: every
 * generated report's full envelope+payload is validated directly against the real {@code
 * deterministic-report.schema.json} via {@link DocumentationSchemaRegistry}.
 */
@SpringBootTest
@Transactional
class DocumentationDeterministicReportGeneratorIT {

	private static final String REPORT_SCHEMA_URN = "urn:aiw:schema:documentation:deterministic-report:v1";
	private static final String RUNTIME_PROFILE_REF = "website-react-typescript-vite-client-v1";

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
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private IntegrationContractRepository integrationContractRepository;

	@Autowired
	private DocumentationProfileLoader profileLoader;

	@Autowired
	private DocumentationAuthorityAdapter authorityAdapter;

	@Autowired
	private DocumentationContextAssembler contextAssembler;

	@Autowired
	private DocumentationDeterministicReportGenerator generator;

	@Autowired
	private DocumentationSchemaRegistry schemaRegistry;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void customerHandoverProfileRequiresNoReportsAtAll() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "de-AT", List.of());
		DocumentationAuthoritySnapshot authoritySnapshot = authorityAdapter.buildAuthoritySnapshot(projectId, candidate, qaResult);

		List<ObjectNode> reports = generator.generate(context, candidate, qaResult, authoritySnapshot, profile);

		assertThat(reports).isEmpty();
	}

	@Test
	void technicalHandoverProfileWithFullDataProducesFiveSchemaValidReports() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithBinding(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		CandidateFinding finding = seedFindingWithRequirement(qaResult, candidate, "REQ-CONTACT-FORM");
		seedPolicyEvaluation(qaResult, candidate, finding, "ALLOW");
		IntegrationContract contract = seedIntegrationContract(projectId, "CONTRACT-EMAIL", true);
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());
		DocumentationAuthoritySnapshot authoritySnapshot = authorityAdapter.buildAuthoritySnapshot(projectId, candidate, qaResult);

		List<ObjectNode> reports = generator.generate(context, candidate, qaResult, authoritySnapshot, profile);

		assertThat(reports).hasSize(5);
		Map<String, ObjectNode> byType = reports.stream()
				.collect(java.util.stream.Collectors.toMap(r -> r.path("reportType").asString(), r -> r));
		assertThat(byType.keySet()).containsExactlyInAnyOrder(
				"ARTIFACT_VERSION_MANIFEST", "IMPLEMENTATION_MANIFEST", "FUNCTIONAL_BINDING_REPORT",
				"INTEGRATION_REFERENCE_REPORT", "QA_FINDING_REGISTER");

		for (ObjectNode report : reports) {
			String json = objectMapper.writeValueAsString(report);
			SchemaValidationResult result = schemaRegistry.validate(REPORT_SCHEMA_URN, json);
			assertThat(result.valid()).as(report.path("reportType").asString() + ": " + result.issues()).isTrue();
			assertThat(report.path("contextRef").asString()).isEqualTo(context.getId().toString());
			assertThat(report.path("deliveryDisposition").asString()).isEqualTo("DEVELOPER_VISIBLE");
		}

		ObjectNode implementationManifest = byType.get("IMPLEMENTATION_MANIFEST");
		assertThat(implementationManifest.path("payload").path("routes").size()).isZero();
		assertThat(implementationManifest.path("payload").path("runtime").path("framework").asString()).isEqualTo("React");

		ObjectNode functionalBindingReport = byType.get("FUNCTIONAL_BINDING_REPORT");
		JsonNode bindings = functionalBindingReport.path("payload").path("bindings");
		assertThat(bindings.size()).isEqualTo(1);
		assertThat(bindings.get(0).path("bindingState").asString()).isEqualTo("IMPLEMENTED_BOUND");
		assertThat(bindings.get(0).path("integrationContractRef").asString()).isEqualTo(contract.getContractRef());

		ObjectNode integrationReferenceReport = byType.get("INTEGRATION_REFERENCE_REPORT");
		JsonNode integrations = integrationReferenceReport.path("payload").path("integrations");
		assertThat(integrations.size()).isEqualTo(1);
		assertThat(integrations.get(0).path("credentialDependency").asString()).isEqualTo("SECRET_MANAGED");

		ObjectNode qaFindingRegister = byType.get("QA_FINDING_REGISTER");
		JsonNode findings = qaFindingRegister.path("payload").path("findings");
		assertThat(findings.size()).isEqualTo(1);
		assertThat(findings.get(0).path("qaDisposition").asString()).isEqualTo("ALLOW");
		assertThat(findings.get(0).path("currentAssessment").asString()).isEqualTo("CURRENT");
		List<String> affectedRefs = stream(findings.get(0).path("affectedRequirementRefs")).map(JsonNode::asString).toList();
		assertThat(affectedRefs).containsExactly("REQ-CONTACT-FORM");
	}

	@Test
	void technicalHandoverProfileWithNoBindingsOrFindingsStillProducesFiveEmptyValidReports() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());
		DocumentationAuthoritySnapshot authoritySnapshot = authorityAdapter.buildAuthoritySnapshot(projectId, candidate, qaResult);

		List<ObjectNode> reports = generator.generate(context, candidate, qaResult, authoritySnapshot, profile);

		assertThat(reports).hasSize(5);
		for (ObjectNode report : reports) {
			String json = objectMapper.writeValueAsString(report);
			SchemaValidationResult result = schemaRegistry.validate(REPORT_SCHEMA_URN, json);
			assertThat(result.valid()).as(report.path("reportType").asString() + ": " + result.issues()).isTrue();
		}
	}

	private Stream<JsonNode> stream(JsonNode array) {
		Stream.Builder<JsonNode> builder = Stream.builder();
		array.forEach(builder::add);
		return builder.build();
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
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), "{\"content\":\"fixture\"}"));
	}

	private ArtifactVersion seedDesignVersion(UUID projectId, String proposalLocalRef) {
		Artifact designArtifact = artifactRepository
				.findByProjectIdAndType(projectId, "design-proposal-set")
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set")));
		int nextVersion = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(designArtifact.getId()).size() + 1;
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		return artifactVersionRepository.saveAndFlush(new ArtifactVersion(
				designArtifact.getId(),
				nextVersion,
				designExecution.getId(),
				"{\"proposals\":[{\"localRef\":\"" + proposalLocalRef + "\"}]}"));
	}

	private WebsiteImplementationCandidate seedCandidateWithDesign(UUID projectId, String proposalLocalRef) {
		ArtifactVersion designVersion = seedDesignVersion(projectId, proposalLocalRef);
		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				RUNTIME_PROFILE_REF,
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private WebsiteImplementationCandidate seedCandidateWithBinding(UUID projectId, String proposalLocalRef) {
		ArtifactVersion designVersion = seedDesignVersion(projectId, proposalLocalRef);
		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		String functionalBindings = "[{\"requirementRef\":\"REQ-CONTACT-FORM\",\"status\":\"IMPLEMENTED_BOUND\","
				+ "\"integrationContractRef\":\"CONTRACT-EMAIL\"}]";
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				RUNTIME_PROFILE_REF,
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				functionalBindings,
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

	private CandidateFinding seedFindingWithRequirement(QaResult qaResult, WebsiteImplementationCandidate candidate, String requirementRef) {
		String normativeBasis = "[{\"type\":\"WEBSITE_REQUIREMENT\",\"ref\":\"" + requirementRef + "\"}]";
		return candidateFindingRepository.saveAndFlush(new CandidateFinding(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				"CONTENT_PLACEHOLDER_LEAK",
				"CONTENT_QUALITY",
				"MINOR",
				normativeBasis,
				"Placeholder text found in a secondary section.",
				null,
				null,
				"[]",
				"fp-" + UUID.randomUUID(),
				"{}"));
	}

	private void seedPolicyEvaluation(
			QaResult qaResult, WebsiteImplementationCandidate candidate, CandidateFinding finding, String disposition) {
		policyEvaluationRepository.saveAndFlush(new PolicyEvaluation(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				qaResult.getQaProfileRef(),
				"CANDIDATE_FINDING",
				finding.getId().toString(),
				"severity-default",
				disposition,
				null));
	}

	private IntegrationContract seedIntegrationContract(UUID projectId, String contractRef, boolean withSymbolicBinding) {
		String symbolicBindings = withSymbolicBinding
				? "[{\"bindingName\":\"emailApiKey\",\"runtimeConfigKey\":\"EMAIL_API_KEY\"}]"
				: "[]";
		String safeContractContent = "{\"integrationContractVersion\":1,\"interfaceName\":\"EmailDeliveryService\","
				+ "\"authorizedOperations\":[{\"operationName\":\"sendMessage\",\"requestShape\":{},\"responseShape\":{}}],"
				+ "\"allowedRuntimeTargets\":[\"CLIENT_STATIC\"],\"symbolicBindings\":" + symbolicBindings + "}";
		return integrationContractRepository.saveAndFlush(new IntegrationContract(projectId, contractRef, 1, safeContractContent));
	}
}
