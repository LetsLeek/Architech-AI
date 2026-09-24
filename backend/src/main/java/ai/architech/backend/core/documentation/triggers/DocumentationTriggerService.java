package ai.architech.backend.core.documentation.triggers;

import ai.architech.backend.core.documentation.canonical.DocumentationCanonicalPackagePersister;
import ai.architech.backend.core.documentation.canonical.DocumentationLine;
import ai.architech.backend.core.documentation.canonical.DocumentationLineRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOrchestrator;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOutcome;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The first real end-to-end caller of the Documentation pipeline built across AIW-189 through
 * AIW-201 (AIW-202) - before this class, every component existed but nothing actually invoked the
 * full chain from a trigger through to a persisted, canonical package. There is still no real HTTP
 * controller or event-bus listener wiring an external event source to {@link
 * #dispatchAutomaticTrigger}/{@link #dispatchDeploymentRefresh} - no ticket in this epic builds one
 * - this class's own "caller" is whatever test exercises it directly, the same "build the
 * classifier before its real input source exists" boundary used throughout this epic (see {@code
 * DocumentationGenerationOutcome}'s own javadoc for the precedent).
 *
 * <p><b>Idempotency is not re-solved here</b>: {@code documentation-workflow-policy.yaml}'s own
 * "Duplikat-Events müssen idempotent sein" is already satisfied by {@link
 * DocumentationCanonicalPackagePersister}'s existing idempotency-key mechanism (AIW-200) - two
 * dispatches of the same trigger for the same candidate reach the same {@link
 * DocumentationGenerationOrchestrator#generate}/{@link DocumentationCanonicalPackagePersister#persist}
 * call path with identical inputs, so the persister's own SHA-256(line id + candidate JSON) key
 * naturally returns the existing canonical version rather than creating a duplicate revision. This
 * class adds no second, parallel dedup mechanism.
 *
 * <p><b>On-demand generation needs no eligibility logic of its own</b> - see {@link
 * #generateOnDemand}, and {@link DocumentationWorkflowTriggerEvaluator}'s own javadoc for why.
 */
@Component
public class DocumentationTriggerService {

	private static final List<String> AUTOMATIC_TRIGGER_REASONS = List.of("INITIAL");
	private static final List<String> ON_DEMAND_REASONS = List.of("MANUAL_REGENERATION");

	private final DocumentationWorkflowTriggerEvaluator triggerEvaluator;
	private final DocumentationGenerationOrchestrator orchestrator;
	private final DocumentationCanonicalPackagePersister packagePersister;
	private final DocumentationProfileLoader profileLoader;
	private final DocumentationLineRepository lineRepository;

	DocumentationTriggerService(
			DocumentationWorkflowTriggerEvaluator triggerEvaluator,
			DocumentationGenerationOrchestrator orchestrator,
			DocumentationCanonicalPackagePersister packagePersister,
			DocumentationProfileLoader profileLoader,
			DocumentationLineRepository lineRepository) {
		this.triggerEvaluator = triggerEvaluator;
		this.orchestrator = orchestrator;
		this.packagePersister = packagePersister;
		this.profileLoader = profileLoader;
		this.lineRepository = lineRepository;
	}

	/**
	 * Dispatches {@code FULL_RELEASE_QA_FINALIZED}/{@code SCOPED_APPROVAL_RECORDED}: resolves the
	 * triggered profile (if the event's own conditions hold - see {@link
	 * DocumentationWorkflowTriggerEvaluator#resolveProfileRef}), generates, and canonicalizes on
	 * success. Returns {@link Optional#empty()} without ever invoking the model when the event's own
	 * conditions don't hold (e.g. a {@code FULL_RELEASE_QA_FINALIZED} for a {@code HOLD}-gated
	 * Candidate) - a distinct, cheaper outcome from a generation/validation failure.
	 */
	public Optional<TriggerDispatchResult> dispatchAutomaticTrigger(
			UUID projectId,
			UUID candidateId,
			UUID qaResultId,
			DocumentationTriggerEvent event,
			String targetLocale,
			boolean externallyConfirmedApproval) {
		Optional<String> resolvedProfileRef = triggerEvaluator.resolveProfileRef(event, candidateId, externallyConfirmedApproval);
		if (resolvedProfileRef.isEmpty()) {
			return Optional.empty();
		}

		DocumentationProfile profile = profileLoader.resolve(resolvedProfileRef.get());
		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidateId, qaResultId, profile, targetLocale);
		return Optional.of(persistIfSuccessful(projectId, profile, outcome, AUTOMATIC_TRIGGER_REASONS));
	}

	/**
	 * Dispatches {@code DEPLOYMENT_RECORDED}: refreshes every already-existing Documentation Line for
	 * this project (the same Candidate/QaResult each line's own profile was last generated
	 * against - re-deriving "the current release-path Candidate" for a project is not this method's
	 * job, matching {@link DocumentationWorkflowTriggerEvaluator}'s own "caller supplies, never
	 * discovers" boundary). Lines with no prior revision are skipped - a deployment cannot refresh
	 * something that was never published, and {@link #dispatchAutomaticTrigger}/{@link
	 * #generateOnDemand} are the paths that create the first revision of a line.
	 */
	public List<TriggerDispatchResult> dispatchDeploymentRefresh(UUID projectId, UUID candidateId, UUID qaResultId, String targetLocale) {
		String reason = triggerEvaluator.refreshGenerationReason(DocumentationTriggerEvent.DEPLOYMENT_RECORDED);
		List<DocumentationLine> lines = lineRepository.findByProjectId(projectId);

		return lines.stream()
				.filter(line -> line.getCurrentPackageVersionId().isPresent())
				.map(line -> {
					DocumentationProfile profile = profileLoader.resolve(line.getProfileRef());
					DocumentationGenerationOutcome outcome =
							orchestrator.generate(projectId, candidateId, qaResultId, profile, line.getLocale());
					return persistIfSuccessful(projectId, profile, outcome, List.of(reason));
				})
				.toList();
	}

	/**
	 * The {@code onDemand} generation path - loads the requested profile and calls the orchestrator
	 * directly, no {@link DocumentationWorkflowTriggerEvaluator} involved. {@code
	 * DocumentationContextPreflightValidator} (AIW-189), which {@link
	 * DocumentationGenerationOrchestrator#generate} already runs first, enforces exactly the
	 * on-demand conditions table ({@code CUSTOMER_HANDOVER} requires {@code PASS}, {@code
	 * TECHNICAL_HANDOVER} allows {@code PASS} or {@code HOLD}) - there is nothing further for this
	 * method to check.
	 */
	public TriggerDispatchResult generateOnDemand(UUID projectId, UUID candidateId, UUID qaResultId, String profileRef, String targetLocale) {
		DocumentationProfile profile = profileLoader.resolve(profileRef);
		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidateId, qaResultId, profile, targetLocale);
		return persistIfSuccessful(projectId, profile, outcome, ON_DEMAND_REASONS);
	}

	private TriggerDispatchResult persistIfSuccessful(
			UUID projectId, DocumentationProfile profile, DocumentationGenerationOutcome outcome, List<String> generationReasons) {
		if (outcome instanceof DocumentationGenerationOutcome.Success success) {
			DocumentationPackageVersion packageVersion = packagePersister.persist(projectId, success, profile, generationReasons);
			return new TriggerDispatchResult(outcome, Optional.of(packageVersion));
		}
		return new TriggerDispatchResult(outcome, Optional.empty());
	}

	/** Pairs the orchestrator's own typed outcome with the canonical package version it produced, if any. */
	public record TriggerDispatchResult(DocumentationGenerationOutcome outcome, Optional<DocumentationPackageVersion> packageVersion) {}
}
