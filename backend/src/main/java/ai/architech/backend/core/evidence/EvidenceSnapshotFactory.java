package ai.architech.backend.core.evidence;

import ai.architech.backend.core.projectinput.FileProjectInput;
import ai.architech.backend.core.projectinput.FileProjectInputRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.projectinput.StructuredProjectInput;
import ai.architech.backend.core.projectinput.StructuredProjectInputRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Takes an {@link EvidenceSnapshot} by reading whichever evidence items exist for a project
 * right now. The snapshot itself is then fixed forever - inputs added afterwards need a new
 * snapshot to be picked up, they never retroactively appear in this one.
 */
@Component
public class EvidenceSnapshotFactory {

	private final ProjectInputRepository projectInputRepository;
	private final StructuredProjectInputRepository structuredProjectInputRepository;
	private final FileProjectInputRepository fileProjectInputRepository;
	private final EvidenceSnapshotRepository evidenceSnapshotRepository;

	EvidenceSnapshotFactory(
			ProjectInputRepository projectInputRepository,
			StructuredProjectInputRepository structuredProjectInputRepository,
			FileProjectInputRepository fileProjectInputRepository,
			EvidenceSnapshotRepository evidenceSnapshotRepository) {
		this.projectInputRepository = projectInputRepository;
		this.structuredProjectInputRepository = structuredProjectInputRepository;
		this.fileProjectInputRepository = fileProjectInputRepository;
		this.evidenceSnapshotRepository = evidenceSnapshotRepository;
	}

	public EvidenceSnapshot takeSnapshot(UUID projectId) {
		EvidenceSnapshot snapshot = new EvidenceSnapshot(
				projectId,
				projectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
						.map(ProjectInput::getId)
						.toList(),
				structuredProjectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
						.map(StructuredProjectInput::getId)
						.toList(),
				fileProjectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
						.map(FileProjectInput::getId)
						.toList());

		return evidenceSnapshotRepository.save(snapshot);
	}
}
