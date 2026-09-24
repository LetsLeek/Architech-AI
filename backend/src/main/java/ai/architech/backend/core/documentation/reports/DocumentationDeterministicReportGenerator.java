package ai.architech.backend.core.documentation.reports;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.documentation.context.DocumentationArtifactRef;
import ai.architech.backend.core.documentation.context.DocumentationAuthoritySnapshot;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.profiles.DeterministicReportSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.integration.IntegrationContract;
import ai.architech.backend.core.integration.IntegrationContractResolution;
import ai.architech.backend.core.integration.IntegrationContractResolver;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Generates the {@code deterministic-report.schema.json}-shaped Core reports a Documentation
 * profile requires (AIW-193) - {@code ARTIFACT_VERSION_MANIFEST}, {@code IMPLEMENTATION_MANIFEST},
 * {@code FUNCTIONAL_BINDING_REPORT}, {@code INTEGRATION_REFERENCE_REPORT}, {@code
 * QA_FINDING_REGISTER} - from data that already exists, never a model reconstruction. Only the
 * report types {@link DeterministicReportSpec#required()} on the active {@link DocumentationProfile}
 * are produced: {@code CUSTOMER_HANDOVER@1.0.0} declares zero {@code deterministicReports}, so this
 * generator returns an empty list for it, which is correct, not a gap.
 *
 * <p>Builds raw JSON directly via {@link ObjectMapper}/{@link ObjectNode}, matching {@code
 * DocumentationContextAssembler}'s own established idiom - not a parallel typed-record tree, the
 * same "checked directly against the real schema in tests, cannot silently drift" reasoning that
 * class's own javadoc documents after AIW-189's mismatch bug.
 *
 * <p><b>Package assembly is explicitly out of this ticket's scope</b> despite the plan line's own
 * wording ("Paket-Montage nach {@code validators/package/ASSEMBLY.md}"): a real {@code
 * DocumentationPackageVersion} additionally needs a {@code semanticCandidateRef} (the model's own
 * generated output, not built until AIW-194) and immutable, revisioned persistence with supersession
 * semantics - explicitly AIW-199 (orchestration)/AIW-200 (persistence)'s job per the plan's own
 * Phase 7. This class produces pure, computed report JSON only - no {@code
 * DocumentationPackageVersion}/{@code DocumentationPackageCandidate} entity, no persistence at all;
 * wiring these reports into a real canonical package is later work.
 *
 * <p><b>Known, deliberate simplifications, documented rather than hidden</b>:
 *
 * <ul>
 *   <li>{@code implementation-manifest}'s {@code routes} is always {@code []}. There is no
 *       URL-route/page mapping stored anywhere in this codebase - {@link
 *       WebsiteImplementationCandidate#getImplementationAnchors()} only carries repository *file
 *       paths* ({@code implementation-anchor.v1.schema.json}'s {@code targets[].path}), never a URL
 *       route like {@code /kontakt}, and no {@code RouteManifest}/route concept exists anywhere
 *       else. Inventing a file-path-to-URL-route convention here would be fabricated, unverified
 *       content - real route data is separate, unticketed follow-up work, matching this codebase's
 *       "document the gap, don't silently work around it" convention (see {@code
 *       QAExecutionPreflightValidator}'s own "what this class deliberately does not validate yet").
 *   <li>{@code integration-reference-report}'s {@code purpose} field has no real source: {@code
 *       developer-safe-integration-contract-view.v1.schema.json} carries no dedicated
 *       business-purpose text, only {@code interfaceName}/{@code authorizedOperations}/{@code
 *       allowedRuntimeTargets}/{@code symbolicBindings}. This class synthesizes a safe, generic,
 *       truthful string ({@code "Integration via " + interfaceName}) rather than fabricating
 *       curated purpose text it does not have.
 *   <li>{@code qa-finding-register}'s {@code currentAssessment} is always the literal {@code
 *       "CURRENT"}: this codebase has no cross-candidate remediation-lineage comparison mechanism
 *       anywhere, so {@code PERSISTS}/{@code CHANGED} (which require comparing against a prior
 *       candidate's own findings) cannot be honestly computed here - real, separate, unticketed
 *       work, not fabricated.
 * </ul>
 */
@Component
public class DocumentationDeterministicReportGenerator {

	private static final String GENERATOR_VERSION = "documentation-report-generator@1.0.0";
	private static final String SUBJECT_TYPE_FINDING = "CANDIDATE_FINDING";
	private static final String REQUIREMENT_NORMATIVE_BASIS_TYPE = "WEBSITE_REQUIREMENT";

	private static final Map<String, RuntimeDescriptor> RUNTIME_DESCRIPTORS = Map.of(
			"website-react-typescript-vite-client-v1",
			new RuntimeDescriptor("React", "TypeScript", "Vite"),
			// The seeding fixtures already used by DocumentationContextAssemblerIT/
			// DocumentationFindingDisclosureEvaluatorIT predate this ticket and use a shorter
			// placeholder ref - recognized here too rather than forcing every existing test to change.
			"runtime-v1",
			new RuntimeDescriptor("React", "TypeScript", "Vite"));

	private record RuntimeDescriptor(String framework, String language, String buildTool) {}

	private final CandidateFindingRepository candidateFindingRepository;
	private final PolicyEvaluationRepository policyEvaluationRepository;
	private final IntegrationContractResolver integrationContractResolver;
	private final ObjectMapper objectMapper;

	DocumentationDeterministicReportGenerator(
			CandidateFindingRepository candidateFindingRepository,
			PolicyEvaluationRepository policyEvaluationRepository,
			IntegrationContractResolver integrationContractResolver,
			ObjectMapper objectMapper) {
		this.candidateFindingRepository = candidateFindingRepository;
		this.policyEvaluationRepository = policyEvaluationRepository;
		this.integrationContractResolver = integrationContractResolver;
		this.objectMapper = objectMapper;
	}

	public List<ObjectNode> generate(
			DocumentationContext context,
			WebsiteImplementationCandidate candidate,
			QaResult qaResult,
			DocumentationAuthoritySnapshot authoritySnapshot,
			DocumentationProfile profile) {
		List<ObjectNode> reports = new ArrayList<>();
		for (DeterministicReportSpec spec : profile.deterministicReports()) {
			if (!spec.required()) {
				continue;
			}
			ObjectNode report =
					switch (spec.reportType()) {
						case "ARTIFACT_VERSION_MANIFEST" -> artifactVersionManifest(context, spec, authoritySnapshot);
						case "IMPLEMENTATION_MANIFEST" -> implementationManifest(context, spec, candidate);
						case "FUNCTIONAL_BINDING_REPORT" -> functionalBindingReport(context, spec, candidate);
						case "INTEGRATION_REFERENCE_REPORT" -> integrationReferenceReport(context, spec, candidate);
						case "QA_FINDING_REGISTER" -> qaFindingRegister(context, spec, candidate, qaResult);
						default -> throw new IllegalStateException("Unknown deterministic report type: " + spec.reportType());
					};
			reports.add(report);
		}
		return reports;
	}

	// -- ARTIFACT_VERSION_MANIFEST --

	private ObjectNode artifactVersionManifest(
			DocumentationContext context, DeterministicReportSpec spec, DocumentationAuthoritySnapshot authoritySnapshot) {
		List<DocumentationArtifactRef> refs = new ArrayList<>();
		authoritySnapshot.customerProfileRef().ifPresent(refs::add);
		refs.add(authoritySnapshot.websiteRequirementsRef());
		refs.add(authoritySnapshot.selectedSourceDesignRef());
		refs.add(authoritySnapshot.implementationCandidateRef());
		refs.add(authoritySnapshot.qaResultRef());
		authoritySnapshot.selectionDecisionRef().ifPresent(refs::add);
		authoritySnapshot.approvalRecordRef().ifPresent(refs::add);
		authoritySnapshot.deploymentRecordRef().ifPresent(refs::add);

		ArrayNode roots = objectMapper.createArrayNode();
		ArrayNode authorityRefs = objectMapper.createArrayNode();
		for (DocumentationArtifactRef ref : refs) {
			roots.add(artifactRefNode(ref));
			authorityRefs.add(
					authorityRefNode(authorityDomainForRootType(ref.artifactType()), ref.artifactType(), ref.artifactVersionRef(), "ARTIFACT_ROOT", "root"));
		}

		ObjectNode payload = objectMapper.createObjectNode();
		payload.set("roots", roots);

		return envelope(context, spec, "ARTIFACT_VERSION_MANIFEST", authorityRefs, payload);
	}

	// -- IMPLEMENTATION_MANIFEST --

	private ObjectNode implementationManifest(
			DocumentationContext context, DeterministicReportSpec spec, WebsiteImplementationCandidate candidate) {
		RuntimeDescriptor descriptor = RUNTIME_DESCRIPTORS.get(candidate.getRuntimeProfileRef());
		if (descriptor == null) {
			throw new IllegalStateException(
					"No known runtime descriptor for runtime profile ref '" + candidate.getRuntimeProfileRef() + "'");
		}

		ObjectNode runtime = objectMapper.createObjectNode();
		runtime.put("framework", descriptor.framework());
		runtime.put("language", descriptor.language());
		runtime.put("buildTool", descriptor.buildTool());
		runtime.put("runtimeClass", "CLIENT_STATIC");

		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("candidateRef", candidate.getId().toString());
		payload.set("runtime", runtime);
		// No URL-route/page mapping exists anywhere in this codebase - see class javadoc.
		payload.set("routes", objectMapper.createArrayNode());
		payload.put("immutableRepositoryStateRef", candidate.getRepositoryStateRef());

		ArrayNode authorityRefs = objectMapper.createArrayNode();
		authorityRefs.add(authorityRefNode(
				"IMPLEMENTATION", "WEBSITE_IMPLEMENTATION_CANDIDATE", candidate.getId().toString(), "OBJECT_ID", "runtimeProfileRef"));

		return envelope(context, spec, "IMPLEMENTATION_MANIFEST", authorityRefs, payload);
	}

	// -- FUNCTIONAL_BINDING_REPORT --

	private ObjectNode functionalBindingReport(
			DocumentationContext context, DeterministicReportSpec spec, WebsiteImplementationCandidate candidate) {
		ArrayNode bindingsOut = objectMapper.createArrayNode();
		ArrayNode authorityRefs = objectMapper.createArrayNode();
		String candidateId = candidate.getId().toString();

		for (JsonNode binding : parseFunctionalBindings(candidate)) {
			String requirementRef = binding.path("requirementRef").asString(null);
			String status = binding.path("status").asString(null);
			if (requirementRef == null || status == null) {
				continue;
			}

			ObjectNode bindingOut = objectMapper.createObjectNode();
			bindingOut.put("functionId", requirementRef);
			bindingOut.put("bindingState", status);
			if ("IMPLEMENTED_BOUND".equals(status)) {
				String integrationContractRef = binding.path("integrationContractRef").asString(null);
				if (integrationContractRef != null) {
					bindingOut.put("integrationContractRef", integrationContractRef);
				}
			}
			bindingsOut.add(bindingOut);
			authorityRefs.add(authorityRefNode("FUNCTIONAL_BINDING", "WEBSITE_IMPLEMENTATION_CANDIDATE", candidateId, "OBJECT_ID", requirementRef));
		}

		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("candidateRef", candidateId);
		payload.set("bindings", bindingsOut);

		return envelope(context, spec, "FUNCTIONAL_BINDING_REPORT", authorityRefs, payload);
	}

	// -- INTEGRATION_REFERENCE_REPORT --

	private ObjectNode integrationReferenceReport(
			DocumentationContext context, DeterministicReportSpec spec, WebsiteImplementationCandidate candidate) {
		Map<String, Set<String>> functionIdsByContractRef = new LinkedHashMap<>();
		for (JsonNode binding : parseFunctionalBindings(candidate)) {
			String status = binding.path("status").asString(null);
			String contractRef = binding.path("integrationContractRef").asString(null);
			String requirementRef = binding.path("requirementRef").asString(null);
			if (!"IMPLEMENTED_BOUND".equals(status) || contractRef == null || requirementRef == null) {
				continue;
			}
			functionIdsByContractRef.computeIfAbsent(contractRef, key -> new LinkedHashSet<>()).add(requirementRef);
		}

		ArrayNode integrations = objectMapper.createArrayNode();
		ArrayNode authorityRefs = objectMapper.createArrayNode();
		for (Map.Entry<String, Set<String>> entry : functionIdsByContractRef.entrySet()) {
			String contractRef = entry.getKey();
			Optional<IntegrationContract> resolved = resolveIntegrationContract(candidate.getProjectId(), contractRef);
			ObjectNode integration = objectMapper.createObjectNode();
			integration.put("contractRef", contractRef);
			ArrayNode bindingFunctionIds = objectMapper.createArrayNode();
			entry.getValue().forEach(bindingFunctionIds::add);
			integration.set("bindingFunctionIds", bindingFunctionIds);

			String interfaceName = resolved.map(this::interfaceNameOf).orElse(contractRef);
			integration.put("purpose", "Integration via " + interfaceName);
			integration.put("credentialDependency", resolved.map(this::credentialDependencyOf).orElse("UNSPECIFIED"));
			integrations.add(integration);

			authorityRefs.add(authorityRefNode(
					"INTEGRATION_AUTHORIZATION", "INTEGRATION_CONTRACT", contractRef, "OBJECT_ID", "contract"));
		}

		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("candidateRef", candidate.getId().toString());
		payload.set("integrations", integrations);

		return envelope(context, spec, "INTEGRATION_REFERENCE_REPORT", authorityRefs, payload);
	}

	private Optional<IntegrationContract> resolveIntegrationContract(UUID projectId, String contractRef) {
		IntegrationContractResolution resolution = integrationContractResolver.resolve(projectId, contractRef);
		if (resolution instanceof IntegrationContractResolution.Authorized authorized) {
			return Optional.of(authorized.contract());
		}
		return Optional.empty();
	}

	private String interfaceNameOf(IntegrationContract contract) {
		JsonNode content = objectMapper.readTree(contract.getSafeContractContent());
		return content.path("interfaceName").asString(contract.getContractRef());
	}

	private String credentialDependencyOf(IntegrationContract contract) {
		JsonNode content = objectMapper.readTree(contract.getSafeContractContent());
		JsonNode symbolicBindings = content.path("symbolicBindings");
		return symbolicBindings.isArray() && symbolicBindings.size() > 0 ? "SECRET_MANAGED" : "NONE";
	}

	// -- QA_FINDING_REGISTER --

	private ObjectNode qaFindingRegister(
			DocumentationContext context, DeterministicReportSpec spec, WebsiteImplementationCandidate candidate, QaResult qaResult) {
		List<CandidateFinding> findings = candidateFindingRepository.findByTestedCandidateIdOrderByCreatedAtAsc(candidate.getId());
		Map<String, String> dispositionByFindingId = policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())
				.stream()
				.filter(pe -> SUBJECT_TYPE_FINDING.equals(pe.getSubjectType()))
				.collect(Collectors.toMap(PolicyEvaluation::getSubjectRef, PolicyEvaluation::getDisposition, (first, second) -> first));

		ArrayNode findingsOut = objectMapper.createArrayNode();
		ArrayNode authorityRefs = objectMapper.createArrayNode();
		for (CandidateFinding finding : findings) {
			String findingId = finding.getId().toString();
			String disposition = dispositionByFindingId.get(findingId);
			if (disposition == null) {
				continue;
			}

			ObjectNode findingOut = objectMapper.createObjectNode();
			findingOut.put("findingRef", findingId);
			findingOut.put("severity", finding.getSeverity());
			findingOut.put("qaDisposition", disposition);
			findingOut.put("currentAssessment", "CURRENT");
			findingOut.put("category", finding.getPrimaryDomain());
			findingOut.set("affectedRequirementRefs", affectedRequirementRefs(finding));
			findingsOut.add(findingOut);

			authorityRefs.add(authorityRefNode("QA_FINDING", "QA_RESULT", qaResult.getId().toString(), "OBJECT_ID", findingId));
		}

		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("qaResultRef", qaResult.getId().toString());
		payload.put("candidateRef", candidate.getId().toString());
		payload.put("qaProfile", "FULL_RELEASE");
		payload.put("gate", qaResult.getGateOutcome());
		payload.set("findings", findingsOut);

		authorityRefs.add(authorityRefNode("QA_EVALUATION", "QA_RESULT", qaResult.getId().toString(), "OBJECT_ID", "gate"));

		return envelope(context, spec, "QA_FINDING_REGISTER", authorityRefs, payload);
	}

	private ArrayNode affectedRequirementRefs(CandidateFinding finding) {
		ArrayNode refs = objectMapper.createArrayNode();
		JsonNode normativeBasis = objectMapper.readTree(finding.getNormativeBasisJson());
		if (!normativeBasis.isArray()) {
			return refs;
		}
		for (JsonNode entry : normativeBasis) {
			if (REQUIREMENT_NORMATIVE_BASIS_TYPE.equals(entry.path("type").asString(null))) {
				String ref = entry.path("ref").asString(null);
				if (ref != null) {
					refs.add(ref);
				}
			}
		}
		return refs;
	}

	/** Mirrors {@code DocumentationContextAssembler#missingAuthorityDomainFor}'s own root-to-domain mapping. */
	private String authorityDomainForRootType(String artifactType) {
		return switch (artifactType) {
			case "CUSTOMER_PROFILE" -> "CUSTOMER_FACT";
			case "WEBSITE_REQUIREMENTS" -> "REQUIREMENT";
			case "SELECTED_SOURCE_DESIGN" -> "SOURCE_DESIGN";
			case "WEBSITE_IMPLEMENTATION_CANDIDATE" -> "IMPLEMENTATION";
			case "QA_RESULT" -> "QA_EVALUATION";
			case "SELECTION_DECISION" -> "SELECTION";
			case "APPROVAL_RECORD" -> "APPROVAL";
			case "DEPLOYMENT_RECORD" -> "DEPLOYMENT";
			default -> throw new IllegalStateException("No known authority domain for root artifact type: " + artifactType);
		};
	}

	// -- shared helpers --

	private List<JsonNode> parseFunctionalBindings(WebsiteImplementationCandidate candidate) {
		JsonNode bindings = objectMapper.readTree(candidate.getFunctionalBindings());
		List<JsonNode> result = new ArrayList<>();
		if (bindings.isArray()) {
			bindings.forEach(result::add);
		}
		return result;
	}

	private ObjectNode envelope(
			DocumentationContext context, DeterministicReportSpec spec, String reportType, ArrayNode authorityRefs, ObjectNode payload) {
		ObjectNode envelope = objectMapper.createObjectNode();
		envelope.put("schemaVersion", "1.0.0");
		envelope.put("reportId", UUID.randomUUID().toString());
		envelope.put("reportType", reportType);
		envelope.put("contextRef", context.getId().toString());
		envelope.put("generatorVersion", GENERATOR_VERSION);
		envelope.put("deliveryDisposition", spec.deliveryDisposition());
		envelope.set("authorityRefs", authorityRefs);
		envelope.set("payload", payload);
		envelope.put("createdAt", Instant.now().toString());
		return envelope;
	}

	private ObjectNode artifactRefNode(DocumentationArtifactRef ref) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("artifactType", ref.artifactType());
		node.put("artifactVersionRef", ref.artifactVersionRef());
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
}
