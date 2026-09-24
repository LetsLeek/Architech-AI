package ai.architech.backend.core.qa.checks;

import ai.architech.backend.core.qa.tooling.QaToolCapability;
import java.util.Optional;

/**
 * One row of {@code registries/qa-checks.yaml} (AIW-172). {@link #capability()} is empty exactly
 * for the frozen registry's own {@code PLATFORM_CORE}-tagged checks - AIW-171 deliberately
 * excluded {@code PLATFORM_CORE} from {@link QaToolCapability} because those checks are pure
 * reference-integrity checks against already-persisted platform rows, needing no sandboxed tool
 * grant at all.
 */
public record QaCheckRegistryEntry(String code, String domain, Optional<QaToolCapability> capability, QaCheckCategory category) {}
