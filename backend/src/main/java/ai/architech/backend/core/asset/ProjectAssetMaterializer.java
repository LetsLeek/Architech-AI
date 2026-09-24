package ai.architech.backend.core.asset;

import ai.architech.backend.core.sandbox.WorkspaceFileSystem;

/**
 * Writes an already-resolved {@link ProjectAsset}'s binary content into a Developer workspace,
 * reusing {@link WorkspaceFileSystem#writeBytes} for its containment/protected-path guarantees
 * (AIW-152) rather than reimplementing path safety here.
 */
public final class ProjectAssetMaterializer {

	private ProjectAssetMaterializer() {
	}

	public static void materialize(ProjectAsset asset, WorkspaceFileSystem workspaceFileSystem, String relativePath) {
		workspaceFileSystem.writeBytes(relativePath, asset.getContent());
	}
}
