package ai.architech.backend.core.documentation.context;

import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.documentation.profiles.DeterministicReportSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.SemanticDocumentSpec;
import ai.architech.backend.core.qa.QaResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Assembles and freezes one {@code documentation-context.schema.json} payload (AIW-190) -
 * combines AIW-189's {@link DocumentationAuthorityAdapter} output with a deliberately bounded
 * {@code authorityCatalog}/{@code resolvedFacts} "provenance catalog" and {@code MISSING_AUTHORITY}
 * /{@code SECTION_AUTHORITY_UNAVAILABLE} context-state proofs, then persists the result as an
 * immutable {@link DocumentationContext} row.
 *
 * <p>Builds raw JSON directly via {@link ObjectMapper}/{@link ObjectNode} rather than an
 * intermediate tree of typed Java records - matching {@code DeveloperExecutionInputAssembler}'s
 * own established idiom for schema-shaped payload assembly in this codebase. This is a deliberate
 * choice made after AIW-189 shipped a typed-record mismatch against the real schema shape
 * (undetected until this ticket actually needed to validate a payload against {@link
 * ai.architech.backend.core.validation.DocumentationSchemaRegistry}): raw JSON construction,
 * checked directly against the real schema in tests, cannot silently drift from what the schema
 * actually requires the way a hand-typed intermediate shape can.
 *
 * <p><b>What this class deliberately does not compute</b> (mirrors {@code
 * QAExecutionPreflightValidator}'s own "what this class deliberately does not validate yet"
 * idiom): {@code findingDisclosureView}, {@code contextIssues} and {@code securityProjection} are
 * all schema-required top-level fields, but none of them are computed here - they are accepted as
 * caller-supplied parameters. {@code securityProjection.redactionApplied}/{@code
 * audienceMinimizationApplied} are both schema {@code const: true}: a context can only ever be
 * schema-valid once real redaction has actually happened, which is AIW-191's job (not built yet).
 * {@code findingDisclosureView} entries require the real {@code FindingDisclosureEvaluator}
 * (AIW-192, not built yet) - not guessed at here. Similarly, the {@code
 * NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED} context-state kind is deliberately not produced by
 * this class for the same reason: it depends on that same evaluator's real output. No production
 * caller should invoke {@link #assemble} with a fabricated {@code true} security projection until
 * AIW-191 exists to legitimately produce one - only tests do that today, to prove the rest of the
 * assembly is schema-valid in isolation.
 *
 * <p>The {@code authorityCatalog}/{@code resolvedFacts} "provenance catalog" built here is
 * intentionally bounded to five verified, schema-grounded facts (business name and opening-hours
 * count from a project's canonical {@code customer-profile} artifact, per-binding functional
 * binding state, the QA gate outcome, and the Candidate's implementation summary) - not an
 * exhaustive extraction of every upstream field. Extending this catalog with more upstream facts
 * (selected pages, actual routes, integration contract terms, per-requirement text, ...) is real,
 * separate follow-up work: this codebase's upstream artifact content schemas support it, but
 * deciding exactly which additional fields are safe/useful to expose is a genuine design decision
 * this ticket does not take on speculatively.
 */
@Component
public class DocumentationContextAssembler {

	private static final String SCHEMA_VERSION = "1.0.0";
	private static final String SELECTED_SECTION_UNAVAILABLE_MARKER = "SCOPED_SECTION_UNAVAILABLE_IF_APPLICABLE";

	private final DocumentationAuthorityAdapter authorityAdapter;
	private final DocumentationContextRepository contextRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	DocumentationContextAssembler(
			DocumentationAuthorityAdapter authorityAdapter,
			DocumentationContextRepository contextRepository,
			ArtifactVersionRepository artifactVersionRepository,
			ObjectMapper objectMapper) {
		this.authorityAdapter = authorityAdapter;
		this.contextRepository = contextRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	public DocumentationContext assemble(
			UUID projectId,
			WebsiteImplementationCandidate candidate,
			QaResult qaResult,
			DocumentationProfile profile,
			String targetLocale,
			JsonNode findingDisclosureView,
			List<JsonNode> contextIssues,
			JsonNode securityProjection) {
		UUID contextId = UUID.randomUUID();

		DocumentationAuthoritySnapshot authoritySnapshot = authorityAdapter.buildAuthoritySnapshot(projectId, candidate, qaResult);
		DocumentationQaState qaState = authorityAdapter.buildQaState(candidate, qaResult);

		ArrayNode authorityCatalog = objectMapper.createArrayNode();
		ArrayNode resolvedFacts = objectMapper.createArrayNode();
		addFacts(authorityCatalog, resolvedFacts, candidate, qaResult, authoritySnapshot);

		ArrayNode contextStates = objectMapper.createArrayNode();
		addContextStates(contextStates, authorityCatalog, contextId, candidate, profile, authoritySnapshot);

		ObjectNode root = objectMapper.createObjectNode();
		root.put("schemaVersion", SCHEMA_VERSION);
		root.put("contextId", contextId.toString());
		root.put("contextVersion", 1);
		root.put("projectRef", projectId.toString());
		root.put("profileRef", profile.ref());
		root.put("policyRef", profile.findingPolicyRef());
		root.put("primaryAudience", profile.primaryAudience());
		root.put("targetLocale", targetLocale);
		root.set("authoritySnapshot", authoritySnapshotNode(authoritySnapshot));
		root.set("qaState", qaStateNode(qaState));
		root.set("profileExecutionContract", profileExecutionContractNode(profile));
		root.set("authorityCatalog", authorityCatalog);
		root.set("resolvedFacts", resolvedFacts);
		root.set("contextStates", contextStates);
		root.set("findingDisclosureView", findingDisclosureView);
		ArrayNode contextIssuesNode = objectMapper.createArrayNode();
		contextIssues.forEach(contextIssuesNode::add);
		root.set("contextIssues", contextIssuesNode);
		root.set("securityProjection", securityProjection);
		root.put("createdAt", Instant.now().toString());

		String contentJson = objectMapper.writeValueAsString(root);
		DocumentationContext context =
				new DocumentationContext(contextId, projectId, candidate.getId(), qaResult.getId(), profile.ref(), 1, contentJson);
		return contextRepository.save(context);
	}

	private ObjectNode authoritySnapshotNode(DocumentationAuthoritySnapshot snapshot) {
		ObjectNode node = objectMapper.createObjectNode();
		snapshot.customerProfileRef().ifPresent(ref -> node.set("customerProfileRef", artifactRefNode(ref)));
		node.set("websiteRequirementsRef", artifactRefNode(snapshot.websiteRequirementsRef()));
		node.set("selectedSourceDesignRef", artifactRefNode(snapshot.selectedSourceDesignRef()));
		node.set("implementationCandidateRef", artifactRefNode(snapshot.implementationCandidateRef()));
		node.set("qaResultRef", artifactRefNode(snapshot.qaResultRef()));
		snapshot.selectionDecisionRef().ifPresent(ref -> node.set("selectionDecisionRef", artifactRefNode(ref)));
		snapshot.approvalRecordRef().ifPresent(ref -> node.set("approvalRecordRef", artifactRefNode(ref)));
		snapshot.deploymentRecordRef().ifPresent(ref -> node.set("deploymentRecordRef", artifactRefNode(ref)));
		return node;
	}

	private ObjectNode qaStateNode(DocumentationQaState qaState) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("qaResultRef", qaState.qaResultRef());
		node.put("candidateRef", qaState.candidateRef());
		node.put("qaProfile", qaState.qaProfile());
		node.put("gate", qaState.gate());
		return node;
	}

	private ObjectNode profileExecutionContractNode(DocumentationProfile profile) {
		SemanticDocumentSpec semanticDocument = profile.semanticDocuments().get(0);

		ObjectNode node = objectMapper.createObjectNode();
		node.put("documentType", semanticDocument.documentType());

		ArrayNode sectionTypes = objectMapper.createArrayNode();
		semanticDocument.sections().forEach(section -> sectionTypes.add(section.sectionType()));
		node.set("sectionTypes", sectionTypes);

		ArrayNode requiredReportTypes = objectMapper.createArrayNode();
		profile.deterministicReports().stream()
				.filter(DeterministicReportSpec::required)
				.map(DeterministicReportSpec::reportType)
				.forEach(requiredReportTypes::add);
		node.set("requiredReportTypes", requiredReportTypes);

		ArrayNode allowedClaimTypes = objectMapper.createArrayNode();
		profile.allowedClaimTypes().forEach(allowedClaimTypes::add);
		node.set("allowedClaimTypes", allowedClaimTypes);

		return node;
	}

	// -- authorityCatalog / resolvedFacts: the bounded, five-item provenance catalog --

	private void addFacts(
			ArrayNode authorityCatalog,
			ArrayNode resolvedFacts,
			WebsiteImplementationCandidate candidate,
			QaResult qaResult,
			DocumentationAuthoritySnapshot authoritySnapshot) {
		authoritySnapshot.customerProfileRef().ifPresent(customerProfileRef -> {
			JsonNode content = customerProfileContent(customerProfileRef);
			addBusinessNameFact(authorityCatalog, resolvedFacts, customerProfileRef, content);
			addOpeningHoursFact(authorityCatalog, resolvedFacts, customerProfileRef, content);
		});
		addFunctionalBindingFacts(authorityCatalog, resolvedFacts, candidate);
		addQaGateFact(authorityCatalog, resolvedFacts, qaResult);
		addImplementationSummaryFact(authorityCatalog, resolvedFacts, candidate);
	}

	private JsonNode customerProfileContent(DocumentationArtifactRef customerProfileRef) {
		UUID artifactVersionId = UUID.fromString(customerProfileRef.artifactVersionRef());
		ArtifactVersion version = artifactVersionRepository
				.findById(artifactVersionId)
				.orElseThrow(() -> new IllegalStateException(
						"customer-profile artifact version " + artifactVersionId + " referenced by the authority snapshot no longer exists"));
		return objectMapper.readTree(version.getContent());
	}

	private void addBusinessNameFact(
			ArrayNode authorityCatalog, ArrayNode resolvedFacts, DocumentationArtifactRef customerProfileRef, JsonNode content) {
		String key = "AUTH_BUSINESS_NAME";
		String factKey = "F_BUSINESS_NAME";
		authorityCatalog.add(catalogEntry(
				key,
				authorityRefNode("CUSTOMER_FACT", customerProfileRef.artifactType(), customerProfileRef.artifactVersionRef(), "OBJECT_ID", "business.name"),
				List.of(factKey)));

		String name = content.path("business").path("name").asString(null);
		if (name != null && !name.isBlank()) {
			resolvedFacts.add(knownStringFact(factKey, key, "CUSTOMER_FACT", "BUSINESS_NAME", name));
		} else {
			resolvedFacts.add(unknownFact(factKey, key, "CUSTOMER_FACT", "BUSINESS_NAME"));
		}
	}

	private void addOpeningHoursFact(
			ArrayNode authorityCatalog, ArrayNode resolvedFacts, DocumentationArtifactRef customerProfileRef, JsonNode content) {
		String key = "AUTH_OPENING_HOURS";
		String factKey = "F_OPENING_HOURS";
		authorityCatalog.add(catalogEntry(
				key,
				authorityRefNode("CUSTOMER_FACT", customerProfileRef.artifactType(), customerProfileRef.artifactVersionRef(), "OBJECT_ID", "openingHours"),
				List.of(factKey)));

		JsonNode openingHours = content.path("openingHours");
		int count = openingHours.isArray() ? openingHours.size() : 0;
		if (count > 0) {
			resolvedFacts.add(knownNumberFact(factKey, key, "CUSTOMER_FACT", "OPENING_HOURS", count));
		} else {
			resolvedFacts.add(unknownFact(factKey, key, "CUSTOMER_FACT", "OPENING_HOURS"));
		}
	}

	private void addFunctionalBindingFacts(ArrayNode authorityCatalog, ArrayNode resolvedFacts, WebsiteImplementationCandidate candidate) {
		JsonNode bindings = objectMapper.readTree(candidate.getFunctionalBindings());
		if (!bindings.isArray()) {
			return;
		}
		String candidateId = candidate.getId().toString();
		int index = 0;
		for (JsonNode binding : bindings) {
			String requirementRef = binding.path("requirementRef").asString(null);
			String status = binding.path("status").asString(null);
			if (requirementRef == null || status == null) {
				continue;
			}
			String suffix = sanitizeKeySuffix(requirementRef, index++);
			String key = "AUTH_BINDING_STATE_" + suffix;
			String factKey = "F_BINDING_STATE_" + suffix;
			authorityCatalog.add(catalogEntry(
					key,
					authorityRefNode("FUNCTIONAL_BINDING", "WEBSITE_IMPLEMENTATION_CANDIDATE", candidateId, "OBJECT_ID", requirementRef),
					List.of(factKey)));
			resolvedFacts.add(knownStringFact(factKey, key, "FUNCTIONAL_BINDING", "BINDING_STATE", status));
		}
	}

	private void addQaGateFact(ArrayNode authorityCatalog, ArrayNode resolvedFacts, QaResult qaResult) {
		String key = "AUTH_FULL_RELEASE_GATE";
		String factKey = "F_FULL_RELEASE_GATE";
		authorityCatalog.add(catalogEntry(
				key, authorityRefNode("QA_EVALUATION", "QA_RESULT", qaResult.getId().toString(), "OBJECT_ID", "gate"), List.of(factKey)));
		resolvedFacts.add(knownStringFact(factKey, key, "QA_EVALUATION", "FULL_RELEASE_GATE", qaResult.getGateOutcome()));
	}

	private void addImplementationSummaryFact(ArrayNode authorityCatalog, ArrayNode resolvedFacts, WebsiteImplementationCandidate candidate) {
		String key = "AUTH_IMPLEMENTATION_SUMMARY";
		String factKey = "F_IMPLEMENTATION_SUMMARY";
		String candidateId = candidate.getId().toString();
		authorityCatalog.add(catalogEntry(
				key,
				authorityRefNode("IMPLEMENTATION", "WEBSITE_IMPLEMENTATION_CANDIDATE", candidateId, "OBJECT_ID", "implementationSummary"),
				List.of(factKey)));

		String summary = candidate.getImplementationSummary();
		// safe-value.schema.json's STRING variant caps at 4000 chars - truncate rather than let
		// schema validation fail on an otherwise-legitimate, just-too-long summary.
		String safeSummary = summary.length() > 4000 ? summary.substring(0, 4000) : summary;
		resolvedFacts.add(knownStringFact(factKey, key, "IMPLEMENTATION", "IMPLEMENTATION_SUMMARY", safeSummary));
	}

	// -- contextStates: MISSING_AUTHORITY / SECTION_AUTHORITY_UNAVAILABLE proofs --

	private void addContextStates(
			ArrayNode contextStates,
			ArrayNode authorityCatalog,
			UUID contextId,
			WebsiteImplementationCandidate candidate,
			DocumentationProfile profile,
			DocumentationAuthoritySnapshot authoritySnapshot) {
		ObjectNode candidateScopeRef = artifactRefRaw("WEBSITE_IMPLEMENTATION_CANDIDATE", candidate.getId().toString());

		for (String optionalRoot : profile.optionalRoots()) {
			Optional<String> affectedDomain = missingAuthorityDomainFor(optionalRoot, authoritySnapshot);
			affectedDomain.ifPresent(domain -> addMissingAuthorityState(
					contextStates, authorityCatalog, contextId, optionalRoot, domain, candidateScopeRef));
		}

		profile.composerDeterministicBlocks().forEach((sectionType, markers) -> {
			if (markers.contains(SELECTED_SECTION_UNAVAILABLE_MARKER)) {
				addSectionAuthorityUnavailableState(contextStates, authorityCatalog, contextId, sectionType, candidateScopeRef);
			}
		});
	}

	/**
	 * {@code SELECTION_DECISION}/{@code APPROVAL_RECORD}/{@code DEPLOYMENT_RECORD} never resolve in
	 * this codebase today (no backing Java entity exists for any of the three) - always missing.
	 * {@code CUSTOMER_PROFILE} is the one genuinely conditional root: missing only when the adapter
	 * could not resolve a canonical {@code customer-profile} artifact for the project.
	 */
	private Optional<String> missingAuthorityDomainFor(String optionalRoot, DocumentationAuthoritySnapshot authoritySnapshot) {
		return switch (optionalRoot) {
			case "SELECTION_DECISION" -> Optional.of("SELECTION");
			case "APPROVAL_RECORD" -> Optional.of("APPROVAL");
			case "DEPLOYMENT_RECORD" -> Optional.of("DEPLOYMENT");
			case "CUSTOMER_PROFILE" -> authoritySnapshot.customerProfileRef().isEmpty() ? Optional.of("CUSTOMER_FACT") : Optional.empty();
			default -> Optional.empty();
		};
	}

	private void addMissingAuthorityState(
			ArrayNode contextStates,
			ArrayNode authorityCatalog,
			UUID contextId,
			String optionalRoot,
			String affectedDomain,
			ObjectNode candidateScopeRef) {
		String stateKey = "CTX_" + optionalRoot;
		String authorityKey = "AUTH_" + stateKey;

		ArrayNode scopeRefs = objectMapper.createArrayNode();
		scopeRefs.add(candidateScopeRef);

		ObjectNode state = objectMapper.createObjectNode();
		state.put("stateKey", stateKey);
		state.put("authorityKey", authorityKey);
		state.put("kind", "MISSING_AUTHORITY");
		state.put("affectedAuthorityDomain", affectedDomain);
		state.set("scopeRefs", scopeRefs);
		contextStates.add(state);

		authorityCatalog.add(contextStateCatalogEntry(authorityKey, contextId, stateKey));
	}

	private void addSectionAuthorityUnavailableState(
			ArrayNode contextStates, ArrayNode authorityCatalog, UUID contextId, String sectionType, ObjectNode candidateScopeRef) {
		String stateKey = "CTX_SECTION_" + sectionType;
		String authorityKey = "AUTH_" + stateKey;

		ArrayNode scopeRefs = objectMapper.createArrayNode();
		scopeRefs.add(candidateScopeRef);

		ObjectNode state = objectMapper.createObjectNode();
		state.put("stateKey", stateKey);
		state.put("authorityKey", authorityKey);
		state.put("kind", "SECTION_AUTHORITY_UNAVAILABLE");
		state.put("affectedAuthorityDomain", "CONTEXT_STATE");
		state.set("scopeRefs", scopeRefs);
		state.put("sectionType", sectionType);
		contextStates.add(state);

		authorityCatalog.add(contextStateCatalogEntry(authorityKey, contextId, stateKey));
	}

	private ObjectNode contextStateCatalogEntry(String key, UUID contextId, String stateKey) {
		return catalogEntry(
				key, authorityRefNode("CONTEXT_STATE", "DOCUMENTATION_CONTEXT", contextId.toString(), "CONTEXT_STATE", stateKey), List.of());
	}

	// -- shared raw-JSON builders --

	private ObjectNode artifactRefNode(DocumentationArtifactRef ref) {
		return artifactRefRaw(ref.artifactType(), ref.artifactVersionRef());
	}

	private ObjectNode artifactRefRaw(String artifactType, String artifactVersionRef) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("artifactType", artifactType);
		node.put("artifactVersionRef", artifactVersionRef);
		return node;
	}

	private ObjectNode authorityRefNode(
			String authorityDomain, String artifactType, String artifactVersionRef, String locatorKind, String locatorValue) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("authorityDomain", authorityDomain);
		node.put("artifactType", artifactType);
		node.put("artifactVersionRef", artifactVersionRef);
		ObjectNode locator = objectMapper.createObjectNode();
		locator.put("kind", locatorKind);
		locator.put("value", locatorValue);
		node.set("locator", locator);
		return node;
	}

	private ObjectNode catalogEntry(String key, ObjectNode authorityRef, List<String> safeFactKeys) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("key", key);
		node.set("authorityRef", authorityRef);
		ArrayNode keys = objectMapper.createArrayNode();
		safeFactKeys.forEach(keys::add);
		node.set("safeFactKeys", keys);
		return node;
	}

	private ObjectNode baseFact(String factKey, String authorityKey, String factDomain, String factType, String state) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("factKey", factKey);
		node.put("authorityKey", authorityKey);
		node.put("factDomain", factDomain);
		node.put("factType", factType);
		node.put("state", state);
		node.put("classification", "PUBLIC_DOCUMENTABLE");
		return node;
	}

	private ObjectNode knownStringFact(String factKey, String authorityKey, String factDomain, String factType, String value) {
		ObjectNode node = baseFact(factKey, authorityKey, factDomain, factType, "KNOWN");
		ObjectNode valueNode = objectMapper.createObjectNode();
		valueNode.put("valueType", "STRING");
		valueNode.put("value", value);
		node.set("value", valueNode);
		return node;
	}

	private ObjectNode knownNumberFact(String factKey, String authorityKey, String factDomain, String factType, int value) {
		ObjectNode node = baseFact(factKey, authorityKey, factDomain, factType, "KNOWN");
		ObjectNode valueNode = objectMapper.createObjectNode();
		valueNode.put("valueType", "NUMBER");
		valueNode.put("value", value);
		node.set("value", valueNode);
		return node;
	}

	private ObjectNode unknownFact(String factKey, String authorityKey, String factDomain, String factType) {
		return baseFact(factKey, authorityKey, factDomain, factType, "UNKNOWN");
	}

	private String sanitizeKeySuffix(String requirementRef, int index) {
		String sanitized = requirementRef.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
		List<String> parts = new ArrayList<>();
		parts.add(sanitized);
		parts.add(String.valueOf(index));
		return String.join("_", parts);
	}
}
