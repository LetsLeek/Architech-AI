package ai.architech.backend.core.sandbox;

/** Matches {@code tool-capability-profile.v1.yaml}'s {@code filesystem.allowed} list exactly. */
public enum FilesystemCapability {
	LIST,
	READ,
	SEARCH,
	WRITE,
	PATCH,
	MKDIR,
	MOVE,
	DELETE
}
