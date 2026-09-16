package ai.architech.backend.core.qa;

import ai.architech.backend.core.verification.SecretPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * One immutable, integrity-protected observation an {@link EvidenceManifest} indexes (AIW-170) -
 * {@code rules/evidence.md}'s own authorized evidence list ("screenshots, DOM snapshots,
 * accessibility tree, scanner results, route results, interaction traces, network/console
 * observations, layout measurements, metadata/performance results, safe integration results and
 * read-only source references"), one row per observation. No setters, no update methods - the
 * same immutable-row idiom {@link ai.architech.backend.core.toolexecution.ToolExecution} already
 * establishes.
 *
 * <p>Carries no pass/fail notion of its own: {@code rules/evidence.md}'s "Tool failure is not
 * positive Evidence" is already structurally enforced by {@link EvaluationIssue} being the
 * separate row type a failed/undeterminable observation is recorded as (AIW-168) - there is
 * deliberately no way to construct an {@code EvidenceRecord} that represents a tool failure as a
 * successful observation, because this type only ever represents what was actually observed.
 *
 * <p>{@link #getContent()} is redacted at construction time via the same
 * {@link SecretPatterns#CONTENT_PATTERNS}-based approach
 * {@code ToolExecutionDiagnostics#sanitize} already established for {@code ToolExecution} - raw
 * captured network/header/payload/tool output can contain secrets before it is ever safe to place
 * in a semantic-Agent's normal context, and this is the one place all Evidence content passes
 * through on its way into persistence.
 */
@Entity
@Table(name = "evidence_record")
public class EvidenceRecord {

	/**
	 * Mirrors {@code rules/evidence.md}'s own enumerated list of authorized evidence kinds, split
	 * one-for-one where the rule names a combined pair ("network/console observations",
	 * "metadata/performance results") - never an invented category the frozen package does not
	 * itself name.
	 */
	public enum Kind {
		SCREENSHOT,
		DOM_SNAPSHOT,
		ACCESSIBILITY_TREE,
		SCANNER_RESULT,
		ROUTE_RESULT,
		INTERACTION_TRACE,
		NETWORK_OBSERVATION,
		CONSOLE_OBSERVATION,
		LAYOUT_MEASUREMENT,
		METADATA_RESULT,
		PERFORMANCE_RESULT,
		SAFE_INTEGRATION_RESULT,
		SOURCE_REFERENCE
	}

	private static final int MAX_CONTENT_LENGTH = 4000;
	private static final String TRUNCATION_SUFFIX = "...[truncated]";

	@Id
	private UUID id;

	@Column(name = "evidence_manifest_id", nullable = false, updatable = false)
	private UUID evidenceManifestId;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "tested_candidate_id", nullable = false, updatable = false)
	private UUID testedCandidateId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "producer_ref", nullable = false, updatable = false)
	private String producerRef;

	@Column(name = "route", updatable = false)
	private String route;

	@Column(name = "viewport_ref", updatable = false)
	private String viewportRef;

	@Column(name = "locale", updatable = false)
	private String locale;

	@Column(name = "interaction_state", updatable = false)
	private String interactionState;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "reused_from_evidence_id", updatable = false)
	private UUID reusedFromEvidenceId;

	@Column(name = "reuse_justification", updatable = false)
	private String reuseJustification;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EvidenceRecord() {
		// required by JPA
	}

	public EvidenceRecord(
			UUID evidenceManifestId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			Kind kind,
			String producerRef,
			String route,
			String viewportRef,
			String locale,
			String interactionState,
			String rawContent,
			UUID reusedFromEvidenceId,
			String reuseJustification) {
		this.id = UUID.randomUUID();
		this.evidenceManifestId = Objects.requireNonNull(evidenceManifestId);
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.testedCandidateId = Objects.requireNonNull(testedCandidateId);
		this.kind = Objects.requireNonNull(kind);
		this.producerRef = Objects.requireNonNull(producerRef);
		this.route = route;
		this.viewportRef = viewportRef;
		this.locale = locale;
		this.interactionState = interactionState;
		this.content = redact(Objects.requireNonNull(rawContent));
		this.reusedFromEvidenceId = reusedFromEvidenceId;
		this.reuseJustification = reuseJustification;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	private static String redact(String rawContent) {
		String redacted = rawContent;
		for (SecretPatterns.NamedPattern namedPattern : SecretPatterns.CONTENT_PATTERNS) {
			Matcher matcher = namedPattern.pattern().matcher(redacted);
			redacted = matcher.replaceAll(Matcher.quoteReplacement("[REDACTED:" + namedPattern.name() + "]"));
		}
		if (redacted.length() > MAX_CONTENT_LENGTH) {
			return redacted.substring(0, MAX_CONTENT_LENGTH) + TRUNCATION_SUFFIX;
		}
		return redacted;
	}

	public UUID getId() {
		return id;
	}

	public UUID getEvidenceManifestId() {
		return evidenceManifestId;
	}

	public UUID getQaExecutionId() {
		return qaExecutionId;
	}

	public UUID getTestedCandidateId() {
		return testedCandidateId;
	}

	public Kind getKind() {
		return kind;
	}

	public String getProducerRef() {
		return producerRef;
	}

	public String getRoute() {
		return route;
	}

	public String getViewportRef() {
		return viewportRef;
	}

	public String getLocale() {
		return locale;
	}

	public String getInteractionState() {
		return interactionState;
	}

	public String getContent() {
		return content;
	}

	public UUID getReusedFromEvidenceId() {
		return reusedFromEvidenceId;
	}

	public String getReuseJustification() {
		return reuseJustification;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
