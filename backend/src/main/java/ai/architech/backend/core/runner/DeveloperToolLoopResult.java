package ai.architech.backend.core.runner;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;

/** The terminal outcome of one {@link DeveloperToolLoopOrchestrator#run} call (AIW-184) - {@code candidate} is non-null only when {@code execution} ended {@code SUCCEEDED}. */
public record DeveloperToolLoopResult(AgentExecution execution, WebsiteImplementationCandidate candidate) {}
