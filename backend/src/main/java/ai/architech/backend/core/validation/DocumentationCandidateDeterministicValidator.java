package ai.architech.backend.core.validation;

import ai.architech.backend.core.documentation.claimtypes.DocumentationClaimType;
import ai.architech.backend.core.documentation.claimtypes.DocumentationClaimTypeRegistry;
import ai.architech.backend.core.documentation.claimtypes.DocumentationClaimTypeRegistryLoader;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.profiles.DocumentSectionSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.SemanticDocumentSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The second validation gate for a raw {@code semantic-documentation-candidate:v1} model
 * output, run after {@link DocumentationCandidateStructureValidator} (AIW-195) - covers
 * {@code validators/candidate/DETERMINISTIC.md}'s five areas as **one connected module**
 * (not 14 separate classes, per the plan's own instruction), scoped precisely against what
 * AIW-195 already checked (see that class's own javadoc for the exact boundary).
 *
 * <p><b>Structure (the half AIW-195 left)</b>: section *order* against the active profile's own
 * declared sequence, and global {@code claimKey} uniqueness across every claim in the candidate.
 *
 * <p><b>Keys</b>: every claim's {@code authorityKeys}/{@code disclosureKeys} resolve against
 * this exact {@link DocumentationContext}'s own {@code authorityCatalog}/{@code
 * findingDisclosureView} - never against any other context, retry, or previous document. A
 * resolved authority entry backed by a {@code CONTEXT_STATE} locator must also have a matching
 * {@code contextStates} record (defensive - {@code DocumentationContextAssembler} always creates
 * both together, so this should never actually fail against real data).
 *
 * <p><b>Domains</b>: the structural minimum-domain-presence precheck from {@code
 * registries/claim-types.yaml} (AIW-196 is the ticket that finally loads it - AIW-188
 * deliberately deferred it here). Deeper, statement-specific semantic sufficiency is AIW-198's
 * job, not this one.
 *
 * <p><b>Disclosure</b>: every {@code findingDisclosureView} entry must have a matching claim
 * that cites both its {@code disclosureKey} and {@code findingAuthorityKey} together, placed in
 * the audience-appropriate section ({@code KNOWN_LIMITATIONS} for {@code CUSTOMER}, {@code
 * QA_AND_OUTSTANDING_ISSUES} for {@code DEVELOPER}). The DETERMINISTIC.md bullet's {@code
 * BLOCK_DOCUMENT}/{@code OMIT} halves are structurally unreachable here: {@link
 * ai.architech.backend.core.documentation.context.DocumentationFindingDisclosureEvaluator}
 * (AIW-192) only ever produces {@code action: DISCLOSE} entries - a blocking/escalated/unmapped
 * finding already throws before any context is even assembled, and {@code OMIT} is never
 * produced since only current-candidate findings are ever queried.
 *
 * <p><b>Lifecycle + Cross-Project</b>: {@code QA_STATUS} claims must cite the QA gate's own
 * authority key; {@code APPROVAL_STATUS}/{@code DEPLOYMENT_STATUS} claims must cite the matching
 * {@code MISSING_AUTHORITY} context-state's own authority key - the *only* legitimate authority
 * for either claim type in this codebase today, since no real {@code ApprovalRecord}/{@code
 * DeploymentRecord} type exists anywhere ({@code DECISION_LOG.md} point 5: a context-state can
 * support "no ApprovalRecord is bound to this context," never the stronger claim "customer did
 * not approve"). The "cross-project/lineage" half of this bullet needs no separate check: every
 * key lookup above is already scoped to the one {@link DocumentationContext} instance passed
 * into {@link #validate}, so there is no code path by which a claim could ever reference
 * anything belonging to a different context or project.
 */
@Component
public class DocumentationCandidateDeterministicValidator {

	private static final String QA_STATUS_CLAIM_TYPE = "QA_STATUS";
	private static final String APPROVAL_STATUS_CLAIM_TYPE = "APPROVAL_STATUS";
	private static final String DEPLOYMENT_STATUS_CLAIM_TYPE = "DEPLOYMENT_STATUS";
	private static final String QA_GATE_AUTHORITY_KEY = "AUTH_FULL_RELEASE_GATE";
	private static final String APPROVAL_CONTEXT_STATE_AUTHORITY_KEY = "AUTH_CTX_APPROVAL_RECORD";
	private static final String DEPLOYMENT_CONTEXT_STATE_AUTHORITY_KEY = "AUTH_CTX_DEPLOYMENT_RECORD";
	private static final String CUSTOMER_AUDIENCE = "CUSTOMER";
	private static final String DEVELOPER_AUDIENCE = "DEVELOPER";
	private static final Map<String, String> DISCLOSURE_SECTION_BY_AUDIENCE =
			Map.of(CUSTOMER_AUDIENCE, "KNOWN_LIMITATIONS", DEVELOPER_AUDIENCE, "QA_AND_OUTSTANDING_ISSUES");

	private final DocumentationClaimTypeRegistryLoader claimTypeRegistryLoader;
	private final ObjectMapper objectMapper;

	DocumentationCandidateDeterministicValidator(
			DocumentationClaimTypeRegistryLoader claimTypeRegistryLoader, ObjectMapper objectMapper) {
		this.claimTypeRegistryLoader = claimTypeRegistryLoader;
		this.objectMapper = objectMapper;
	}

	/**
	 * Assumes {@code candidateJson} already passed {@link DocumentationCandidateStructureValidator}
	 * - if no unique document of the profile's required type can be found, this method returns no
	 * issues rather than re-reporting AIW-195's own identity failure a second time under a
	 * different validator name.
	 */
	public DocumentationCandidateValidationResult validate(String candidateJson, DocumentationContext context, DocumentationProfile profile) {
		JsonNode candidate = objectMapper.readTree(candidateJson);
		JsonNode contextContent = objectMapper.readTree(context.getContentJson());
		SemanticDocumentSpec requiredDocumentSpec = profile.semanticDocuments().get(0);

		JsonNode document = findRequiredDocument(candidate, requiredDocumentSpec.documentType());
		if (document == null) {
			return DocumentationCandidateValidationResult.passed();
		}

		List<DocumentationCandidateValidationIssue> issues = new ArrayList<>();

		Map<String, JsonNode> authorityCatalogByKey = indexByField(contextContent.path("authorityCatalog"), "key");
		Map<String, JsonNode> disclosureViewByKey = indexByField(contextContent.path("findingDisclosureView").path("entries"), "disclosureKey");
		Set<String> contextStateKeys = streamArray(contextContent.path("contextStates"))
				.map(n -> n.path("stateKey").asString())
				.collect(Collectors.toCollection(HashSet::new));
		String primaryAudience = contextContent.path("primaryAudience").asString(null);

		validateSectionOrder(document, requiredDocumentSpec, issues);
		List<ClaimOccurrence> claims = collectClaims(document, issues);

		DocumentationClaimTypeRegistry claimTypeRegistry = claimTypeRegistryLoader.load();
		for (ClaimOccurrence occurrence : claims) {
			validateKeys(occurrence, authorityCatalogByKey, disclosureViewByKey, contextStateKeys, issues);
			validateDomains(occurrence, authorityCatalogByKey, claimTypeRegistry, issues);
			validateLifecycle(occurrence, issues);
		}

		validateDisclosureCoverage(contextContent, claims, primaryAudience, issues);

		return issues.isEmpty() ? DocumentationCandidateValidationResult.passed() : new DocumentationCandidateValidationResult(false, issues);
	}

	// -- Structure: section order + global claimKey uniqueness --

	private void validateSectionOrder(
			JsonNode document, SemanticDocumentSpec requiredDocumentSpec, List<DocumentationCandidateValidationIssue> issues) {
		List<String> expectedOrder = requiredDocumentSpec.sections().stream().map(DocumentSectionSpec::sectionType).toList();
		Set<String> expectedTypes = new LinkedHashSet<>(expectedOrder);

		List<String> actualOrder = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int index = 0;
		for (JsonNode section : document.path("sections")) {
			String sectionType = section.path("sectionType").asString(null);
			if (expectedTypes.contains(sectionType)) {
				if (!seen.add(sectionType)) {
					issues.add(new DocumentationCandidateValidationIssue(
							"structure", "documents[0].sections[" + index + "]", "duplicate section type '" + sectionType + "'"));
				} else {
					actualOrder.add(sectionType);
				}
			}
			index++;
		}

		if (!actualOrder.equals(expectedOrder)) {
			issues.add(new DocumentationCandidateValidationIssue(
					"structure",
					"documents[0].sections",
					"section order " + actualOrder + " does not match the profile's declared order " + expectedOrder));
		}
	}

	private record ClaimOccurrence(JsonNode claim, String sectionType, String ref) {}

	private List<ClaimOccurrence> collectClaims(JsonNode document, List<DocumentationCandidateValidationIssue> issues) {
		List<ClaimOccurrence> claims = new ArrayList<>();
		Set<String> seenClaimKeys = new HashSet<>();

		int sectionIndex = 0;
		for (JsonNode section : document.path("sections")) {
			String sectionType = section.path("sectionType").asString(null);
			int blockIndex = 0;
			for (JsonNode block : section.path("blocks")) {
				String blockType = block.path("blockType").asString(null);
				String blockRef = "documents[0].sections[" + sectionIndex + "].blocks[" + blockIndex + "]";
				if ("NARRATIVE".equals(blockType)) {
					collectClaimsFromArray(block.path("claims"), sectionType, blockRef + ".claims", claims, seenClaimKeys, issues);
				} else if ("LIST".equals(blockType)) {
					int itemIndex = 0;
					for (JsonNode item : block.path("items")) {
						collectClaimsFromArray(
								item.path("claims"),
								sectionType,
								blockRef + ".items[" + itemIndex + "].claims",
								claims,
								seenClaimKeys,
								issues);
						itemIndex++;
					}
				}
				blockIndex++;
			}
			sectionIndex++;
		}
		return claims;
	}

	private void collectClaimsFromArray(
			JsonNode claimsArray,
			String sectionType,
			String claimsArrayRef,
			List<ClaimOccurrence> claims,
			Set<String> seenClaimKeys,
			List<DocumentationCandidateValidationIssue> issues) {
		int claimIndex = 0;
		for (JsonNode claim : claimsArray) {
			String ref = claimsArrayRef + "[" + claimIndex + "]";
			String claimKey = claim.path("claimKey").asString(null);
			if (claimKey != null && !seenClaimKeys.add(claimKey)) {
				issues.add(new DocumentationCandidateValidationIssue(
						"structure", ref + ".claimKey", "duplicate claimKey '" + claimKey + "' within candidate"));
			}
			claims.add(new ClaimOccurrence(claim, sectionType, ref));
			claimIndex++;
		}
	}

	// -- Keys --

	private void validateKeys(
			ClaimOccurrence occurrence,
			Map<String, JsonNode> authorityCatalogByKey,
			Map<String, JsonNode> disclosureViewByKey,
			Set<String> contextStateKeys,
			List<DocumentationCandidateValidationIssue> issues) {
		for (JsonNode keyNode : occurrence.claim().path("authorityKeys")) {
			String key = keyNode.asString();
			JsonNode entry = authorityCatalogByKey.get(key);
			if (entry == null) {
				issues.add(new DocumentationCandidateValidationIssue(
						"keys", occurrence.ref() + ".authorityKeys", "authorityKey '" + key + "' does not resolve against this context's authorityCatalog"));
				continue;
			}
			JsonNode locator = entry.path("authorityRef").path("locator");
			if ("CONTEXT_STATE".equals(locator.path("kind").asString(null))) {
				String stateKey = locator.path("value").asString(null);
				if (!contextStateKeys.contains(stateKey)) {
					issues.add(new DocumentationCandidateValidationIssue(
							"keys",
							occurrence.ref() + ".authorityKeys",
							"authorityKey '" + key + "' references context-state '" + stateKey + "' with no matching contextStates record"));
				}
			}
		}

		for (JsonNode keyNode : occurrence.claim().path("disclosureKeys")) {
			String key = keyNode.asString();
			if (!disclosureViewByKey.containsKey(key)) {
				issues.add(new DocumentationCandidateValidationIssue(
						"keys",
						occurrence.ref() + ".disclosureKeys",
						"disclosureKey '" + key + "' does not resolve against this context's findingDisclosureView"));
			}
		}
	}

	// -- Domains --

	private void validateDomains(
			ClaimOccurrence occurrence,
			Map<String, JsonNode> authorityCatalogByKey,
			DocumentationClaimTypeRegistry claimTypeRegistry,
			List<DocumentationCandidateValidationIssue> issues) {
		String claimType = occurrence.claim().path("claimType").asString(null);
		Optional<DocumentationClaimType> claimTypeDef = claimTypeRegistry.byId(claimType);
		if (claimTypeDef.isEmpty() || claimTypeDef.get().domainMinimum().isEmpty()) {
			return;
		}

		Set<String> minimumDomains = new HashSet<>(claimTypeDef.get().domainMinimum());
		boolean satisfied = false;
		for (JsonNode keyNode : occurrence.claim().path("authorityKeys")) {
			JsonNode entry = authorityCatalogByKey.get(keyNode.asString());
			if (entry == null) {
				continue; // already flagged by the Keys check above
			}
			String domain = entry.path("authorityRef").path("authorityDomain").asString(null);
			if (minimumDomains.contains(domain)) {
				satisfied = true;
				break;
			}
		}

		if (!satisfied) {
			issues.add(new DocumentationCandidateValidationIssue(
					"domains",
					occurrence.ref() + ".authorityKeys",
					"claimType '" + claimType + "' requires at least one authorityKey whose domain is one of " + minimumDomains
							+ ", but none of the cited authorityKeys resolve to such a domain"));
		}
	}

	// -- Lifecycle + Cross-Project --

	private void validateLifecycle(ClaimOccurrence occurrence, List<DocumentationCandidateValidationIssue> issues) {
		String claimType = occurrence.claim().path("claimType").asString(null);
		Set<String> authorityKeys =
				streamArray(occurrence.claim().path("authorityKeys")).map(JsonNode::asString).collect(Collectors.toSet());

		if (QA_STATUS_CLAIM_TYPE.equals(claimType) && !authorityKeys.contains(QA_GATE_AUTHORITY_KEY)) {
			issues.add(new DocumentationCandidateValidationIssue(
					"lifecycle", occurrence.ref(), "QA_STATUS claim must cite the QA gate authority key '" + QA_GATE_AUTHORITY_KEY + "'"));
		} else if (APPROVAL_STATUS_CLAIM_TYPE.equals(claimType) && !authorityKeys.contains(APPROVAL_CONTEXT_STATE_AUTHORITY_KEY)) {
			issues.add(new DocumentationCandidateValidationIssue(
					"lifecycle",
					occurrence.ref(),
					"APPROVAL_STATUS claim must cite the approval context-state authority key '" + APPROVAL_CONTEXT_STATE_AUTHORITY_KEY
							+ "' - no ApprovalRecord type exists in this codebase, so this is the only legitimate authority"));
		} else if (DEPLOYMENT_STATUS_CLAIM_TYPE.equals(claimType) && !authorityKeys.contains(DEPLOYMENT_CONTEXT_STATE_AUTHORITY_KEY)) {
			issues.add(new DocumentationCandidateValidationIssue(
					"lifecycle",
					occurrence.ref(),
					"DEPLOYMENT_STATUS claim must cite the deployment context-state authority key '" + DEPLOYMENT_CONTEXT_STATE_AUTHORITY_KEY + "'"));
		}
	}

	// -- Disclosure --

	private void validateDisclosureCoverage(
			JsonNode contextContent, List<ClaimOccurrence> claims, String primaryAudience, List<DocumentationCandidateValidationIssue> issues) {
		String expectedSection = DISCLOSURE_SECTION_BY_AUDIENCE.get(primaryAudience);

		for (JsonNode entry : contextContent.path("findingDisclosureView").path("entries")) {
			if (!"DISCLOSE".equals(entry.path("action").asString(null))) {
				continue; // structurally unreachable today - DocumentationFindingDisclosureEvaluator never emits anything else
			}
			String disclosureKey = entry.path("disclosureKey").asString(null);
			String findingAuthorityKey = entry.path("findingAuthorityKey").asString(null);

			boolean covered = claims.stream().anyMatch(occurrence -> {
				JsonNode claim = occurrence.claim();
				boolean hasDisclosureKey =
						streamArray(claim.path("disclosureKeys")).anyMatch(n -> n.asString().equals(disclosureKey));
				boolean hasAuthorityKey = streamArray(claim.path("authorityKeys")).anyMatch(n -> n.asString().equals(findingAuthorityKey));
				boolean inExpectedSection = expectedSection == null || expectedSection.equals(occurrence.sectionType());
				return hasDisclosureKey && hasAuthorityKey && inExpectedSection;
			});

			if (!covered) {
				issues.add(new DocumentationCandidateValidationIssue(
						"disclosure",
						"findingDisclosureView.entries",
						"disclosure entry '" + disclosureKey + "' (authorityKey '" + findingAuthorityKey + "') has no matching claim"
								+ (expectedSection != null ? " in section '" + expectedSection + "'" : "")));
			}
		}
	}

	// -- shared helpers --

	private JsonNode findRequiredDocument(JsonNode candidate, String expectedDocumentType) {
		JsonNode match = null;
		for (JsonNode document : candidate.path("documents")) {
			if (expectedDocumentType.equals(document.path("documentType").asString(null))) {
				if (match != null) {
					return null;
				}
				match = document;
			}
		}
		return match;
	}

	private Map<String, JsonNode> indexByField(JsonNode array, String field) {
		Map<String, JsonNode> byField = new LinkedHashMap<>();
		for (JsonNode node : array) {
			String key = node.path(field).asString(null);
			if (key != null) {
				byField.put(key, node);
			}
		}
		return byField;
	}

	private Stream<JsonNode> streamArray(JsonNode array) {
		Stream.Builder<JsonNode> builder = Stream.builder();
		array.forEach(builder::add);
		return builder.build();
	}
}
