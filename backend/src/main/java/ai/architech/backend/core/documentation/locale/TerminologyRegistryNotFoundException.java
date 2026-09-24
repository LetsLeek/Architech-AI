package ai.architech.backend.core.documentation.locale;

public class TerminologyRegistryNotFoundException extends RuntimeException {

	public TerminologyRegistryNotFoundException(String locale) {
		super("No Documentation terminology registry found for locale '" + locale + "'");
	}
}
