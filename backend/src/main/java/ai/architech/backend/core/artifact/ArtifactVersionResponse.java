package ai.architech.backend.core.artifact;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The latest canonical version of one artifact, for reading. {@code content} is embedded as
 * real JSON (not a stringified blob the caller has to re-parse) - safe to parse eagerly here
 * because reaching {@link ArtifactVersion} at all already means it passed schema validation
 * (see {@link ArtifactVersion}'s own contract).
 */
public record ArtifactVersionResponse(UUID artifactId, String type, int versionNumber, JsonNode content, Instant createdAt) {}
