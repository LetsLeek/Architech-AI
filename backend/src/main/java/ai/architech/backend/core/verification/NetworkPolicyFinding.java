package ai.architech.backend.core.verification;

public record NetworkPolicyFinding(String url, String host, NetworkPolicyOutcome outcome, String reason) {}
