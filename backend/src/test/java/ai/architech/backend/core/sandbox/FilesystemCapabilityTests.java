package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilesystemCapabilityTests {

	@Test
	void containsExactlyTheEightCapabilitiesTheProfileLists() {
		assertThat(FilesystemCapability.values())
				.containsExactlyInAnyOrder(
						FilesystemCapability.LIST,
						FilesystemCapability.READ,
						FilesystemCapability.SEARCH,
						FilesystemCapability.WRITE,
						FilesystemCapability.PATCH,
						FilesystemCapability.MKDIR,
						FilesystemCapability.MOVE,
						FilesystemCapability.DELETE);
	}
}
