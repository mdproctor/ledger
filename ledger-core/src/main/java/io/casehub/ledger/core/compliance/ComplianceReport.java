package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceReport(
        String actorId,
        UUID subjectId,
        Instant from,
        Instant to,
        int totalDecisions,
        List<DecisionRecord> decisions,
        String merkleRootAtGeneration) {

    public String format(final ReportFormat format) {
        return switch (format) {
            case PLAIN_JSON -> toPlainJson();
            case JSON_LD -> toJsonLd();
            case CSV -> toCsv();
        };
    }

    private String toPlainJson() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"actorId\": ").append(jsonStr(actorId)).append(",\n");
        sb.append("  \"subjectId\": ").append(jsonStr(subjectId != null ? subjectId.toString() : null)).append(",\n");
        sb.append("  \"from\": ").append(jsonStr(from.toString())).append(",\n");
        sb.append("  \"to\": ").append(jsonStr(to.toString())).append(",\n");
        sb.append("  \"totalDecisions\": ").append(totalDecisions).append(",\n");
        sb.append("  \"merkleRootAtGeneration\": ").append(jsonStr(merkleRootAtGeneration)).append(",\n");
        sb.append("  \"decisions\": [\n");
        for (int i = 0; i < decisions.size(); i++) {
            sb.append(decisionToJson(decisions.get(i)));
            if (i < decisions.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    private String toJsonLd() {
        final String base = toPlainJson();
        return "{\n" +
               "  \"@context\": \"https://www.w3.org/ns/prov\",\n" +
               "  \"@type\": \"ComplianceReport\",\n" +
               "  \"report\": " + base.replace("\n", "\n  ") + "\n}";
    }

    private String toCsv() {
        final StringBuilder sb = new StringBuilder();
        sb.append("entryId,occurredAt,algorithmRef,confidenceScore,contestationUri," +
                  "humanOverrideAvailable,sourceEntityType,sourceEntityId\n");
        for (final DecisionRecord d : decisions) {
            sb.append(csvField(d.entryId() != null ? d.entryId().toString() : "")).append(',');
            sb.append(csvField(d.occurredAt() != null ? d.occurredAt().toString() : "")).append(',');
            sb.append(csvField(d.algorithmRef())).append(',');
            sb.append(d.confidenceScore() != null ? d.confidenceScore() : "").append(',');
            sb.append(csvField(d.contestationUri())).append(',');
            sb.append(d.humanOverrideAvailable() != null ? d.humanOverrideAvailable() : "").append(',');
            sb.append(csvField(d.sourceEntityType())).append(',');
            sb.append(csvField(d.sourceEntityId())).append('\n');
        }
        return sb.toString();
    }

    private static String decisionToJson(final DecisionRecord d) {
        return "    {" +
               "\"entryId\": " + jsonStr(d.entryId() != null ? d.entryId().toString() : null) +
               ", \"occurredAt\": " + jsonStr(d.occurredAt() != null ? d.occurredAt().toString() : null) +
               ", \"algorithmRef\": " + jsonStr(d.algorithmRef()) +
               ", \"confidenceScore\": " + d.confidenceScore() +
               ", \"contestationUri\": " + jsonStr(d.contestationUri()) +
               ", \"humanOverrideAvailable\": " + d.humanOverrideAvailable() +
               ", \"sourceEntityType\": " + jsonStr(d.sourceEntityType()) +
               ", \"sourceEntityId\": " + jsonStr(d.sourceEntityId()) +
               "}";
    }

    private static String jsonStr(final String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static String csvField(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
