package ai.architech.backend.core.developer.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Unit-level proof of {@link DeveloperToolCapabilityProfileLoader}'s own safety-contract enforcement (AIW-184) - no Spring context, no classpath scan. */
class DeveloperToolCapabilityProfileLoaderTests {

	private static final DeveloperToolCapabilityProfile.Filesystem FILESYSTEM =
			new DeveloperToolCapabilityProfile.Filesystem(List.of("read"), true, List.of(".git"));
	private static final DeveloperToolCapabilityProfile.ProjectExecution PROJECT_EXECUTION =
			new DeveloperToolCapabilityProfile.ProjectExecution(List.of("install"), false);
	private static final DeveloperToolCapabilityProfile.Network NETWORK = new DeveloperToolCapabilityProfile.Network(false, true);

	@Test
	void aCompliantProfileHasNoViolations() {
		DeveloperToolCapabilityProfile profile = profile(
				new DeveloperToolCapabilityProfile.Git(List.of("status"), List.of()),
				new DeveloperToolCapabilityProfile.Security(true, false, false, false));

		assertThat(DeveloperToolCapabilityProfileLoader.safetyContractViolations(profile)).isEmpty();
	}

	@Test
	void reportsEverySecurityFieldViolationByName() {
		DeveloperToolCapabilityProfile profile = profile(
				new DeveloperToolCapabilityProfile.Git(List.of("status"), List.of()),
				new DeveloperToolCapabilityProfile.Security(false, true, true, true));

		assertThat(DeveloperToolCapabilityProfileLoader.safetyContractViolations(profile)).containsExactlyInAnyOrder(
				"security.nonRoot must be true",
				"security.dockerSocket must be false",
				"security.cloudCredentials must be false",
				"security.platformSecrets must be false");
	}

	@Test
	void reportsADangerousGitSubcommandEvenIfSomehowPresentInAllowed() {
		DeveloperToolCapabilityProfile profile = profile(
				new DeveloperToolCapabilityProfile.Git(List.of("status", "push"), List.of()),
				new DeveloperToolCapabilityProfile.Security(true, false, false, false));

		assertThat(DeveloperToolCapabilityProfileLoader.safetyContractViolations(profile))
				.containsExactly("git.allowed must never contain 'push'");
	}

	@Test
	void invalidProfileExceptionIncludesTheResourceAndCause() {
		ClassPathResource resource = new ClassPathResource("does-not-exist.yaml");
		RuntimeException cause = new RuntimeException("boom");

		InvalidDeveloperToolCapabilityProfileException exception =
				new InvalidDeveloperToolCapabilityProfileException(resource, "malformed", cause);

		assertThat(exception.getMessage()).contains("malformed").contains("does-not-exist.yaml");
		assertThat(exception.getCause()).isSameAs(cause);
	}

	@Test
	void invalidProfileExceptionWithoutACauseStillIncludesTheResource() {
		ClassPathResource resource = new ClassPathResource("does-not-exist.yaml");

		InvalidDeveloperToolCapabilityProfileException exception =
				new InvalidDeveloperToolCapabilityProfileException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("does-not-exist.yaml");
		assertThat(exception.getCause()).isNull();
	}

	private DeveloperToolCapabilityProfile profile(
			DeveloperToolCapabilityProfile.Git git, DeveloperToolCapabilityProfile.Security security) {
		return new DeveloperToolCapabilityProfile("test-profile", FILESYSTEM, PROJECT_EXECUTION, true, git, true, NETWORK, security);
	}
}
