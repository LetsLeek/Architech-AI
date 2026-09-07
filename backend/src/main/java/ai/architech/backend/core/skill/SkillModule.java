package ai.architech.backend.core.skill;

/** One instructional module's content, already inlined - never just a file reference. */
public record SkillModule(String filename, String content) {}
