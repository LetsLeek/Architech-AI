package ai.architech.backend.core.validation;

import ai.architech.backend.core.documentation.profiles.DocumentSectionSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.SemanticDocumentSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The first validation gate a raw {@code semantic-documentation-candidate:v1} model output goes
 * through, immediately after {@code DocumentationGenerationRunner} (AIW-194) returns raw content -
 * before anything deeper. Mirrors {@link DeveloperResultValidator}'s own "strict schema validation
 * first, short-circuit on failure, then a dedicated identity check" idiom exactly.
 *
 * <p><b>Where this ticket's scope ends, deliberately</b>: {@code validators/candidate/
 * DETERMINISTIC.md}'s own "Structure" bullet also covers section *taxonomy/order* and "no
 * duplicate {@code claimKey} globally within candidate" - this class checks section *membership*
 * (is every section type one the profile actually declares), *required-coverage* (is every
 * profile-required section present) and the {@code allowEmptyAgentBlocks} contract, but never
 * section *order*, never walks into {@code blocks}/{@code claims} content, never checks
 * {@code claimKey} uniqueness, and never resolves an {@code authorityKey}/{@code disclosureKey}.
 * All of that is AIW-196's job (the same DETERMINISTIC.md spec's "Structure"/"Keys" bullets), kept
 * out of this class specifically so the two tickets don't duplicate the same check under two
 * different names.
 *
 * <p><b>Why "Identity" doesn't compare a self-declared ref, unlike {@link
 * DeveloperResultTargetIdentityValidator}</b>: {@code semantic-documentation-candidate.schema.json}
 * itself states "No Core-owned contextRef/audience/locale/canonical state or provenance IDs are
 * model-writable" - there is no {@code contextRef}/{@code profileRef} field on the candidate to
 * compare against anything. "Identity" here instead means: exactly one document in the 1-3-item
 * {@code documents} array has the one {@code documentType} the active profile actually requires
 * (both frozen profiles declare exactly one required {@code semanticDocuments} entry) - any
 * additional document of a different type is a rejection, not silently-ignored extra content
 * (rule {@code DOC-GEN-006}: "MUST NOT invent extra unsupported sections or document types").
 */
@Component
public class DocumentationCandidateStructureValidator {

	private static final String CANDIDATE_SCHEMA_URN = "urn:aiw:schema:documentation:semantic-documentation-candidate:v1";

	private final DocumentationSchemaRegistry schemaRegistry;
	private final ObjectMapper objectMapper;

	DocumentationCandidateStructureValidator(DocumentationSchemaRegistry schemaRegistry, ObjectMapper objectMapper) {
		this.schemaRegistry = schemaRegistry;
		this.objectMapper = objectMapper;
	}

	public DocumentationCandidateValidationResult validate(String candidateJson, DocumentationProfile profile) {
		SchemaValidationResult schemaResult = schemaRegistry.validate(CANDIDATE_SCHEMA_URN, candidateJson);
		if (!schemaResult.valid()) {
			List<DocumentationCandidateValidationIssue> issues = schemaResult.issues().stream()
					.map(issue -> new DocumentationCandidateValidationIssue("schema", issue.path(), issue.message()))
					.toList();
			return new DocumentationCandidateValidationResult(false, issues);
		}

		List<DocumentationCandidateValidationIssue> issues = new ArrayList<>();
		JsonNode candidate = objectMapper.readTree(candidateJson);
		SemanticDocumentSpec requiredDocumentSpec = profile.semanticDocuments().get(0);

		JsonNode identifiedDocument = validateIdentity(candidate, requiredDocumentSpec, issues);
		if (identifiedDocument != null) {
			validateStructure(identifiedDocument, requiredDocumentSpec, issues);
		}

		return issues.isEmpty() ? DocumentationCandidateValidationResult.passed() : new DocumentationCandidateValidationResult(false, issues);
	}

	/**
	 * Returns the one document matching the profile's required {@code documentType}, or {@code
	 * null} if identity failed (zero or more than one match) - callers must not attempt structure
	 * validation without a uniquely identified document to validate it against.
	 */
	private JsonNode validateIdentity(
			JsonNode candidate, SemanticDocumentSpec requiredDocumentSpec, List<DocumentationCandidateValidationIssue> issues) {
		String expectedDocumentType = requiredDocumentSpec.documentType();
		List<JsonNode> matches = new ArrayList<>();
		int index = 0;
		for (JsonNode document : candidate.path("documents")) {
			String documentType = document.path("documentType").asString(null);
			if (expectedDocumentType.equals(documentType)) {
				matches.add(document);
			} else {
				issues.add(new DocumentationCandidateValidationIssue(
						"identity",
						"documents[" + index + "].documentType",
						"unsupported document type '" + documentType + "' - profile requires exactly '" + expectedDocumentType + "'"));
			}
			index++;
		}

		if (matches.isEmpty()) {
			issues.add(new DocumentationCandidateValidationIssue(
					"identity", "documents", "no document of the required type '" + expectedDocumentType + "' is present"));
			return null;
		}
		if (matches.size() > 1) {
			issues.add(new DocumentationCandidateValidationIssue(
					"identity",
					"documents",
					"expected exactly one document of type '" + expectedDocumentType + "', found " + matches.size()));
			return null;
		}
		return matches.get(0);
	}

	private void validateStructure(
			JsonNode document, SemanticDocumentSpec requiredDocumentSpec, List<DocumentationCandidateValidationIssue> issues) {
		Map<String, DocumentSectionSpec> sectionSpecsByType = new LinkedHashMap<>();
		for (DocumentSectionSpec sectionSpec : requiredDocumentSpec.sections()) {
			sectionSpecsByType.put(sectionSpec.sectionType(), sectionSpec);
		}

		Set<String> seenSectionTypes = new LinkedHashSet<>();
		int index = 0;
		for (JsonNode section : document.path("sections")) {
			String sectionType = section.path("sectionType").asString(null);
			DocumentSectionSpec sectionSpec = sectionSpecsByType.get(sectionType);
			if (sectionSpec == null) {
				issues.add(new DocumentationCandidateValidationIssue(
						"structure",
						"documents[0].sections[" + index + "].sectionType",
						"unsupported section type '" + sectionType + "' is not declared by profile document type '"
								+ requiredDocumentSpec.documentType() + "'"));
				index++;
				continue;
			}
			seenSectionTypes.add(sectionType);

			boolean hasBlocks = section.path("blocks").isArray() && !section.path("blocks").isEmpty();
			if (!sectionSpec.allowEmptyAgentBlocks() && !hasBlocks) {
				issues.add(new DocumentationCandidateValidationIssue(
						"structure",
						"documents[0].sections[" + index + "].blocks",
						"section '" + sectionType + "' requires at least one block (allowEmptyAgentBlocks is false)"));
			}
			index++;
		}

		for (DocumentSectionSpec sectionSpec : requiredDocumentSpec.sections()) {
			if (sectionSpec.required() && !seenSectionTypes.contains(sectionSpec.sectionType())) {
				issues.add(new DocumentationCandidateValidationIssue(
						"structure", "documents[0].sections", "missing required section '" + sectionSpec.sectionType() + "'"));
			}
		}
	}
}
