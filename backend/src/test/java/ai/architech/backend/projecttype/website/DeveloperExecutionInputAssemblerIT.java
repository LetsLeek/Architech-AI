package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.validation.DeveloperExecutionInputValidator;
import ai.architech.backend.core.validation.PreExecutionValidationResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves AIW-151's assembler produces exactly what AIW-141's validator accepts - a real,
 * end-to-end round trip through both tickets, not two isolated unit tests that happen to agree
 * on paper.
 */
@SpringBootTest
@Transactional
class DeveloperExecutionInputAssemblerIT {

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
			  "goals": [{"localRef": "req-goal-1", "description": "Grow local visibility", "strength": "must", "sourceRefs": ["s1"]}],
			  "targetAudiences": [],
			  "contentRequirements": [
			    {"localRef": "req-content-1", "type": "offering", "description": "Show the menu", "strength": "must", "sourceRefs": ["s1"]}
			  ],
			  "functionalRequirements": [], "languages": [], "constraints": [], "unknowns": [], "conflicts": []
			}
			""";

	private static String proposal(String localRef) {
		return """
				{
				  "localRef": "%s",
				  "name": "Warm Minimal",
				  "concept": "A calm, minimal layout emphasizing the menu.",
				  "websitePlan": {
				    "requirementRefs": ["req-goal-1"],
				    "pages": [
				      {
				        "localRef": "page-a-home", "name": "Home", "route": "/",
				        "purpose": "Introduce the cafe and lead to the menu",
				        "requirementRefs": ["req-goal-1", "req-content-1"],
				        "sections": [
				          {
				            "localRef": "sec-a-hero", "kind": "hero", "purpose": "Welcome visitors",
				            "layoutIntent": "centered, single column", "customerDataRefs": ["cust-loc-1"],
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
				.formatted(localRef);
	}

	private static final String PROPOSAL_SET_JSON =
			"""
			{"proposals": [%s, %s, %s]}
			""".formatted(proposal("prop-a"), proposal("prop-b"), proposal("prop-c"));

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
	private DeveloperExecutionInputValidator validator;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void assembledInputPassesTheRealPreExecutionValidator() {
		UUID projectId = seedProject();

		String assembled = assembler.assemble(projectId, "prop-a", "commit-sha-fixture", 3);
		PreExecutionValidationResult result = validator.validate(projectId, assembled);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void embedsOnlyTheTargetProposalNeverItsSiblings() {
		UUID projectId = seedProject();

		String assembled = assembler.assemble(projectId, "prop-b", "commit-sha-fixture", 3);
		JsonNode input = objectMapper.readTree(assembled);

		assertThat(input.path("targetDesign").path("proposal").path("localRef").asString()).isEqualTo("prop-b");
		assertThat(assembled).doesNotContain("\"localRef\":\"prop-a\"").doesNotContain("\"localRef\":\"prop-c\"");
	}

	@Test
	void integrationContractsIsAlwaysEmptyInV1() {
		UUID projectId = seedProject();

		String assembled = assembler.assemble(projectId, "prop-a", "commit-sha-fixture", 3);
		JsonNode input = objectMapper.readTree(assembled);

		assertThat(input.path("integrationContext").path("integrationContracts")).isEmpty();
	}

	@Test
	void includesRetryContextOnlyWhenSupplied() {
		UUID projectId = seedProject();

		String withoutRetry = assembler.assemble(projectId, "prop-a", "commit-sha-fixture", 3);
		assertThat(objectMapper.readTree(withoutRetry).path("executionContext").has("retryContext")).isFalse();

		String withRetry = assembler.assemble(
				projectId,
				"prop-a",
				"commit-sha-fixture",
				3,
				new RetryContext(UUID.randomUUID().toString(), "RUNNER_VERIFICATION_FAILURE", "typecheck failed"));
		JsonNode retryNode = objectMapper.readTree(withRetry).path("executionContext").path("retryContext");
		assertThat(retryNode.path("retryReasonCode").asString()).isEqualTo("RUNNER_VERIFICATION_FAILURE");
	}

	@Test
	void throwsWhenACanonicalArtifactIsMissing() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThatThrownBy(() -> assembler.assemble(project.getId(), "prop-a", "commit-sha-fixture", 3))
				.isInstanceOf(ApplicationException.class);
	}

	@Test
	void throwsWhenTheTargetProposalLocalRefDoesNotExist() {
		UUID projectId = seedProject();

		assertThatThrownBy(() -> assembler.assemble(projectId, "prop-does-not-exist", "commit-sha-fixture", 3))
				.isInstanceOf(IllegalArgumentException.class);
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
