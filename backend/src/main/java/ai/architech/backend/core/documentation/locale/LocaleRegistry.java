package ai.architech.backend.core.documentation.locale;

import java.util.List;

/** The loaded {@code registries/locale-registry.yaml} (AIW-188): no automatic locale fallback. */
public record LocaleRegistry(String registryVersion, List<String> activeLocales, String note) {}
