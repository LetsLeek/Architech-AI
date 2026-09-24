package ai.architech.backend.core.validation;

import ai.architech.backend.core.qa.EvidenceRecord;
import ai.architech.backend.core.qa.EvidenceRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code core/VALIDATORS.md}'s own {@code EvidenceBindingValidator}: "Validates Evidence
 * existence, integrity, Candidate/execution binding and relevant context. Foreign Candidate
 * Evidence requires explicit compatible reuse provenance." (AIW-170).
 *
 * <p>"Integrity" here is {@link EvidenceRecord}'s own immutability plus the redaction
 * {@link EvidenceRecord} already applies at construction time - this validator's own job is
 * strictly the binding checks named above: every {@code evidenceRef} must resolve to a real row,
 * that row must belong to the Candidate under evaluation (unless it carries an explicit,
 * non-blank {@link EvidenceRecord#getReuseJustification() reuse justification} -
 * {@code rules/evidence.md}'s "Evidence for another Candidate MUST NOT be silently reused" /
 * "Evidence reuse requires explicit compatibility and provenance"), and any context the claim
 * under evaluation actually requires (route/viewport/locale/interaction-state) must be backed by
 * at least one of the cited Evidence rows.
 *
 * <p>Deliberately does not judge whether the cited Evidence set is <em>sufficient</em> for a
 * compound claim, and never treats a {@code valid()} binding result as proof of coverage -
 * {@code rules/evidence.md}'s own "Absence of Findings does not prove coverage" and
 * {@code skills/evidence-assessment/SKILL.md}'s "One Evidence item may be insufficient for a
 * compound claim" are judgments about evaluation completeness the (not yet built) semantic
 * evidence-assessment step and {@code QAPolicyAggregator} (AIW-176) own, not this validator -
 * this type answers only "does what was cited actually exist and bind correctly", never "was
 * enough cited".
 */
@Component
public class EvidenceBindingValidator {

	private final EvidenceRecordRepository evidenceRecordRepository;

	public EvidenceBindingValidator(EvidenceRecordRepository evidenceRecordRepository) {
		this.evidenceRecordRepository = evidenceRecordRepository;
	}

	public EvidenceBindingResult validate(UUID testedCandidateId, RequiredEvidenceContext requiredContext, List<String> evidenceRefs) {
		List<EvidenceReferenceProblem> problems = new ArrayList<>();
		List<EvidenceRecord> resolved = new ArrayList<>();

		for (String evidenceRef : evidenceRefs) {
			Optional<EvidenceRecord> evidence = resolve(evidenceRef);
			if (evidence.isEmpty()) {
				problems.add(new EvidenceReferenceProblem(evidenceRef, "no Evidence exists for this reference"));
				continue;
			}
			EvidenceRecord record = evidence.get();
			if (!record.getTestedCandidateId().equals(testedCandidateId) && !hasCompatibleReuse(record)) {
				problems.add(new EvidenceReferenceProblem(evidenceRef, "Evidence was captured against Candidate "
						+ record.getTestedCandidateId() + ", not the Candidate under evaluation (" + testedCandidateId
						+ "), and carries no compatible-reuse justification"));
				continue;
			}
			resolved.add(record);
		}

		problems.addAll(missingContextProblems(requiredContext, resolved));

		return problems.isEmpty() ? EvidenceBindingResult.valid() : EvidenceBindingResult.invalid(problems);
	}

	private List<EvidenceReferenceProblem> missingContextProblems(RequiredEvidenceContext requiredContext, List<EvidenceRecord> resolved) {
		List<EvidenceReferenceProblem> problems = new ArrayList<>();
		if (requiredContext.route() != null && resolved.stream().noneMatch(e -> requiredContext.route().equals(e.getRoute()))) {
			problems.add(missingContext("route '" + requiredContext.route() + "'"));
		}
		if (requiredContext.viewportRef() != null
				&& resolved.stream().noneMatch(e -> requiredContext.viewportRef().equals(e.getViewportRef()))) {
			problems.add(missingContext("viewport '" + requiredContext.viewportRef() + "'"));
		}
		if (requiredContext.locale() != null && resolved.stream().noneMatch(e -> requiredContext.locale().equals(e.getLocale()))) {
			problems.add(missingContext("locale '" + requiredContext.locale() + "'"));
		}
		if (requiredContext.interactionState() != null
				&& resolved.stream().noneMatch(e -> requiredContext.interactionState().equals(e.getInteractionState()))) {
			problems.add(missingContext("interaction state '" + requiredContext.interactionState() + "'"));
		}
		return problems;
	}

	private EvidenceReferenceProblem missingContext(String description) {
		return new EvidenceReferenceProblem(null, "none of the cited Evidence carries the required " + description + " context");
	}

	private boolean hasCompatibleReuse(EvidenceRecord record) {
		return record.getReuseJustification() != null && !record.getReuseJustification().isBlank();
	}

	private Optional<EvidenceRecord> resolve(String evidenceRef) {
		UUID evidenceId;
		try {
			evidenceId = UUID.fromString(Objects.requireNonNull(evidenceRef));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		return evidenceRecordRepository.findById(evidenceId);
	}
}
