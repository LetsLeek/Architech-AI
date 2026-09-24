package ai.architech.backend.core.documentation.profiles;

import java.util.List;

/** A profile's {@code semanticDocuments} entry: the one model-generated document it requires. */
public record SemanticDocumentSpec(String documentType, boolean required, List<DocumentSectionSpec> sections) {}
