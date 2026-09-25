package ai.architech.backend.projecttype.website;

import java.util.List;

/** The aggregate result of driving all three A/B/C siblings to completion (or failure) for one project. */
public record WebsiteGenerationOutcome(List<WebsiteGenerationSiblingOutcome> siblings) {

	public boolean allSucceeded() {
		return siblings.stream().allMatch(sibling -> sibling.candidate() != null);
	}
}
