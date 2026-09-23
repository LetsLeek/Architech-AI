package ai.architech.backend.core.documentation.profiles;

/** One entry of a {@link SemanticDocumentSpec}'s {@code sections} list. */
public record DocumentSectionSpec(String sectionType, boolean required, boolean allowEmptyAgentBlocks) {}
