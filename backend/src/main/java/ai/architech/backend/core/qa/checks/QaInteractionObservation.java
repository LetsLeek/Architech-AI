package ai.architech.backend.core.qa.checks;

import java.util.List;

/** What {@link QaPlaywrightDriver#interact} observed after clicking one selector (AIW-172). */
public record QaInteractionObservation(boolean elementFound, boolean clickSucceeded, List<String> pageErrors, List<String> consoleErrors) {}
