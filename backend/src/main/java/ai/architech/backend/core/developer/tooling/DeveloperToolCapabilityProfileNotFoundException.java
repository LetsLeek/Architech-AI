package ai.architech.backend.core.developer.tooling;

public class DeveloperToolCapabilityProfileNotFoundException extends RuntimeException {

	public DeveloperToolCapabilityProfileNotFoundException(String id) {
		super("No Developer tool capability profile found for id '" + id + "'");
	}
}
