package ai.architech.backend.core.verification;

/**
 * One network request observed during local route/navigation smoke - the data shape
 * {@link NetworkPolicyChecker} classifies. Capturing these for real (a headless browser
 * listening to its own network events during Runner Verification) is Runner-integration scope,
 * not this ticket's: AIW-157 is the classification policy itself, exactly as its own title says
 * ("browser and external network policy checker"), consumed by whatever later capture mechanism
 * produces this list.
 */
public record ObservedRequest(String url, String purpose) {}
