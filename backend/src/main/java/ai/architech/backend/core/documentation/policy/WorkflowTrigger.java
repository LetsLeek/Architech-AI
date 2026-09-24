package ai.architech.backend.core.documentation.policy;

import java.util.List;
import java.util.Optional;

/**
 * One entry of {@link DocumentationWorkflowPolicy#enabledTriggers()} or {@link
 * DocumentationWorkflowPolicy#onDemand()} - {@code event} is present only on {@code
 * enabledTriggers} entries in the frozen package.
 */
public record WorkflowTrigger(Optional<String> event, String profileRef, List<String> conditions) {}
