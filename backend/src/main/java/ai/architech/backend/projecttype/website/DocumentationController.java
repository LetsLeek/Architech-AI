package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.documentation.canonical.DocumentationLine;
import ai.architech.backend.core.documentation.canonical.DocumentationLineRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only Documentation package API (AIW-209) - exposes every {@link DocumentationLine} for a
 * project (one per {@code profileRef}+{@code locale} combination - e.g. {@code TECHNICAL_HANDOVER}
 * /{@code en} and {@code CUSTOMER_HANDOVER}/{@code en} are separate lines) together with a summary
 * of its current canonical {@link DocumentationPackageVersion}, mirroring {@code
 * QaResultController}'s (AIW-208) exact structural/naming/error-handling conventions.
 *
 * <p><b>The automatic trigger this endpoint reads the output of is wired elsewhere</b> - {@code
 * QaTriggerService} (AIW-213) already calls {@code DocumentationTriggerService
 * .dispatchAutomaticTrigger} directly on a QA {@code PASS} gate outcome; this ticket's own real
 * remaining scope, confirmed via two AIW-209 Jira comments before starting, is exactly this
 * read-only API - no trigger endpoint is added here.
 *
 * <p><b>Empty list, not 404, when nothing has been generated yet</b> - unlike QA (a single "latest
 * result" concept for a project), Documentation is inherently multi-line/multi-locale, and "no
 * Documentation generated yet" (e.g. a project that hasn't reached a QA {@code PASS} gate) is a
 * normal, non-exceptional state for this list-shaped resource. This endpoint therefore returns
 * {@code 200 OK} with an empty array rather than a {@code DOCUMENTATION_NOT_FOUND}-style 404 - a
 * project that genuinely doesn't exist still reports {@link ErrorCode#PROJECT_NOT_FOUND}, the same
 * check {@code QaResultController} performs first.
 *
 * <p>No raw {@code contentJson} is ever returned - see {@link DocumentationLineResponse}'s own
 * javadoc for the parsed-summary convention it applies instead.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/documentation-packages")
class DocumentationController {

	private final ProjectRepository projectRepository;
	private final DocumentationLineRepository documentationLineRepository;
	private final DocumentationPackageVersionRepository documentationPackageVersionRepository;
	private final ObjectMapper objectMapper;

	DocumentationController(
			ProjectRepository projectRepository,
			DocumentationLineRepository documentationLineRepository,
			DocumentationPackageVersionRepository documentationPackageVersionRepository,
			ObjectMapper objectMapper) {
		this.projectRepository = projectRepository;
		this.documentationLineRepository = documentationLineRepository;
		this.documentationPackageVersionRepository = documentationPackageVersionRepository;
		this.objectMapper = objectMapper;
	}

	@GetMapping
	ResponseEntity<List<DocumentationLineResponse>> list(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}

		List<DocumentationLineResponse> lines =
				documentationLineRepository.findByProjectId(projectId).stream().map(this::toResponse).toList();

		return ResponseEntity.ok(lines);
	}

	private DocumentationLineResponse toResponse(DocumentationLine line) {
		DocumentationPackageVersion currentVersion = line.getCurrentPackageVersionId()
				.flatMap(documentationPackageVersionRepository::findById)
				.orElse(null);
		return DocumentationLineResponse.from(line, currentVersion, objectMapper);
	}
}
