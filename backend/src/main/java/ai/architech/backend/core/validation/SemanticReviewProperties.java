package ai.architech.backend.core.validation;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every semantic review category (see {@link DesignProposalSetSemanticReviewer}) is a
 * blocking integrity violation by default - every one of them maps to an explicit rule in
 * {@code project-types/website/rules/design-integrity/RULE.md}, not a mere style preference.
 * {@code nonBlockingCategories} lets a future workflow policy deliberately downgrade specific
 * categories to advisory-only without a code change - empty (nothing downgraded) until a real
 * policy decision says otherwise.
 */
@ConfigurationProperties(prefix = "architech.designer.semantic-review")
public record SemanticReviewProperties(Set<String> nonBlockingCategories) {

	public SemanticReviewProperties {
		nonBlockingCategories = nonBlockingCategories == null ? Set.of() : nonBlockingCategories;
	}
}
