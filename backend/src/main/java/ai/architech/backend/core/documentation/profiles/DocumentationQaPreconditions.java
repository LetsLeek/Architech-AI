package ai.architech.backend.core.documentation.profiles;

import java.util.List;

/** A profile's {@code qaPreconditions} block: the QA profile/gate combination it requires. */
public record DocumentationQaPreconditions(String qaProfile, List<String> allowedGates) {}
