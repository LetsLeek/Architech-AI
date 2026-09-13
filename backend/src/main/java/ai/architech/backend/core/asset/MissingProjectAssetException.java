package ai.architech.backend.core.asset;

import java.util.UUID;

public class MissingProjectAssetException extends RuntimeException {

	public MissingProjectAssetException(UUID projectId, String assetRef) {
		super("No asset '" + assetRef + "' exists for project " + projectId);
	}
}
