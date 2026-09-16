package ai.architech.backend.core.qa.profiles;

import ai.architech.backend.core.qa.tooling.QaToolCapability;

/** One required check whose bound {@link QaToolCapability} the resolved tool profile does not grant (AIW-175). */
public record ToolProfileCompatibilityProblem(String checkCode, QaToolCapability requiredCapability) {}
