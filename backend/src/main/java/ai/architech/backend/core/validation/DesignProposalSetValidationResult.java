package ai.architech.backend.core.validation;

import java.util.List;

public record DesignProposalSetValidationResult(boolean valid, List<DesignProposalSetValidationIssue> issues) {

	public static DesignProposalSetValidationResult passed() {
		return new DesignProposalSetValidationResult(true, List.of());
	}
}
