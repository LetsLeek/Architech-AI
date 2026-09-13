package ai.architech.backend.core.repository;

/** Opaque identity of an immutable Development Base (B0) - here, its exact git commit SHA. */
public record DevelopmentBaseRef(String commitSha) {}
