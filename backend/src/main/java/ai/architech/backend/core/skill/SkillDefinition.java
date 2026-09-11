package ai.architech.backend.core.skill;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A parsed {@code skill.yaml} with its instructional modules already loaded and inlined,
 * in the deterministic order their filenames impose (zero-padded numeric prefixes, e.g.
 * {@code 01-...md} before {@code 02-...md}). A module is compositional instruction content
 * for a single skill invocation, never a separate agent execution.
 */
public record SkillDefinition(
		int schemaVersion, String id, String name, int version, String description, List<SkillModule> modules) {

	/** The full instructional content for this skill: every module's content, in order. */
	public String inlinedContent() {
		return modules.stream().map(SkillModule::content).collect(Collectors.joining("\n\n"));
	}
}
