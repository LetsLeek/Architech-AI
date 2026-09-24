package ai.architech.backend.core.documentation.context;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Deterministic finding-disclosure decisions for the {@code findingDisclosureView} object
 * {@code documentation-context.schema.json} requires (AIW-192) - wired into {@link
 * DocumentationContextAssembler} as the real source AIW-190 deliberately left unfilled.
 *
 * <p><b>Materiality mapping - a design synthesis, not spelled out verbatim anywhere in the frozen
 * package.</b> {@code documentation-policy.yaml}'s {@code findingDisclosure.customer}/{@code
 * .developer} blocks describe disclosure outcomes per finding-materiality-bucket ({@code
 * materialCustomerImpact}, {@code blockingOrUnresolvedEscalation}, {@code resolvedHistorical},
 * {@code notEvaluableMaterial}, {@code technicalOnlyNonmaterial}, {@code unmappedMaterialFallback}
 * for customer; {@code allTechnicallyRelevantCurrent}, {@code materialSecurityDetail}, {@code
 * resolvedHistorical}, {@code unmappedCurrentFallback} for developer) but never says how Core
 * decides which bucket a finding falls into. The grounded answer used here: {@link
 * PolicyEvaluation} - the already-persisted, deterministic {@code BLOCK|ALLOW|ESCALATE}
 * disposition {@code QAPolicyAggregator} (AIW-176) computed for every {@link CandidateFinding} at
 * QA time, matched by {@code subjectType == "CANDIDATE_FINDING" && subjectRef == finding.id}
 * (verified against {@code QAPolicyAggregator}'s own {@code SUBJECT_TYPE_FINDING} constant).
 *
 * <p><b>Customer audience</b> (only ever reached for a {@code gate == PASS} {@link QaResult}, per
 * AIW-189's preflight - and {@code QAPolicyAggregator}'s own gate logic guarantees a {@code PASS}
 * result has zero {@code BLOCK}-disposed findings, so the block paths below are a defensive,
 * fail-closed safety net for an inconsistent state, not an expected runtime path): every {@code
 * ALLOW}-disposed finding is treated as material and discloses ({@code materialCustomerImpact:
 * DISCLOSE}) - there is no further "technical-only, not customer-material" signal anywhere in this
 * codebase to implement {@code technicalOnlyNonmaterial: OMIT_IF_POLICY_MATCHES} against, so this
 * deliberately conservative V1 behaviour always discloses rather than silently omitting a real
 * limitation. A {@code BLOCK}/{@code ESCALATE} disposition, or a finding with no matching {@link
 * PolicyEvaluation} at all, throws {@link DocumentationFindingDisclosureBlockedException} -
 * matching {@code blockingOrUnresolvedEscalation}/{@code notEvaluableMaterial}/{@code
 * unmappedMaterialFallback: BLOCK_DOCUMENT} - and no context is ever assembled.
 *
 * <p><b>Developer audience</b>: every current-candidate finding discloses regardless of
 * disposition ({@code allTechnicallyRelevantCurrent: DISCLOSE}), including one with no matching
 * {@link PolicyEvaluation} ({@code unmappedCurrentFallback: DISCLOSE}) - the opposite,
 * fail-open default from customer's fail-closed one. This asymmetry is real and intentional in
 * the source policy (matches {@code TECHNICAL_HANDOVER} also accepting gate {@code HOLD}, where
 * blocking findings can legitimately still be present) - not "fixed" here.
 *
 * <p><b>{@code action: OMIT} never appears</b>: {@code documentation-policy.yaml}'s {@code
 * onlyCurrentCandidateFindings: true} is satisfied structurally - only {@link
 * CandidateFinding}s for the exact Candidate under documentation are ever queried, never a prior
 * remediated Candidate's findings, so {@code resolvedHistorical: OMIT} has nothing to apply to
 * here.
 *
 * <p><b>No new deduplication work</b>: {@code core.qa.invariants.FindingFingerprintNormalizer}/
 * {@code FindingDeduplicationValidator} (AIW-174) deduplicate findings produced within one QA
 * execution, before they are ever persisted. The finding set this evaluator reads is already
 * exactly that deduplicated, persisted set - there is nothing left for this ticket to
 * deduplicate.
 */
@Component
public class DocumentationFindingDisclosureEvaluator {

	private static final String CUSTOMER_AUDIENCE = "CUSTOMER";
	private static final String SUBJECT_TYPE_FINDING = "CANDIDATE_FINDING";
	private static final String DISPOSITION_BLOCK = "BLOCK";
	private static final String DISPOSITION_ESCALATE = "ESCALATE";
	private static final String CUSTOMER_REASON_CODE = "MATERIAL_CUSTOMER_IMPACT";
	private static final String DEVELOPER_REASON_CODE = "ALL_TECHNICALLY_RELEVANT_CURRENT";

	private final CandidateFindingRepository candidateFindingRepository;
	private final PolicyEvaluationRepository policyEvaluationRepository;
	private final ObjectMapper objectMapper;

	DocumentationFindingDisclosureEvaluator(
			CandidateFindingRepository candidateFindingRepository,
			PolicyEvaluationRepository policyEvaluationRepository,
			ObjectMapper objectMapper) {
		this.candidateFindingRepository = candidateFindingRepository;
		this.policyEvaluationRepository = policyEvaluationRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * {@code entries} is the raw {@code findingDisclosureView.entries} array; {@code
	 * authorityCatalogEntries} are the matching {@code QA_FINDING} catalog entries every {@code
	 * findingAuthorityKey} above must resolve against ({@code validators/candidate/DETERMINISTIC.md}'s
	 * "Keys" rule) - the caller folds these into the same {@code authorityCatalog} array the
	 * provenance-catalog facts already populate, before audience minimization/secret scanning run
	 * over the combined result. {@code anyDisclosed} tells the caller whether to emit the
	 * {@code NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED} context-state.
	 */
	public record DocumentationFindingDisclosureResult(ArrayNode entries, ArrayNode authorityCatalogEntries, boolean anyDisclosed) {}

	public DocumentationFindingDisclosureResult evaluate(
			WebsiteImplementationCandidate candidate, QaResult qaResult, String primaryAudience) {
		List<CandidateFinding> findings = candidateFindingRepository.findByTestedCandidateIdOrderByCreatedAtAsc(candidate.getId());
		Map<String, String> dispositionByFindingId = policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())
				.stream()
				.filter(pe -> SUBJECT_TYPE_FINDING.equals(pe.getSubjectType()))
				.collect(Collectors.toMap(PolicyEvaluation::getSubjectRef, PolicyEvaluation::getDisposition, (first, second) -> first));

		ArrayNode entries = objectMapper.createArrayNode();
		ArrayNode catalogEntries = objectMapper.createArrayNode();
		String candidateId = candidate.getId().toString();
		boolean isCustomer = CUSTOMER_AUDIENCE.equals(primaryAudience);

		for (CandidateFinding finding : findings) {
			String findingId = finding.getId().toString();
			Optional<String> disposition = Optional.ofNullable(dispositionByFindingId.get(findingId));

			if (isCustomer) {
				if (disposition.isEmpty()) {
					throw new DocumentationFindingDisclosureBlockedException(
							"Finding " + findingId + " has no PolicyEvaluation for QA result " + qaResult.getId()
									+ " - customer documentation generation blocked (notEvaluableMaterial)");
				}
				if (DISPOSITION_BLOCK.equals(disposition.get()) || DISPOSITION_ESCALATE.equals(disposition.get())) {
					throw new DocumentationFindingDisclosureBlockedException(
							"Finding " + findingId + " has disposition " + disposition.get()
									+ " - customer documentation generation blocked (blockingOrUnresolvedEscalation)");
				}
				addDiscloseEntry(entries, catalogEntries, finding, candidateId, CUSTOMER_AUDIENCE, CUSTOMER_REASON_CODE);
			} else {
				addDiscloseEntry(entries, catalogEntries, finding, candidateId, "DEVELOPER", DEVELOPER_REASON_CODE);
			}
		}

		return new DocumentationFindingDisclosureResult(entries, catalogEntries, entries.size() > 0);
	}

	private void addDiscloseEntry(
			ArrayNode entries, ArrayNode catalogEntries, CandidateFinding finding, String candidateId, String audience, String reasonCode) {
		String findingId = finding.getId().toString();
		String keySuffix = sanitize(findingId);
		String disclosureKey = "DISC_" + keySuffix;
		String authorityKey = "AUTH_FINDING_" + keySuffix;

		ObjectNode entry = objectMapper.createObjectNode();
		entry.put("disclosureKey", disclosureKey);
		entry.put("findingAuthorityKey", authorityKey);
		entry.put("action", "DISCLOSE");
		entry.put("audience", audience);
		entry.put("reasonCode", reasonCode);
		entry.set("materialImpactFactKeys", objectMapper.createArrayNode());
		entry.put("currentCandidateRef", candidateId);
		entries.add(entry);

		ObjectNode locator = objectMapper.createObjectNode();
		locator.put("kind", "OBJECT_ID");
		locator.put("value", findingId);

		ObjectNode authorityRef = objectMapper.createObjectNode();
		authorityRef.put("authorityDomain", "QA_FINDING");
		authorityRef.put("artifactType", "QA_RESULT");
		authorityRef.put("artifactVersionRef", finding.getQaResultId().toString());
		authorityRef.set("locator", locator);

		ObjectNode catalogEntry = objectMapper.createObjectNode();
		catalogEntry.put("key", authorityKey);
		catalogEntry.set("authorityRef", authorityRef);
		catalogEntry.set("safeFactKeys", objectMapper.createArrayNode());
		catalogEntries.add(catalogEntry);
	}

	private String sanitize(String uuidString) {
		return uuidString.toUpperCase(Locale.ROOT).replace("-", "_");
	}
}
