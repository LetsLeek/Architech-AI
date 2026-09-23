package ai.architech.backend.core.documentation.profiles;

/** A profile's {@code deterministicReports} entry: one Core-generated report it requires. */
public record DeterministicReportSpec(String reportType, boolean required, String deliveryDisposition) {}
