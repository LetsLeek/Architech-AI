package ai.architech.backend.core.evidence;

import ai.architech.backend.core.projectinput.FileProjectInput;
import ai.architech.backend.core.projectinput.FileProjectInputRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.projectinput.StructuredProjectInput;
import ai.architech.backend.core.projectinput.StructuredProjectInputRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link SourceContext} strictly from what an {@link EvidenceSnapshot} allows -
 * never anything queried fresh from a project's current inputs.
 */
@Component
public class SourceContextFactory {

	private final EvidenceSnapshotRepository evidenceSnapshotRepository;
	private final ProjectInputRepository projectInputRepository;
	private final StructuredProjectInputRepository structuredProjectInputRepository;
	private final FileProjectInputRepository fileProjectInputRepository;

	SourceContextFactory(
			EvidenceSnapshotRepository evidenceSnapshotRepository,
			ProjectInputRepository projectInputRepository,
			StructuredProjectInputRepository structuredProjectInputRepository,
			FileProjectInputRepository fileProjectInputRepository) {
		this.evidenceSnapshotRepository = evidenceSnapshotRepository;
		this.projectInputRepository = projectInputRepository;
		this.structuredProjectInputRepository = structuredProjectInputRepository;
		this.fileProjectInputRepository = fileProjectInputRepository;
	}

	public SourceContext build(UUID evidenceSnapshotId) {
		EvidenceSnapshot snapshot = evidenceSnapshotRepository
				.findById(evidenceSnapshotId)
				.orElseThrow(() -> new EvidenceSnapshotNotFoundException(evidenceSnapshotId));

		List<SourceItem> items = new ArrayList<>();

		for (UUID id : snapshot.getProjectInputIds()) {
			ProjectInput input = projectInputRepository.findById(id).orElseThrow();
			items.add(new SourceItem(input.getId(), SourceOrigin.FREE_TEXT, input.getContent()));
		}

		for (UUID id : snapshot.getStructuredProjectInputIds()) {
			StructuredProjectInput input = structuredProjectInputRepository.findById(id).orElseThrow();
			items.add(new SourceItem(input.getId(), SourceOrigin.STRUCTURED, renderFields(input.getFields())));
		}

		for (UUID id : snapshot.getFileProjectInputIds()) {
			FileProjectInput input = fileProjectInputRepository.findById(id).orElseThrow();
			items.add(new SourceItem(input.getId(), SourceOrigin.FILE, describeFile(input)));
		}

		return new SourceContext(evidenceSnapshotId, List.copyOf(items));
	}

	private static String renderFields(Map<String, String> fields) {
		return fields.entrySet().stream()
				.map(entry -> entry.getKey() + ": " + entry.getValue())
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

	/**
	 * No text extraction happens here - that is a separate, later platform concern (see
	 * AIW-29). Until it exists, a file is represented by its origin metadata only.
	 */
	private static String describeFile(FileProjectInput file) {
		return "Uploaded file: %s (%s, %d bytes) - content not yet extracted"
				.formatted(file.getFilename(), file.getContentType(), file.getSizeBytes());
	}
}
