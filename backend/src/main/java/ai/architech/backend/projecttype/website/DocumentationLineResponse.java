package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.documentation.canonical.DocumentationLine;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What {@link DocumentationController} returns for one project's {@link DocumentationLine}
 * (AIW-209) - profile/locale identity and current revision, plus a parsed summary of the current
 * canonical {@link DocumentationPackageVersion} (audience, policy, artifact/report counts,
 * generation reasons), never the version's raw {@code contentJson} blob - same "no raw tool
 * payloads" convention {@code QaResultResponse}'s own javadoc already establishes for {@code
 * QaResult}'s {@code domainResultsJson}/{@code provenanceJson}.
 *
 * <p>{@link #currentPackageVersion()} is {@code null} for a line that has no current package
 * version yet. In practice this never happens today - {@code DocumentationCanonicalPackagePersister
 * .persist} only ever creates a {@code DocumentationLine} and assigns it a current package version
 * inside the same {@code @Transactional} method, so a persisted line always has one - but the field
 * stays nullable rather than this class assuming that invariant can never change.
 */
record DocumentationLineResponse(
		UUID documentationLineId,
		String profileRef,
		String locale,
		int currentRevision,
		DocumentationPackageVersionSummary currentPackageVersion) {

	static DocumentationLineResponse from(
			DocumentationLine line, DocumentationPackageVersion currentVersion, ObjectMapper objectMapper) {
		return new DocumentationLineResponse(
				line.getId(),
				line.getProfileRef(),
				line.getLocale(),
				line.getCurrentRevision(),
				currentVersion == null ? null : DocumentationPackageVersionSummary.from(currentVersion, objectMapper));
	}

	record DocumentationPackageVersionSummary(
			UUID packageVersionId,
			int revision,
			String audience,
			String policyRef,
			int semanticArtifactCount,
			int deterministicReportCount,
			List<String> generationReasons,
			Instant createdAt) {

		static DocumentationPackageVersionSummary from(DocumentationPackageVersion version, ObjectMapper objectMapper) {
			JsonNode content = objectMapper.readTree(version.getContentJson());
			return new DocumentationPackageVersionSummary(
					version.getId(),
					version.getRevision(),
					content.path("audience").asString(null),
					content.path("policyRef").asString(null),
					content.path("semanticArtifactRefs").size(),
					content.path("deterministicReportRefs").size(),
					stringList(content.path("generationReasons")),
					version.getCreatedAt());
		}

		private static List<String> stringList(JsonNode node) {
			List<String> values = new ArrayList<>();
			for (JsonNode item : node) {
				values.add(item.asString());
			}
			return values;
		}
	}
}
