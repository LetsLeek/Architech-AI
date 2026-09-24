package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates a {@code developer-agent-result:v1} candidate's {@code implementationAnchors}
 * (AIW-142): uniqueness (no two anchors both claim the same design element under the same
 * {@code kind}), that {@code PAGE}/{@code SECTION} anchors resolve to a real page/section in the
 * target proposal, that every {@code PAGE}/{@code SECTION} in the target proposal has at least
 * one anchor ("mandatory Page/Section coverage" - missing coverage fails result validation per
 * AIW-142's own acceptance criteria), and that every anchor target {@code path} resolves to a
 * real file in the frozen handoff repository state.
 *
 * <p>{@code repositoryFiles} is caller-supplied (the exact set of repository-relative paths
 * present in the frozen handoff state, e.g. from {@code git ls-files}) rather than this class
 * reading a live workspace itself - the same boundary {@code SecretScanGate} draws for the same
 * reason: this validator is pure and unit-testable against fixtures without needing a real
 * sandbox/workspace at this ticket's own scope.
 */
@Component
public class ImplementationAnchorValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DeveloperResultValidationResult validate(String resultJson, String proposalJson, Set<String> repositoryFiles) {
		JsonNode result;
		JsonNode proposal;
		try {
			result = objectMapper.readTree(resultJson);
			proposal = objectMapper.readTree(proposalJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false, List.of(new DeveloperResultValidationIssue("anchor", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		Set<String> pageRefs = new HashSet<>();
		Set<String> sectionRefs = new HashSet<>();
		for (JsonNode page : proposal.path("websitePlan").path("pages")) {
			String pageRef = page.path("localRef").asString(null);
			if (pageRef != null) {
				pageRefs.add(pageRef);
			}
			for (JsonNode section : page.path("sections")) {
				String sectionRef = section.path("localRef").asString(null);
				if (sectionRef != null) {
					sectionRefs.add(sectionRef);
				}
			}
		}
		Set<String> allProposalLocalRefs = new HashSet<>(LocalRefExtractor.extract(proposal));

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		Set<String> seenAnchorKeys = new HashSet<>();
		Set<String> coveredPages = new HashSet<>();
		Set<String> coveredSections = new HashSet<>();

		for (JsonNode anchor : result.path("implementationAnchors")) {
			String designLocalRef = anchor.path("designLocalRef").asString(null);
			String kind = anchor.path("kind").asString("");

			if (!seenAnchorKeys.add(designLocalRef + "::" + kind)) {
				issues.add(new DeveloperResultValidationIssue(
						"anchor", "implementationAnchors", "duplicate anchor for '" + designLocalRef + "' (" + kind + ")"));
			}

			switch (kind) {
				case "PAGE" -> {
					if (pageRefs.contains(designLocalRef)) {
						coveredPages.add(designLocalRef);
					} else {
						issues.add(unresolvedAnchorIssue(designLocalRef, kind));
					}
				}
				case "SECTION" -> {
					if (sectionRefs.contains(designLocalRef)) {
						coveredSections.add(designLocalRef);
					} else {
						issues.add(unresolvedAnchorIssue(designLocalRef, kind));
					}
				}
				default -> {
					if (!allProposalLocalRefs.contains(designLocalRef)) {
						issues.add(unresolvedAnchorIssue(designLocalRef, kind));
					}
				}
			}

			for (JsonNode target : anchor.path("targets")) {
				String path = target.path("path").asString(null);
				if (path != null && !repositoryFiles.contains(path)) {
					issues.add(new DeveloperResultValidationIssue(
							"anchor",
							"implementationAnchors.targets.path",
							"'" + path + "' does not resolve to a real repository-relative source file in the frozen handoff state"));
				}
			}
		}

		for (String pageRef : pageRefs) {
			if (!coveredPages.contains(pageRef)) {
				issues.add(new DeveloperResultValidationIssue("anchor", "implementationAnchors", "page '" + pageRef + "' has no implementation anchor"));
			}
		}
		for (String sectionRef : sectionRefs) {
			if (!coveredSections.contains(sectionRef)) {
				issues.add(new DeveloperResultValidationIssue(
						"anchor", "implementationAnchors", "section '" + sectionRef + "' has no implementation anchor"));
			}
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}

	private DeveloperResultValidationIssue unresolvedAnchorIssue(String designLocalRef, String kind) {
		return new DeveloperResultValidationIssue(
				"anchor", "implementationAnchors.designLocalRef", "'" + designLocalRef + "' (" + kind + ") is not part of the target proposal");
	}
}
