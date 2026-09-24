package ai.architech.backend.core.documentation.profiles;

public class DocumentationProfileNotFoundException extends RuntimeException {

	public DocumentationProfileNotFoundException(String ref) {
		super("No Documentation profile found for ref '" + ref + "'");
	}
}
