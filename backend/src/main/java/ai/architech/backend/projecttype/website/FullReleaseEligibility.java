package ai.architech.backend.projecttype.website;

import java.util.UUID;

/**
 * Whether the exact release-path Candidate is currently eligible to transition to Final Human/
 * Customer Approval (AIW-178). {@link #eligible()} says nothing more than that a matching {@code
 * PASS} {@code QaResult} exists - it is deliberately not, and can never become, a Customer
 * Approval, Production Readiness, Deployment eligibility, legal/security/accessibility
 * certification, or production verification state; those remain entirely outside this type and
 * this ticket's own scope, per AIW-178's own acceptance criteria.
 */
public record FullReleaseEligibility(UUID releasePathCandidateId, boolean eligible) {}
