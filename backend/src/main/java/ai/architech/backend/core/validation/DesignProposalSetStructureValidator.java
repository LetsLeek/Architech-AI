package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Structural invariants of a {@code design-proposal-set} candidate that JSON Schema alone
 * cannot express (AIW-122), checked entirely from the candidate's own JSON - no external
 * artifact needed:
 *
 * <ul>
 *   <li>exactly one root route {@code /} exists per proposal;
 *   <li>page routes are unique within each proposal;
 *   <li>{@code pageRef} resolves to a page in the same proposal;
 *   <li>{@code sectionRef} resolves to a section under the specific page its sibling
 *       {@code pageRef} names;
 *   <li>{@code patternRef} resolves to a UI pattern in the same proposal.
 * </ul>
 *
 * <p>Every check below resolves a reference only against the sets collected from the <em>same
 * proposal</em> the reference appears in - a reference that only resolves in a different
 * proposal therefore fails as "not found here" rather than being silently accepted, which is
 * exactly what "no local references cross proposal boundaries" requires; no separate pass is
 * needed to enforce that rule.
 *
 * <p>{@code localRef} global uniqueness across the whole artifact is {@link
 * LocalRefUniquenessValidator}'s existing, already-generic concern - not duplicated here.
 */
@Component
public class DesignProposalSetStructureValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DesignProposalSetValidationResult validate(String candidateJson) {
		JsonNode root;
		try {
			root = objectMapper.readTree(candidateJson);
		} catch (RuntimeException e) {
			return new DesignProposalSetValidationResult(
					false, List.of(new DesignProposalSetValidationIssue(null, "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		List<DesignProposalSetValidationIssue> issues = new ArrayList<>();
		for (JsonNode proposal : root.path("proposals")) {
			issues.addAll(validateProposal(proposal));
		}
		return issues.isEmpty() ? DesignProposalSetValidationResult.passed() : new DesignProposalSetValidationResult(false, issues);
	}

	private List<DesignProposalSetValidationIssue> validateProposal(JsonNode proposal) {
		String proposalRef = proposal.path("localRef").asString(null);
		List<DesignProposalSetValidationIssue> issues = new ArrayList<>();

		Set<String> pageRefs = new HashSet<>();
		Map<String, Set<String>> sectionRefsByPage = new HashMap<>();
		List<String> routes = new ArrayList<>();
		for (JsonNode page : proposal.path("websitePlan").path("pages")) {
			String pageRef = page.path("localRef").asString(null);
			if (pageRef != null) {
				pageRefs.add(pageRef);
			}
			routes.add(page.path("route").asString(null));

			Set<String> sectionRefs = new HashSet<>();
			for (JsonNode section : page.path("sections")) {
				String sectionRef = section.path("localRef").asString(null);
				if (sectionRef != null) {
					sectionRefs.add(sectionRef);
				}
			}
			if (pageRef != null) {
				sectionRefsByPage.put(pageRef, sectionRefs);
			}
		}

		Set<String> patternRefs = new HashSet<>();
		for (JsonNode uiPattern : proposal.path("designSpecification").path("uiPatterns")) {
			String patternRef = uiPattern.path("localRef").asString(null);
			if (patternRef != null) {
				patternRefs.add(patternRef);
			}
		}

		issues.addAll(validateRoutes(proposalRef, routes));
		collectRefIssues(proposalRef, proposal, pageRefs, sectionRefsByPage, patternRefs, issues);
		return issues;
	}

	private static List<DesignProposalSetValidationIssue> validateRoutes(String proposalRef, List<String> routes) {
		List<DesignProposalSetValidationIssue> issues = new ArrayList<>();
		long rootCount = routes.stream().filter("/"::equals).count();
		if (rootCount != 1) {
			issues.add(new DesignProposalSetValidationIssue(
					proposalRef, "websitePlan.pages[].route", "expected exactly one root route '/', found " + rootCount));
		}

		Map<String, Long> occurrences = new HashMap<>();
		routes.forEach(route -> occurrences.merge(route, 1L, Long::sum));
		occurrences.forEach((route, count) -> {
			if (count > 1) {
				issues.add(new DesignProposalSetValidationIssue(
						proposalRef, "websitePlan.pages[].route", "route '" + route + "' used " + count + " times, must be unique within the proposal"));
			}
		});
		return issues;
	}

	/**
	 * A single generic walk collecting every {@code pageRef}/{@code sectionRef}/{@code
	 * patternRef} occurrence anywhere in the proposal - {@code sectionRef} always appears
	 * alongside its own {@code pageRef} in the same {@code navigationTarget} object (the only
	 * shape the frozen schema defines for it), so detecting that pairing needs no explicit
	 * knowledge of the navigationTarget "type" discriminator itself.
	 */
	private static void collectRefIssues(
			String proposalRef,
			JsonNode node,
			Set<String> pageRefs,
			Map<String, Set<String>> sectionRefsByPage,
			Set<String> patternRefs,
			List<DesignProposalSetValidationIssue> issues) {
		if (node.isObject()) {
			JsonNode sectionRefNode = node.path("sectionRef");
			JsonNode pageRefNode = node.path("pageRef");
			if (sectionRefNode.isTextual()) {
				String sectionRef = sectionRefNode.asString();
				String pageRef = pageRefNode.isTextual() ? pageRefNode.asString() : null;
				if (pageRef == null || !pageRefs.contains(pageRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef, "target.pageRef", "pageRef '" + pageRef + "' does not resolve to a page in this proposal"));
				} else if (!sectionRefsByPage.getOrDefault(pageRef, Set.of()).contains(sectionRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef,
							"target.sectionRef",
							"sectionRef '" + sectionRef + "' does not resolve to a section under page '" + pageRef + "'"));
				}
			} else if (pageRefNode.isTextual()) {
				String pageRef = pageRefNode.asString();
				if (!pageRefs.contains(pageRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef, "target.pageRef", "pageRef '" + pageRef + "' does not resolve to a page in this proposal"));
				}
			}

			JsonNode patternRefNode = node.path("patternRef");
			if (patternRefNode.isTextual()) {
				String patternRef = patternRefNode.asString();
				if (!patternRefs.contains(patternRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef, "element.patternRef", "patternRef '" + patternRef + "' does not resolve to a UI pattern in this proposal"));
				}
			}

			for (String property : node.propertyNames()) {
				collectRefIssues(proposalRef, node.path(property), pageRefs, sectionRefsByPage, patternRefs, issues);
			}
		} else if (node.isArray()) {
			for (JsonNode item : node) {
				collectRefIssues(proposalRef, item, pageRefs, sectionRefsByPage, patternRefs, issues);
			}
		}
	}
}
