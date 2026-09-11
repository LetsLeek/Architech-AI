package ai.architech.backend.core.runner;

import ai.architech.backend.core.agentexecution.AgentExecution;

/**
 * What one Runner attempt produced. {@code execution} intentionally never reaches SUCCEEDED
 * here - that requires validation and persistence (not yet implemented), so it stays
 * RUNNING even after a candidate was received. {@code candidateOutput} is the model's raw
 * response text; nothing here parses, validates, or persists it as a canonical artifact.
 */
public record RunnerResult(AgentExecution execution, String candidateOutput) {}
