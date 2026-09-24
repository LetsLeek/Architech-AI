package ai.architech.backend.core.documentation.triggers;

/**
 * The three {@code documentation-workflow-policy.yaml} event names this codebase actually acts on
 * (AIW-202): {@code FULL_RELEASE_QA_FINALIZED} (automatic → {@code TECHNICAL_HANDOVER}), {@code
 * SCOPED_APPROVAL_RECORDED} (automatic → {@code CUSTOMER_HANDOVER}), and {@code
 * DEPLOYMENT_RECORDED} (the one optional refresh event). The policy's own {@code
 * ignoredAutomaticTriggers} list ({@code GENERIC_PROJECT_UPDATED}, {@code
 * DOCUMENTATION_CANONICALIZED}, {@code RENDER_COMPLETED}, {@code DOWNLOAD_RECORDED}) is
 * deliberately not represented here at all - nothing in this codebase constructs a value for an
 * event this enum has no constant for, so "must never trigger generation" is a structural
 * guarantee rather than a case this evaluator has to remember to reject.
 */
public enum DocumentationTriggerEvent {
	FULL_RELEASE_QA_FINALIZED,
	SCOPED_APPROVAL_RECORDED,
	DEPLOYMENT_RECORDED
}
