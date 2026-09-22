package ai.architech.backend.core.qa.remediation;

/**
 * Where a {@code HOLD} QA gate outcome's own resolution responsibility routes to (AIW-181) -
 * "Distinguishes intra-execution tool retry, QA execution retry, Developer remediation cycle,
 * Authority resolution and Human escalation." Intra-execution tool retry and QA execution retry
 * are the platform's own already-existing generic mechanisms ({@code ToolExecution} correction
 * cycles, {@code BoundedRetryAgentRunner}) and are not routing destinations of their own - they
 * happen entirely within one QA execution attempt, before a {@code QaResult} (and therefore a
 * {@code HOLD}) even exists.
 */
public enum EscalationRoute {
	DEVELOPER_REMEDIATION,
	AUTHORITY_RESOLUTION,
	QA_PLATFORM_RETRY,
	HUMAN_ESCALATION
}
