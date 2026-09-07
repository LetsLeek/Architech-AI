package ai.architech.backend.core.evidence;

import java.util.UUID;

public class EvidenceSnapshotNotFoundException extends RuntimeException {

	public EvidenceSnapshotNotFoundException(UUID evidenceSnapshotId) {
		super("No evidence snapshot found for id '" + evidenceSnapshotId + "'");
	}
}
