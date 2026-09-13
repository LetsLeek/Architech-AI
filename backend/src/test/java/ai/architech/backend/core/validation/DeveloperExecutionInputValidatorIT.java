package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.rule.RuleDefinitionNotFoundException;
import ai.architech.backend.core.rule.RuleLoader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers what AIW-141's own acceptance criteria names that is actually buildable against this
 * codebase's real infrastructure today (see {@link DeveloperExecutionInputValidator}'s own class
 * doc for what is deliberately deferred and why): valid setup, cross-project leakage, target-
 * proposal mismatch, embedded-content tampering, and missing agent/rule/skill resolvability.
 */
@SpringBootTest
@Transactional
class DeveloperExecutionInputValidatorIT {

	private static final String CUSTOMER_PROFILE =
			"""
			{
			  "business": {"name": "Green Leaf Cafe"},
			  "contact": {"phone": "+43 1 2345678"},
			  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
			  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
			  "openingHours": [],
			  "socialLinks": [],
			  "providedClaims": [],
			  "unknowns": [],
			  "conflicts": [],
			  "provenance": []
			}
			""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{
			  "goals": [
			    {"localRef": "req-goal-1", "description": "Grow local visibility", "strength": "must", "sourceRefs": ["source-1"]}
			  ],
			  "targetAudiences": [],
			  "contentRequirements": [
			    {"localRef": "req-content-1", "type": "offering", "description": "Show the menu", "strength": "must", "sourceRefs": ["source-1"]}
			  ],
			  "functionalRequirements": [],
			  "languages": [],
			  "constraints": [],
			  "unknowns": [],
			  "conflicts": []
			}
			""";

	private static final String PROPOSAL_A =
			"""
			{
			  "localRef": "prop-a",
			  "name": "Warm Minimal",
			  "concept": "A calm, minimal layout emphasizing the menu.",
			  "websitePlan": {
			    "requirementRefs": ["req-goal-1"],
			    "pages": [
			      {
			        "localRef": "page-a-home",
			        "name": "Home",
			        "route": "/",
			        "purpose": "Introduce the cafe and lead to the menu",
			        "requirementRefs": ["req-goal-1", "req-content-1"],
			        "sections": [
			          {
			            "localRef": "sec-a-hero",
			            "kind": "hero",
			            "purpose": "Welcome visitors",
			            "layoutIntent": "centered, single column",
			            "customerDataRefs": ["cust-loc-1"],
			            "elements": [
			              {"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome message"}
			            ]
			          }
			        ]
			      }
			    ]
			  },
			  "designSpecification": {
			    "colors": [{"role": "primary", "value": "#2f4f2f"}],
			    "typography": [
			      {"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}
			    ],
			    "spacing": [{"role": "section", "valueRem": 3}],
			    "layout": {
			      "contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"
			    },
			    "uiPatterns": [],
			    "imagery": {"direction": "warm, natural tones", "treatment": "soft-edged photography"},
			    "responsive": {
			      "navigationBehavior": "collapse into a menu icon", "contentStacking": "vertical", "typeScaling": "fluid clamp()",
			      "spacingAdjustment": "reduce by a third", "mediaBehavior": "scale to container"
			    }
			  }
			}
			""";

	private static final String PROPOSAL_SET_JSON =
			"""
			{"proposals": [%s, %s, %s]}
			"""
					.formatted(
							PROPOSAL_A,
							PROPOSAL_A.replace("prop-a", "prop-b"),
							PROPOSAL_A.replace("prop-a", "prop-c"));

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private DeveloperExecutionInputValidator validator;

	@MockitoBean
	private RuleLoader ruleLoader;

	@Test
	void acceptsAFullyValidExecutionInput() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion customerProfile = persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = executionInput(customerProfile, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsACrossProjectArtifactReference() {
		Project ownProject = projectRepository.saveAndFlush(new Project("website"));
		Project otherProject = projectRepository.saveAndFlush(new Project("website"));
		// Belongs to a DIFFERENT project than the execution claims to run under.
		ArtifactVersion customerProfile = persistArtifactVersion(otherProject.getId(), "customer-profile", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(ownProject.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(ownProject.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = executionInput(customerProfile, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(ownProject.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("different project"));
	}

	@Test
	void rejectsATargetProposalLocalRefThatDoesNotExistInTheDesignProposalSet() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion customerProfile = persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = executionInput(customerProfile, websiteRequirements, proposalSet, "prop-does-not-exist", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("does not exist"));
	}

	@Test
	void rejectsEmbeddedContentThatDoesNotMatchTheReferencedArtifactVersion() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion customerProfile = persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String tamperedCustomerProfile = CUSTOMER_PROFILE.replace("Green Leaf Cafe", "A Completely Different Business");
		String input = executionInput(
				customerProfile, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A, tamperedCustomerProfile);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("does not match"));
	}

	@Test
	void rejectsAnUnresolvableArtifactVersionReference() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = """
				{
				  "projectContext": {"projectRef": "p", "projectType": "WEBSITE", "operation": "INITIAL_GENERATION"},
				  "canonicalUpstream": {
				    "customerProfileArtifactVersionRef": "not-a-uuid",
				    "customerProfile": %s,
				    "websiteRequirementsArtifactVersionRef": "%s",
				    "websiteRequirements": %s
				  },
				  "targetDesign": {"designArtifactVersionRef": "%s", "targetProposalLocalRef": "prop-a", "proposal": %s},
				  "technicalContext": {
				    "runtimeProfileRef": "r", "developmentBaseRef": "d", "dependencyPolicyRef": "dp",
				    "verificationPolicyRef": "vp", "toolCapabilityProfileRef": "tp"
				  },
				  "integrationContext": {"integrationContracts": []},
				  "executionContext": {
				    "agentContractVersion": "1.0.0", "rulesetVersion": "1.0.0", "skillProfileRef": "sp",
				    "correctionBudget": {"maxCorrectionCycles": 3}
				  }
				}
				"""
				.formatted(CUSTOMER_PROFILE, websiteRequirements.getId(), WEBSITE_REQUIREMENTS, proposalSet.getId(), PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("not a resolvable artifact version reference"));
	}

	@Test
	void rejectsAReferenceToAnArtifactVersionThatDoesNotExist() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);
		// Well-formed UUID, but nothing was ever persisted under it.
		ArtifactVersion nonExistent = new ArtifactVersion(UUID.randomUUID(), 1, UUID.randomUUID(), CUSTOMER_PROFILE);

		String input = executionInput(nonExistent, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("no artifact version exists"));
	}

	@Test
	void rejectsAnArtifactVersionOfTheWrongType() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		// Wrong-type artifact deliberately isolated from both schema validation and content-match
		// checks: its content is genuinely customer-profile-shaped (so schema validation and
		// content matching both pass), but its owning Artifact row is typed "website-requirements"
		// - only the type check itself can catch this.
		ArtifactVersion wrongTypeVersion = persistArtifactVersion(project.getId(), "unrelated-artifact-type", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = executionInput(wrongTypeVersion, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("expected 'customer-profile'"));
	}

	@Test
	void rejectsAStructurallyInvalidExecutionInputBeforeAnyReferenceIsChecked() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		PreExecutionValidationResult result = validator.validate(project.getId(), "{ \"not\": \"a valid developer-execution-input\" }");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void reportsAnIssueWhenTheNormativeRuleCannotBeResolved() {
		when(ruleLoader.resolve(anyString(), anyInt())).thenThrow(new RuleDefinitionNotFoundException("website-developer-integrity", 1));

		Project project = projectRepository.saveAndFlush(new Project("website"));
		ArtifactVersion customerProfile = persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		ArtifactVersion websiteRequirements = persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		ArtifactVersion proposalSet = persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);

		String input = executionInput(customerProfile, websiteRequirements, proposalSet, "prop-a", PROPOSAL_A);

		PreExecutionValidationResult result = validator.validate(project.getId(), input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("rules"));
	}

	private ArtifactVersion persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = new AgentExecution(projectId, "requirements-agent", 1);
		execution = agentExecutionRepository.saveAndFlush(execution);
		return artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}

	private String executionInput(
			ArtifactVersion customerProfile,
			ArtifactVersion websiteRequirements,
			ArtifactVersion proposalSet,
			String targetProposalLocalRef,
			String embeddedProposal) {
		return executionInput(customerProfile, websiteRequirements, proposalSet, targetProposalLocalRef, embeddedProposal, CUSTOMER_PROFILE);
	}

	private String executionInput(
			ArtifactVersion customerProfile,
			ArtifactVersion websiteRequirements,
			ArtifactVersion proposalSet,
			String targetProposalLocalRef,
			String embeddedProposal,
			String embeddedCustomerProfile) {
		return """
				{
				  "projectContext": {
				    "projectRef": "project-fixture-1",
				    "projectType": "WEBSITE",
				    "operation": "INITIAL_GENERATION"
				  },
				  "canonicalUpstream": {
				    "customerProfileArtifactVersionRef": "%s",
				    "customerProfile": %s,
				    "websiteRequirementsArtifactVersionRef": "%s",
				    "websiteRequirements": %s
				  },
				  "targetDesign": {
				    "designArtifactVersionRef": "%s",
				    "targetProposalLocalRef": "%s",
				    "proposal": %s
				  },
				  "technicalContext": {
				    "runtimeProfileRef": "website-react-typescript-vite-client-v1",
				    "developmentBaseRef": "dev-base-fixture",
				    "dependencyPolicyRef": "website-developer-dependency-policy-v1",
				    "verificationPolicyRef": "website-developer-verification-policy-v1",
				    "toolCapabilityProfileRef": "website-developer-tools-v1"
				  },
				  "integrationContext": {
				    "integrationContracts": []
				  },
				  "executionContext": {
				    "agentContractVersion": "1.0.0",
				    "rulesetVersion": "1.0.0",
				    "skillProfileRef": "website-developer-skill-profile-v1",
				    "correctionBudget": {"maxCorrectionCycles": 3}
				  }
				}
				"""
				.formatted(
						customerProfile.getId(),
						embeddedCustomerProfile,
						websiteRequirements.getId(),
						WEBSITE_REQUIREMENTS,
						proposalSet.getId(),
						targetProposalLocalRef,
						embeddedProposal);
	}
}
