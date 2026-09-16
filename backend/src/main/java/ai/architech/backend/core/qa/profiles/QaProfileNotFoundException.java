package ai.architech.backend.core.qa.profiles;

public class QaProfileNotFoundException extends RuntimeException {

	public QaProfileNotFoundException(String ref) {
		super("No QA profile found for ref '" + ref + "'");
	}
}
