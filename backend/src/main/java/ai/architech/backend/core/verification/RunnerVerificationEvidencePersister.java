package ai.architech.backend.core.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Persists one {@link AuthoritativeRunnerVerifier} run's complete evidence (AIW-155): the run
 * itself, plus exactly one {@link RunnerVerificationGateRecord} for every name in {@link
 * AuthoritativeRunnerVerifier#MANDATORY_GATE_NAMES} - a gate present in the supplied {@link
 * RunnerVerificationResult} is persisted with its real outcome, one absent from it (because
 * fail-fast stopped the run earlier) is persisted as {@link GateStatus#SKIPPED} rather than
 * silently having no row at all.
 *
 * <p>The persisted run's own {@code outcome} is deliberately <em>recomputed</em> from the full,
 * SKIPPED-filled gate list here, never copied directly from {@link RunnerVerificationResult#outcome()}:
 * that method only inspects whichever gates happen to be present, so a result list that is
 * merely incomplete (rather than genuinely terminated by a FAIL/ERROR gate) would otherwise
 * compute to a false {@code PASS}. Recomputing here - {@code ERROR} if any gate errored, else
 * {@code FAIL} if any gate failed <em>or was skipped</em>, else {@code PASS} - is what makes
 * "mandatory skipped gates cannot yield final PASS" a structural guarantee of the persisted
 * evidence itself, independent of whether the caller happened to pass a well-formed result.
 *
 * <p>Does no result validation itself, the same idiom as {@code CandidatePromoter}/{@code
 * WebsiteImplementationCandidatePromoter}: callers pass whatever {@link RunnerVerificationResult}
 * {@link AuthoritativeRunnerVerifier#verify} actually produced.
 */
@Component
public class RunnerVerificationEvidencePersister {

	private final RunnerVerificationRunRepository runRepository;
	private final RunnerVerificationGateRecordRepository gateRecordRepository;

	RunnerVerificationEvidencePersister(
			RunnerVerificationRunRepository runRepository, RunnerVerificationGateRecordRepository gateRecordRepository) {
		this.runRepository = runRepository;
		this.gateRecordRepository = gateRecordRepository;
	}

	public RunnerVerificationRun persist(UUID agentExecutionId, String repositoryStateRef, RunnerVerificationResult result) {
		Map<String, GateResult> byName =
				result.gates().stream().collect(Collectors.toMap(GateResult::gateName, Function.identity()));

		record Draft(String gateName, int order, GateStatus status, String detail) {}

		List<Draft> drafts = new ArrayList<>();
		int order = 0;
		for (String gateName : AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES) {
			order++;
			GateResult gate = byName.get(gateName);
			drafts.add(gate != null
					? new Draft(gateName, order, GateStatus.fromOutcome(gate.outcome()), gate.detail())
					: new Draft(gateName, order, GateStatus.SKIPPED, null));
		}

		VerificationOutcome outcome = recomputeOutcome(drafts.stream().map(Draft::status).toList());
		RunnerVerificationRun run =
				runRepository.saveAndFlush(new RunnerVerificationRun(agentExecutionId, repositoryStateRef, outcome));

		List<RunnerVerificationGateRecord> records = drafts.stream()
				.map(draft -> new RunnerVerificationGateRecord(run.getId(), draft.gateName(), draft.order(), draft.status(), draft.detail()))
				.toList();
		gateRecordRepository.saveAll(records);

		return run;
	}

	private VerificationOutcome recomputeOutcome(List<GateStatus> statuses) {
		if (statuses.stream().anyMatch(status -> status == GateStatus.ERROR)) {
			return VerificationOutcome.ERROR;
		}
		if (statuses.stream().anyMatch(status -> status == GateStatus.FAIL || status == GateStatus.SKIPPED)) {
			return VerificationOutcome.FAIL;
		}
		return VerificationOutcome.PASS;
	}
}
