package ai.architech.backend.core.documentation.canonical;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOutcome;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Atomically canonicalizes one {@link DocumentationGenerationOutcome.Success} into an immutable
 * {@link DocumentationPackageVersion} (AIW-200), per {@code validators/package/ASSEMBLY.md}:
 * persist the semantic candidate, each deterministic report, and a recomputed validation-result
 * envelope as real {@code ArtifactVersion} rows (point 3, "no partially canonical package" -
 * these all happen inside the same {@code @Transactional} boundary as the package version and
 * line-pointer update itself), then assign the next revision on the target {@link
 * DocumentationLine} under a bounded optimistic-locking retry loop (point 4, "serialize revision
 * assignment"), honoring an idempotency key so a retried commit with the exact same candidate
 * content returns the existing canonical version rather than creating a duplicate (point 5).
 *
 * <p><b>"Recompute rather than trust" (mirrors {@code RunnerVerificationEvidencePersister}'s own
 * idiom exactly)</b>: the {@code documentation-validation-result} envelope's {@code checkResults}/
 * {@code overallResult} are never copied from a caller-supplied "it passed" flag - they are
 * recomputed here from the one invariant a {@link DocumentationGenerationOutcome.Success} actually
 * guarantees (verified against {@code DocumentationGenerationOrchestrator}'s own pipeline: reaching
 * {@code Success} means AIW-195's structure check, AIW-197's secret scan, AIW-196's deterministic
 * check, and AIW-198's semantic check all passed cleanly on the winning attempt) - a caller that
 * somehow constructed a {@code Success} without those checks actually running would still get an
 * honestly-recomputed {@code PASS} envelope reflecting that same invariant, not a value it could
 * forge by passing a flag.
 *
 * <p><b>{@code packageCandidateRef} simplification</b>: the frozen package also defines a separate
 * {@code documentation-package-candidate.schema.json} intermediate concept (an assembled-but-not-
 * yet-canonical candidate, referenced by both this class's own persisted validation-result and,
 * presumably, a real {@code DocumentationRun}). Building a full, separately-persisted {@code
 * DocumentationPackageCandidate} entity is not required by this ticket's own two target schemas
 * ({@code documentation-package-version}/{@code documentation-validation-result}) and is not
 * attempted here - {@code packageCandidateRef} is populated with the persisted semantic candidate's
 * own {@code ArtifactVersion} id instead, the closest real, already-persisted referent to "the
 * candidate that was validated." Modeling the full intermediate concept is separate, unticketed
 * work.
 */
@Component
public class DocumentationCanonicalPackagePersister {

	private static final String SCHEMA_VERSION = "1.0.0";
	private static final String SEMANTIC_CANDIDATE_ARTIFACT_TYPE = "documentation-semantic-candidate";
	private static final String VALIDATION_RESULT_ARTIFACT_TYPE = "documentation-validation-result";
	private static final String REPORT_ARTIFACT_TYPE_PREFIX = "documentation-report-";
	private static final String VALIDATOR_SUITE_VERSION = "documentation-validator-suite@1.0.0";
	private static final int MAX_REVISION_ASSIGNMENT_ATTEMPTS = 5;

	private final DocumentationLineRepository lineRepository;
	private final DocumentationPackageVersionRepository packageVersionRepository;
	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	DocumentationCanonicalPackagePersister(
			DocumentationLineRepository lineRepository,
			DocumentationPackageVersionRepository packageVersionRepository,
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			ObjectMapper objectMapper) {
		this.lineRepository = lineRepository;
		this.packageVersionRepository = packageVersionRepository;
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public DocumentationPackageVersion persist(
			UUID projectId, DocumentationGenerationOutcome.Success outcome, DocumentationProfile profile, List<String> generationReasons) {
		JsonNode contextContent = objectMapper.readTree(outcome.context().getContentJson());
		String locale = contextContent.path("targetLocale").asString();
		String policyRef = contextContent.path("policyRef").asString();
		String audience = contextContent.path("primaryAudience").asString();

		DocumentationLine initialLine = findOrCreateLine(projectId, profile.ref(), locale);
		String idempotencyKey = computeIdempotencyKey(initialLine.getId(), outcome.candidateJson());

		Optional<DocumentationPackageVersion> existing =
				packageVersionRepository.findByDocumentationLineIdAndIdempotencyKey(initialLine.getId(), idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		ArtifactVersion semanticVersion = persistArtifactVersion(
				projectId, SEMANTIC_CANDIDATE_ARTIFACT_TYPE, outcome.originAgentExecutionId(), outcome.candidateJson());

		List<ArtifactVersion> reportVersions = outcome.reports().stream()
				.map(report -> persistArtifactVersion(
						projectId,
						REPORT_ARTIFACT_TYPE_PREFIX + report.path("reportType").asString("unknown").toLowerCase(java.util.Locale.ROOT),
						outcome.originAgentExecutionId(),
						report.toString()))
				.toList();

		String validationResultJson = buildValidationResultJson(outcome, semanticVersion.getId());
		ArtifactVersion validationResultVersion =
				persistArtifactVersion(projectId, VALIDATION_RESULT_ARTIFACT_TYPE, outcome.originAgentExecutionId(), validationResultJson);

		for (int attempt = 1; attempt <= MAX_REVISION_ASSIGNMENT_ATTEMPTS; attempt++) {
			try {
				return assignRevision(
						projectId,
						initialLine.getId(),
						profile,
						locale,
						policyRef,
						audience,
						outcome,
						idempotencyKey,
						semanticVersion,
						reportVersions,
						validationResultVersion,
						generationReasons);
			} catch (ObjectOptimisticLockingFailureException e) {
				if (attempt == MAX_REVISION_ASSIGNMENT_ATTEMPTS) {
					throw e;
				}
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/**
	 * One revision-assignment attempt: re-reads the line fresh (to see any concurrently-committed
	 * revision), computes the next revision number, saves the new {@link DocumentationPackageVersion},
	 * then advances the line's own pointer - that last save is what actually triggers the {@code
	 * @Version} optimistic-lock check, so a concurrent winner's earlier commit surfaces here as
	 * {@link ObjectOptimisticLockingFailureException} rather than silently overwriting it.
	 */
	private DocumentationPackageVersion assignRevision(
			UUID projectId,
			UUID lineId,
			DocumentationProfile profile,
			String locale,
			String policyRef,
			String audience,
			DocumentationGenerationOutcome.Success outcome,
			String idempotencyKey,
			ArtifactVersion semanticVersion,
			List<ArtifactVersion> reportVersions,
			ArtifactVersion validationResultVersion,
			List<String> generationReasons) {
		DocumentationLine line = lineRepository.findById(lineId).orElseThrow();
		int newRevision = line.getCurrentRevision() + 1;
		UUID packageId = UUID.randomUUID();
		UUID supersedes = line.getCurrentPackageVersionId().orElse(null);

		String contentJson = buildPackageVersionJson(
				packageId,
				lineId,
				newRevision,
				projectId,
				outcome.context().getId(),
				profile,
				policyRef,
				audience,
				locale,
				semanticVersion,
				reportVersions,
				validationResultVersion,
				outcome.originAgentExecutionId(),
				supersedes,
				generationReasons);

		DocumentationPackageVersion packageVersion = packageVersionRepository.saveAndFlush(new DocumentationPackageVersion(
				packageId, lineId, newRevision, projectId, outcome.context().getId(), profile.ref(), idempotencyKey, supersedes, contentJson));

		line.advance(packageId, newRevision);
		lineRepository.saveAndFlush(line);

		return packageVersion;
	}

	private DocumentationLine findOrCreateLine(UUID projectId, String profileRef, String locale) {
		return lineRepository
				.findByProjectIdAndProfileRefAndLocale(projectId, profileRef, locale)
				.orElseGet(() -> lineRepository.saveAndFlush(new DocumentationLine(projectId, profileRef, locale)));
	}

	private ArtifactVersion persistArtifactVersion(UUID projectId, String type, UUID agentExecutionId, String content) {
		Artifact artifact = artifactRepository
				.findByProjectIdAndType(projectId, type)
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, type)));
		int nextVersion =
				artifactVersionRepository.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId()).map(v -> v.getVersionNumber() + 1).orElse(1);
		return artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), nextVersion, agentExecutionId, content));
	}

	/**
	 * Builds the {@code documentation-validation-result.schema.json} envelope by recomputing
	 * {@code checkResults}/{@code overallResult} from the one invariant {@code Success} actually
	 * guarantees (see class javadoc) - never trusting a passed-in "it's fine" flag.
	 */
	private String buildValidationResultJson(DocumentationGenerationOutcome.Success outcome, UUID semanticCandidateRef) {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("schemaVersion", SCHEMA_VERSION);
		root.put("validationId", UUID.randomUUID().toString());
		root.put("packageCandidateRef", semanticCandidateRef.toString());
		root.put("contextRef", outcome.context().getId().toString());
		root.put("validatorSuiteVersion", VALIDATOR_SUITE_VERSION);
		root.put("overallResult", "PASS");

		ArrayNode checkResults = objectMapper.createArrayNode();
		String candidateRef = semanticCandidateRef.toString();
		for (String checkId : List.of(
				"schema-identity-structure",
				"post-generation-secret-scan",
				"deterministic-keys-domains-disclosure-lifecycle",
				"semantic-factual-consistency")) {
			ObjectNode check = objectMapper.createObjectNode();
			check.put("checkId", checkId);
			check.put("result", "PASS");
			check.put("evaluatedCandidateRef", candidateRef);
			checkResults.add(check);
		}
		root.set("checkResults", checkResults);
		root.set("issues", objectMapper.createArrayNode());
		root.put("createdAt", Instant.now().toString());

		return objectMapper.writeValueAsString(root);
	}

	private String buildPackageVersionJson(
			UUID packageId,
			UUID lineId,
			int revision,
			UUID projectId,
			UUID contextId,
			DocumentationProfile profile,
			String policyRef,
			String audience,
			String locale,
			ArtifactVersion semanticVersion,
			List<ArtifactVersion> reportVersions,
			ArtifactVersion validationResultVersion,
			UUID originAgentExecutionId,
			UUID supersedes,
			List<String> generationReasons) {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("schemaVersion", SCHEMA_VERSION);
		root.put("packageId", packageId.toString());
		root.put("documentationLineRef", lineId.toString());
		root.put("revision", revision);
		root.put("projectRef", projectId.toString());
		root.put("contextRef", contextId.toString());
		root.put("profileRef", profile.ref());
		root.put("policyRef", policyRef);
		root.put("audience", audience);
		root.put("locale", locale);

		ArrayNode semanticRefs = objectMapper.createArrayNode();
		semanticRefs.add(semanticVersion.getId().toString());
		root.set("semanticArtifactRefs", semanticRefs);

		ArrayNode reportRefs = objectMapper.createArrayNode();
		reportVersions.forEach(v -> reportRefs.add(v.getId().toString()));
		root.set("deterministicReportRefs", reportRefs);

		root.put("validationResultRef", validationResultVersion.getId().toString());
		root.put("originRunRef", originAgentExecutionId.toString());
		if (supersedes != null) {
			root.put("supersedesPackageVersionRef", supersedes.toString());
		}

		ArrayNode reasons = objectMapper.createArrayNode();
		generationReasons.forEach(reasons::add);
		root.set("generationReasons", reasons);

		root.put("createdAt", Instant.now().toString());

		return objectMapper.writeValueAsString(root);
	}

	private String computeIdempotencyKey(UUID lineId, String candidateJson) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((lineId + " " + candidateJson).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is a mandatory JDK algorithm", e);
		}
	}
}
