package ai.architech.backend.core.qa.tooling;

public class QaToolCapabilityProfileNotFoundException extends RuntimeException {

	public QaToolCapabilityProfileNotFoundException(String ref) {
		super("No QA tool capability profile found for ref '" + ref + "'");
	}
}
