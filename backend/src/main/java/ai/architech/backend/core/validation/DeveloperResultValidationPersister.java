package ai.architech.backend.core.validation;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Persists exactly one {@link DeveloperResultValidator#validate} outcome as a {@link
 * DeveloperResultValidationRecord} (AIW-161) - the durable evidence trail
 * {@code DeveloperExecutionAuditTrailAssembler} reads back, closing the gap {@link
 * DeveloperResultValidator}'s own javadoc names: that class produces a result but has "no
 * DeveloperAgentRunner yet to wire this into", so nothing previously made a validation outcome
 * durable at all - {@link DeveloperResultValidator#validate} itself does no persistence, exactly
 * the same idiom {@code WebsiteImplementationCandidatePromoter}/{@code
 * RunnerVerificationEvidencePersister} already establish (the deterministic-computation class and
 * the persistence class stay separate).
 *
 * <p>Builds {@code issues} from a Jackson tree, never by handing the {@link
 * DeveloperResultValidationIssue} records straight to data-binding - matching the tree-based
 * serialization idiom already used everywhere else in this codebase.
 */
@Component
public class DeveloperResultValidationPersister {

	private final DeveloperResultValidationRecordRepository repository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	DeveloperResultValidationPersister(DeveloperResultValidationRecordRepository repository) {
		this.repository = repository;
	}

	public DeveloperResultValidationRecord persist(UUID agentExecutionId, DeveloperResultValidationResult result) {
		ArrayNode issues = objectMapper.createArrayNode();
		for (DeveloperResultValidationIssue issue : result.issues()) {
			ObjectNode issueNode = objectMapper.createObjectNode();
			issueNode.put("validator", issue.validator());
			issueNode.put("ref", issue.ref());
			issueNode.put("reason", issue.reason());
			issues.add(issueNode);
		}

		return repository.saveAndFlush(
				new DeveloperResultValidationRecord(agentExecutionId, result.valid(), objectMapper.writeValueAsString(issues)));
	}
}
