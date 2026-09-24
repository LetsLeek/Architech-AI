package ai.architech.backend.core.documentation.rendering;

import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersionRepository;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextRepository;
import ai.architech.backend.core.documentation.locale.TerminologyRegistry;
import ai.architech.backend.core.documentation.locale.TerminologyRegistryLoader;
import ai.architech.backend.core.documentation.profiles.DocumentSectionSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import ai.architech.backend.core.documentation.profiles.SemanticDocumentSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Renders one canonical {@link DocumentationPackageVersion} (AIW-200) into a Markdown document
 * (AIW-201) - a separate, purely additive downstream step: it only ever reads canonical data
 * ({@code DocumentationPackageVersion}/{@code ArtifactVersion}/{@code DocumentationContext} rows)
 * and writes its own new {@link DocumentationRender} row. Any failure during rendering therefore
 * cannot corrupt canonical data by construction - there is nothing canonical for a render failure
 * to have touched (per {@code INTEGRATION_HANDOFF.md} point 7: "Rendering errors do not mutate
 * semantic artifacts"). A rendering exception is a plain, real thrown exception; no typed error
 * hierarchy is invented for it.
 *
 * <p><b>Scope: {@code MARKDOWN} only for V1</b> - {@code documentation-render.schema.json}'s
 * {@code format} enum also lists {@code HTML}/{@code PDF}; PDF generation needs a new PDF-library
 * dependency (a bigger, separate decision this ticket does not make unilaterally), and HTML is a
 * genuinely different templating concern, not just a text-format variant of Markdown. Both are
 * real, deliberately deferred follow-up work, not an oversight.
 *
 * <p><b>Core-owned deterministic block insertion</b> (the substantial part of this ticket's own
 * work): {@link DocumentationProfile#composerDeterministicBlocks()} was loaded by AIW-188 and
 * consulted by AIW-190 only to decide *whether* to emit a context-state proof - none of those five
 * markers were ever turned into actual rendered content anywhere in AIW-190 through AIW-200.
 * Turning them into real, locale-aware prose is this class's own job ({@code DOC-GEN-005}: "Core
 * inserts deterministic blocks", {@code rules/documentation-integrity/RULE.md}). Recognized markers:
 * {@code SCOPED_MISSING_DEPLOYMENT_IF_APPLICABLE}, {@code SCOPED_SECTION_UNAVAILABLE_IF_APPLICABLE},
 * {@code SCOPED_ZERO_DISCLOSABLE_FINDINGS_IF_APPLICABLE}, {@code SCOPED_CORE_LIFECYCLE_STATUS},
 * {@code DETERMINISTIC_REPORT_REFS} - see {@link #renderDeterministicBlock} for exactly what each
 * one renders and under what condition.
 */
@Component
public class DocumentationMarkdownRenderer {

	static final String RENDERER_VERSION = "documentation-markdown-renderer@1.0.0";
	private static final String FORMAT = "MARKDOWN";

	private static final Map<String, Map<String, String>> DOCUMENT_TITLES = Map.of(
			"CUSTOMER_WEBSITE_HANDOVER", Map.of("en-GB", "Customer Website Handover", "de-AT", "Kunden-Website-Übergabe"),
			"TECHNICAL_HANDOVER_GUIDE", Map.of("en-GB", "Technical Handover Guide", "de-AT", "Technischer Übergabeleitfaden"));

	private static final Map<String, Map<String, String>> FIXED_SENTENCES = Map.of(
			"SCOPED_MISSING_DEPLOYMENT_IF_APPLICABLE",
			Map.of(
					"en-GB", "Deployment information is not yet recorded for this release.",
					"de-AT", "Für dieses Release liegen noch keine Deployment-Informationen vor."),
			"SCOPED_SECTION_UNAVAILABLE_IF_APPLICABLE",
			Map.of(
					"en-GB", "This section is not available for this release.",
					"de-AT", "Dieser Abschnitt ist für dieses Release nicht verfügbar."),
			"SCOPED_ZERO_DISCLOSABLE_FINDINGS_IF_APPLICABLE",
			Map.of(
					"en-GB", "No known limitations have been disclosed for this release.",
					"de-AT", "Für dieses Release wurden keine bekannten Einschränkungen offengelegt."));

	private static final Map<String, Map<String, String>> LIFECYCLE_STATUS_TEXT = Map.of(
			"PASS",
			Map.of(
					"en-GB", "This website has passed full release quality assurance.",
					"de-AT", "Diese Website hat die vollständige Freigabe-Qualitätssicherung bestanden."),
			"HOLD",
			Map.of(
					"en-GB", "This website's release quality assurance is currently on hold.",
					"de-AT", "Die Freigabe-Qualitätssicherung dieser Website ist derzeit ausgesetzt."));

	private static final Map<String, String> REFERENCES_LABEL =
			Map.of("en-GB", "Deterministic reports:", "de-AT", "Deterministische Berichte:");

	private final DocumentationPackageVersionRepository packageVersionRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final DocumentationContextRepository contextRepository;
	private final DocumentationProfileLoader profileLoader;
	private final TerminologyRegistryLoader terminologyRegistryLoader;
	private final DocumentationRenderRepository renderRepository;
	private final ObjectMapper objectMapper;

	DocumentationMarkdownRenderer(
			DocumentationPackageVersionRepository packageVersionRepository,
			ArtifactVersionRepository artifactVersionRepository,
			DocumentationContextRepository contextRepository,
			DocumentationProfileLoader profileLoader,
			TerminologyRegistryLoader terminologyRegistryLoader,
			DocumentationRenderRepository renderRepository,
			ObjectMapper objectMapper) {
		this.packageVersionRepository = packageVersionRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.contextRepository = contextRepository;
		this.profileLoader = profileLoader;
		this.terminologyRegistryLoader = terminologyRegistryLoader;
		this.renderRepository = renderRepository;
		this.objectMapper = objectMapper;
	}

	public DocumentationRender render(UUID packageVersionId) {
		DocumentationPackageVersion packageVersion = packageVersionRepository.findById(packageVersionId).orElseThrow();
		JsonNode packageContent = objectMapper.readTree(packageVersion.getContentJson());

		String locale = packageContent.path("locale").asString();
		String profileRef = packageContent.path("profileRef").asString();
		DocumentationProfile profile = profileLoader.resolve(profileRef);
		TerminologyRegistry terminology = terminologyRegistryLoader.resolve(locale);

		DocumentationContext context =
				contextRepository.findById(UUID.fromString(packageContent.path("contextRef").asString())).orElseThrow();
		JsonNode contextContent = objectMapper.readTree(context.getContentJson());
		JsonNode contextStates = contextContent.path("contextStates");
		JsonNode qaState = contextContent.path("qaState");

		UUID semanticVersionId = UUID.fromString(packageContent.path("semanticArtifactRefs").get(0).asString());
		ArtifactVersion semanticVersion = artifactVersionRepository.findById(semanticVersionId).orElseThrow();
		JsonNode document = objectMapper.readTree(semanticVersion.getContent()).path("documents").get(0);

		List<ReportInfo> reports = resolveReports(packageContent.path("deterministicReportRefs"));

		String markdown =
				renderDocument(document, profile, terminology, locale, contextStates, qaState, reports);

		UUID renderId = UUID.randomUUID();
		String contentJson = buildRenderEnvelope(renderId, semanticVersionId);
		DocumentationRender render =
				new DocumentationRender(renderId, packageVersionId, FORMAT, RENDERER_VERSION, markdown, contentJson);
		return renderRepository.saveAndFlush(render);
	}

	private String renderDocument(
			JsonNode document,
			DocumentationProfile profile,
			TerminologyRegistry terminology,
			String locale,
			JsonNode contextStates,
			JsonNode qaState,
			List<ReportInfo> reports) {
		Map<String, JsonNode> candidateSectionsByType = new LinkedHashMap<>();
		for (JsonNode section : document.path("sections")) {
			candidateSectionsByType.put(section.path("sectionType").asString(), section);
		}

		StringBuilder md = new StringBuilder();
		String documentType = document.path("documentType").asString();
		md.append("# ").append(title(DOCUMENT_TITLES, documentType, locale)).append("\n\n");

		SemanticDocumentSpec documentSpec = profile.semanticDocuments().get(0);
		Map<String, List<String>> deterministicBlocks = profile.composerDeterministicBlocks();

		for (DocumentSectionSpec sectionSpec : documentSpec.sections()) {
			String sectionType = sectionSpec.sectionType();
			md.append("## ").append(terminology.sectionTitles().getOrDefault(sectionType, sectionType)).append("\n\n");

			JsonNode candidateSection = candidateSectionsByType.get(sectionType);
			if (candidateSection != null) {
				renderBlocks(md, candidateSection.path("blocks"));
			}

			for (String marker : deterministicBlocks.getOrDefault(sectionType, List.of())) {
				renderDeterministicBlock(md, marker, sectionType, locale, contextStates, qaState, reports);
			}
		}

		return md.toString();
	}

	private void renderBlocks(StringBuilder md, JsonNode blocks) {
		for (JsonNode block : blocks) {
			String blockType = block.path("blockType").asString(null);
			if ("NARRATIVE".equals(blockType)) {
				for (JsonNode claim : block.path("claims")) {
					md.append(claim.path("text").asString()).append("\n\n");
				}
			} else if ("LIST".equals(blockType)) {
				for (JsonNode item : block.path("items")) {
					for (JsonNode claim : item.path("claims")) {
						md.append("- ").append(claim.path("text").asString()).append("\n");
					}
				}
				md.append("\n");
			}
		}
	}

	private void renderDeterministicBlock(
			StringBuilder md,
			String marker,
			String sectionType,
			String locale,
			JsonNode contextStates,
			JsonNode qaState,
			List<ReportInfo> reports) {
		switch (marker) {
			case "SCOPED_MISSING_DEPLOYMENT_IF_APPLICABLE" -> {
				if (hasContextState(contextStates, "MISSING_AUTHORITY", "DEPLOYMENT", null)) {
					md.append(localized(FIXED_SENTENCES, marker, locale)).append("\n\n");
				}
			}
			case "SCOPED_SECTION_UNAVAILABLE_IF_APPLICABLE" -> {
				if (hasContextState(contextStates, "SECTION_AUTHORITY_UNAVAILABLE", null, sectionType)) {
					md.append(localized(FIXED_SENTENCES, marker, locale)).append("\n\n");
				}
			}
			case "SCOPED_ZERO_DISCLOSABLE_FINDINGS_IF_APPLICABLE" -> {
				if (hasContextState(contextStates, "NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED", null, null)) {
					md.append(localized(FIXED_SENTENCES, marker, locale)).append("\n\n");
				}
			}
			case "SCOPED_CORE_LIFECYCLE_STATUS" -> {
				String gate = qaState.path("gate").asString("PASS");
				md.append(title(LIFECYCLE_STATUS_TEXT, gate, locale)).append("\n\n");
			}
			case "DETERMINISTIC_REPORT_REFS" -> {
				md.append(REFERENCES_LABEL.getOrDefault(locale, REFERENCES_LABEL.get("en-GB"))).append("\n\n");
				for (ReportInfo report : reports) {
					md.append("- ").append(report.reportType()).append(": `").append(report.artifactVersionId()).append("`\n");
				}
				md.append("\n");
			}
			default -> {
				// Every marker the frozen profiles actually declare is handled above; an
				// unrecognized one is silently skipped rather than failing rendering outright -
				// this is presentation-only output, not a correctness gate.
			}
		}
	}

	private boolean hasContextState(JsonNode contextStates, String kind, String domain, String sectionType) {
		for (JsonNode state : contextStates) {
			if (!kind.equals(state.path("kind").asString(null))) {
				continue;
			}
			if (domain != null && !domain.equals(state.path("affectedAuthorityDomain").asString(null))) {
				continue;
			}
			if (sectionType != null && !sectionType.equals(state.path("sectionType").asString(null))) {
				continue;
			}
			return true;
		}
		return false;
	}

	private List<ReportInfo> resolveReports(JsonNode reportRefs) {
		List<ReportInfo> reports = new ArrayList<>();
		for (JsonNode refNode : reportRefs) {
			UUID artifactVersionId = UUID.fromString(refNode.asString());
			ArtifactVersion version = artifactVersionRepository.findById(artifactVersionId).orElseThrow();
			JsonNode reportJson = objectMapper.readTree(version.getContent());
			reports.add(new ReportInfo(reportJson.path("reportType").asString("UNKNOWN"), artifactVersionId));
		}
		return reports;
	}

	private String buildRenderEnvelope(UUID renderId, UUID artifactVersionRef) {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("renderId", renderId.toString());
		root.put("artifactVersionRef", artifactVersionRef.toString());
		root.put("format", FORMAT);
		root.put("rendererVersion", RENDERER_VERSION);
		root.put("outputRef", renderId.toString());
		root.put("generatedAt", Instant.now().toString());
		return root.toString();
	}

	private String title(Map<String, Map<String, String>> table, String key, String locale) {
		Map<String, String> byLocale = table.get(key);
		if (byLocale == null) {
			return key;
		}
		return byLocale.getOrDefault(locale, byLocale.get("en-GB"));
	}

	private String localized(Map<String, Map<String, String>> table, String key, String locale) {
		return title(table, key, locale);
	}

	private record ReportInfo(String reportType, UUID artifactVersionId) {}
}
