package ai.architech.backend.core.documentation.locale;

import java.util.List;
import java.util.Map;

/** One loaded {@code registries/terminology/<locale>.yaml} (AIW-188). */
public record TerminologyRegistry(
		String locale, List<String> protectedEnums, Map<String, String> sectionTitles, Map<String, String> requirements, String legalNames) {}
