package ai.architech.backend.core.validation;

import ai.architech.backend.core.evidence.SourceRef;
import ai.architech.backend.core.evidence.SourceRefRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Checks that every ref a candidate output cites actually belongs to the exact evidence
 * snapshot active for that attempt - not merely "some ref that exists somewhere", which
 * would let a model cite evidence from an unrelated project/execution. Detects and reports
 * only: never repairs, renames, or drops an invalid ref from a candidate on its own.
 *
 * <p>Not yet wired into {@code AgentRunner}/{@code AgentExecution} - that requires the
 * candidate to actually be parsed for its cited refs, which needs the JSON Schema/output
 * contract validation (AIW-47) this Runner doesn't have yet. This class is deliberately
 * usable standalone in the meantime.
 */
@Component
public class SourceRefValidator {

	private final SourceRefRepository sourceRefRepository;

	SourceRefValidator(SourceRefRepository sourceRefRepository) {
		this.sourceRefRepository = sourceRefRepository;
	}

	public SourceRefValidationResult validate(UUID evidenceSnapshotId, Set<String> citedRefs) {
		Set<String> refsInActiveSnapshot = sourceRefRepository.findByEvidenceSnapshotId(evidenceSnapshotId).stream()
				.map(SourceRef::getRef)
				.collect(Collectors.toSet());

		List<SourceRefValidationIssue> issues = citedRefs.stream()
				.filter(ref -> !refsInActiveSnapshot.contains(ref))
				.map(ref -> new SourceRefValidationIssue(ref, "not part of the active evidence snapshot"))
				.toList();

		return issues.isEmpty() ? SourceRefValidationResult.passed() : new SourceRefValidationResult(false, issues);
	}
}
