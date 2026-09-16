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
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.projecttype.website.DeveloperExecutionInputAssembler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-180's own named scenarios: valid remediation, authority conflict,
 * wrong source Candidate, prohibited scope expansion.
 */
@SpringBootTest
@Transactional
class QaRemediationPreflightValidatorIT {

	private static final String CUSTOMER_PROFILE = """
			{
			  "business": {"name": "Green Leaf Cafe"},
			  "contact": {"phone": "+43 1 2345678"},
			  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
			  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
			  "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []
			}
			""";

	private static final String WEBSITE_REQUIREMENTS = """
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

	private static String proposalSetJson() {
		return """
				{"proposals": [%s, %s, %s]}
				""".formatted(proposal("prop-a"), proposal("prop-b"), proposal("prop-c"));
	}

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private DeveloperExecutionInputAssembler assembler;

	@Autowired
	private QaRemediationPreflightValidator validator;

	@Test
	void aValidRemediationRequestPasses() {
		UUID projectId = seedProject();
		ArtifactVersion designVersion = persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, designVersion, "prop-a");
		CandidateFinding finding = seedFinding(projectId, candidate.getId());

		String input = assembler.assembleForRemediation(
				projectId, candidate, UUID.randomUUID().toString(), List.of(finding.getId().toString()), List.of(), "commit-sha", 3);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void anAuthorityConflictIsRejectedWhenTheTargetVariantLineageDiffersFromTheSourceCandidatesOwn() {
		UUID projectId = seedProject();
		ArtifactVersion designVersion = persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, designVersion, "prop-a");
		CandidateFinding finding = seedFinding(projectId, candidate.getId());

		// The assembled input targets a DIFFERENT proposal than the source Candidate's own lineage.
		String input = tamperedTargetProposal(
				assembler.assembleForRemediation(
						projectId, candidate, UUID.randomUUID().toString(), List.of(finding.getId().toString()), List.of(), "commit-sha", 3),
				"prop-b");

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("REMEDIATION_AUTHORITY_CONFLICT"));
	}

	@Test
	void aWrongOrNonExistentSourceCandidateIsRejected() {
		UUID projectId = seedProject();
		ArtifactVersion designVersion = persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, designVersion, "prop-a");
		CandidateFinding finding = seedFinding(projectId, candidate.getId());

		String input = assembler.assembleForRemediation(
				projectId, candidate, UUID.randomUUID().toString(), List.of(finding.getId().toString()), List.of(), "commit-sha", 3);
		String tampered = input.replace(candidate.getId().toString(), UUID.randomUUID().toString());

		PreExecutionValidationResult result = validator.validate(projectId, tampered);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("INVALID_SOURCE_CANDIDATE"));
	}

	@Test
	void aFindingBelongingToADifferentCandidateIsRejectedAsProhibitedScopeExpansion() {
		UUID projectId = seedProject();
		ArtifactVersion designVersion = persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());
		WebsiteImplementationCandidate sourceCandidate = seedCandidate(projectId, designVersion, "prop-a");
		WebsiteImplementationCandidate otherCandidate = seedCandidate(projectId, designVersion, "prop-a");
		CandidateFinding foreignFinding = seedFinding(projectId, otherCandidate.getId());

		String input = assembler.assembleForRemediation(
				projectId, sourceCandidate, UUID.randomUUID().toString(), List.of(foreignFinding.getId().toString()), List.of(), "commit-sha", 3);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("REMEDIATION_SCOPE_INCOMPATIBLE"));
	}

	@Test
	void noAuthorizedFindingsIsRejectedAsMissingRemediationAuthority() {
		UUID projectId = seedProject();
		ArtifactVersion designVersion = persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, designVersion, "prop-a");
		CandidateFinding finding = seedFinding(projectId, candidate.getId());

		String input = assembler.assembleForRemediation(
				projectId, candidate, UUID.randomUUID().toString(), List.of(finding.getId().toString()), List.of(), "commit-sha", 3);
		String tampered = input.replace("\"authorizedFindingRefs\":[\"" + finding.getId() + "\"]", "\"authorizedFindingRefs\":[]");

		PreExecutionValidationResult result = validator.validate(projectId, tampered);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("MISSING_REMEDIATION_AUTHORITY"));
	}

	@Test
	void anInitialGenerationInputIsPassedThroughUnchanged() {
		UUID projectId = seedProject();
		persistArtifactVersion(projectId, "design-proposal-set", proposalSetJson());

		String input = assembler.assemble(projectId, "prop-a", "commit-sha", 3);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	private String tamperedTargetProposal(String assembledJson, String replacementLocalRef) {
		return assembledJson.replaceFirst("\"targetProposalLocalRef\":\"[^\"]*\"", "\"targetProposalLocalRef\":\"" + replacementLocalRef + "\"");
	}

	private UUID seedProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		return project.getId();
	}

	private ArtifactVersion persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		return artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}

	private WebsiteImplementationCandidate seedCandidate(UUID projectId, ArtifactVersion designVersion, String proposalLocalRef) {
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), designVersion.getId().toString(), proposalLocalRef, "runtime-profile-fixture",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
	}

	private CandidateFinding seedFinding(UUID projectId, UUID testedCandidateId) {
		AgentExecution qaAgentExecution = new AgentExecution(projectId, "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), testedCandidateId, "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		UUID qaResultId = UUID.randomUUID();
		CandidateFinding finding = new CandidateFinding(
				qaResultId, qaExecution.getId(), testedCandidateId, "NAV_TARGET_MISMATCH", "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-" + UUID.randomUUID(), "{\"detectionMethod\":\"SEMANTIC\"}");
		qaResultRepository.saveAndFlush(new QaResult(
				qaResultId, qaExecution.getId(), testedCandidateId, "website-qa-full-release@1.0.0", inputSnapshot.getId(),
				"COMPLETE", "[]", "[\"" + finding.getId() + "\"]", "[]", "[]", "[]", "[]", "HOLD",
				"[\"BLOCKING_CANDIDATE_FINDING\"]", evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));

		return candidateFindingRepository.saveAndFlush(finding);
	}
}
