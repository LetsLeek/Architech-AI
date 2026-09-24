package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.toolexecution.ToolCapability;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import ai.architech.backend.projecttype.website.DeveloperExecutionInputAssembler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof of AIW-142's full pipeline, seeded through the same real
 * {@link DeveloperExecutionInputAssembler} AIW-151 built (a genuine cross-ticket round trip, not
 * a hand-typed executionInput that happens to agree with the validator on paper) - covers every
 * fixture scenario AIW-142's own acceptance criteria names: schema failure, target mismatch, bad
 * anchors, binding coverage, duplicate issues, fake blockers and valid handoff (both branches).
 */
@SpringBootTest
@Transactional
class DeveloperResultValidatorIT {

	private static final String CUSTOMER_PROFILE =
			"""
			{
			  "business": {"name": "Green Leaf Cafe"},
			  "contact": {"phone": "+43 1 2345678"},
			  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
			  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
			  "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []
			}
			""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{
			  "goals": [], "targetAudiences": [], "contentRequirements": [],
			  "functionalRequirements": [
			    {"localRef": "req-func-1", "type": "booking", "description": "Let customers book a table", "strength": "must", "sourceRefs": ["s1"]}
			  ],
			  "languages": [], "constraints": [], "unknowns": [], "conflicts": []
			}
			""";

	private static String proposal(String localRef, boolean addressesFunctionalRequirement) {
		return """
				{
				  "localRef": "%s",
				  "name": "Warm Minimal",
				  "concept": "A calm, minimal layout emphasizing the menu.",
				  "websitePlan": {
				    "requirementRefs": [%s],
				    "pages": [
				      {
				        "localRef": "page-a-home", "name": "Home", "route": "/",
				        "purpose": "Introduce the cafe and lead to booking",
				        "sections": [
				          {
				            "localRef": "sec-a-hero", "kind": "hero", "purpose": "Welcome visitors",
				            "layoutIntent": "centered, single column",
				            "elements": [{"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome"}]
				          }
				        ]
				      }
				    ]
				  },
				  "designSpecification": {
				    "colors": [{"role": "primary", "value": "#2f4f2f"}],
				    "typography": [{"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}],
				    "spacing": [{"role": "section", "valueRem": 3}],
				    "layout": {"contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"},
				    "uiPatterns": [],
				    "imagery": {"direction": "warm, natural tones", "treatment": "soft-edged photography"},
				    "responsive": {
				      "navigationBehavior": "collapse into a menu icon", "contentStacking": "vertical", "typeScaling": "fluid clamp()",
				      "spacingAdjustment": "reduce by a third", "mediaBehavior": "scale to container"
				    }
				  }
				}
				"""
				.formatted(localRef, addressesFunctionalRequirement ? "\"req-func-1\"" : "");
	}

	private static final String PROPOSAL_SET_JSON =
			"""
			{"proposals": [%s, %s, %s]}
			""".formatted(proposal("prop-a", true), proposal("prop-b", false), proposal("prop-c", false));

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private DeveloperExecutionInputAssembler assembler;

	@Autowired
	private DeveloperResultValidator developerResultValidator;

	@Autowired
	private ObjectMapper objectMapper;

	private static final Set<String> REPOSITORY_FILES = Set.of("src/pages/Home.tsx");

	@Test
	void rejectsACandidateThatFailsItsOwnDiscriminatedUnionSchema() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);

		String malformed = """
				{"resultType": "IMPLEMENTATION_READY", "targetDesign": {}}""";

		DeveloperResultValidationResult result = developerResultValidator.validate(
				malformed, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("schema"));
	}

	@Test
	void rejectsATargetDesignMismatch() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");

		String wrongTarget = readyResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-b-does-not-match",
				anchorsJson(),
				bindingsJson(),
				"[]");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				wrongTarget, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("target-identity"));
	}

	@Test
	void rejectsMissingPageCoverageAnchors() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");

		// Only a SECTION anchor - the mandatory PAGE anchor for "page-a-home" is missing.
		String missingPageAnchor = readyResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				"""
				[{"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}]""",
				bindingsJson(),
				"[]");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				missingPageAnchor, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue ->
				assertThat(issue.reason()).contains("page-a-home").contains("no implementation anchor"));
	}

	@Test
	void rejectsMissingFunctionalBindingCoverage() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");

		String noBindings = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), "[]", "[]");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				noBindings, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("functional-binding"));
	}

	@Test
	void rejectsNearDuplicateUnresolvedIssues() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");

		String duplicateIssues =
				"""
				[
				  {"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-func-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "Booking capacity unknown"},
				  {"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-func-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "We don't know the max party size"}
				]""";
		String result = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson(), duplicateIssues);

		DeveloperResultValidationResult validationResult = developerResultValidator.validate(
				result, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("unresolved-issue"));
	}

	@Test
	void rejectsAFakeToolCapabilityBlockerWithNoSupportingEvidence() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));
		// Evidence exists for this execution, but nothing in it was ever denied/errored - so a
		// TOOL_CAPABILITY_MISSING blocker has nothing real to point to.
		List<ToolExecution> succeededOnly = List.of(new ToolExecution(
				execution.getId(), ToolCapability.PROJECT_EXECUTION, "install", 0,
				ToolExecutionStatus.SUCCEEDED, null, Instant.now(), Instant.now()));

		String blocked = blockedResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				"""
				[{"code": "TOOL_CAPABILITY_MISSING", "relatedRequirementRefs": [], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "diagnosticSummary": "npm blocked"}]""");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				blocked, executionInput, projectId, REPOSITORY_FILES, succeededOnly);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("blocker"));
	}

	@Test
	void acceptsAValidImplementationReadyHandoff() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");

		String valid = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson(), "[]");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				valid, executionInput, projectId, REPOSITORY_FILES, List.of());

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void acceptsAValidBlockedHandoffWithSupportingEvidence() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));
		List<ToolExecution> deniedEvidence = List.of(new ToolExecution(
				execution.getId(), ToolCapability.PROJECT_EXECUTION, "install", 0,
				ToolExecutionStatus.DENIED, "capability not on the allowed list", Instant.now(), Instant.now()));

		String blocked = blockedResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				"""
				[{"code": "TOOL_CAPABILITY_MISSING", "relatedRequirementRefs": [], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "diagnosticSummary": "npm install was denied"}]""");

		DeveloperResultValidationResult result = developerResultValidator.validate(
				blocked, executionInput, projectId, REPOSITORY_FILES, deniedEvidence);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	private String assemble(UUID projectId) {
		return assembler.assemble(projectId, "prop-a", "commit-sha-fixture", 3);
	}

	private String anchorsJson() {
		return """
				[
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx", "symbol": "HeroSection"}]}
				]""";
	}

	private String bindingsJson() {
		return """
				[{"requirementRef": "req-func-1", "designLocalRefs": ["sec-a-hero"], "status": "IMPLEMENTED_LOCAL"}]""";
	}

	private String readyResult(
			String designArtifactVersionRef, String proposalLocalRef, String anchors, String bindings, String unresolvedIssues) {
		return """
				{
				  "resultType": "IMPLEMENTATION_READY",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "%s"},
				  "implementationSummary": "Implemented the home page hero.",
				  "implementationAnchors": %s,
				  "functionalBindings": %s,
				  "unresolvedIssues": %s
				}
				""".formatted(designArtifactVersionRef, proposalLocalRef, anchors, bindings, unresolvedIssues);
	}

	private String blockedResult(String designArtifactVersionRef, String proposalLocalRef, String blockers) {
		return """
				{
				  "resultType": "BLOCKED",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "%s"},
				  "blockers": %s
				}
				""".formatted(designArtifactVersionRef, proposalLocalRef, blockers);
	}

	private UUID seedProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);
		return project.getId();
	}

	private void persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}
}
