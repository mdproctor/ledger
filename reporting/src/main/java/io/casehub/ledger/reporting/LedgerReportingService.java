package io.casehub.ledger.reporting;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.ledger.core.compliance.AuditTrailExport;
import io.casehub.ledger.core.compliance.ComplianceReport;
import io.casehub.ledger.core.compliance.ReportFormat;
import io.casehub.ledger.core.compliance.SubjectAuditTrail;
import io.casehub.ledger.core.compliance.AuditEntry;
import io.casehub.platform.api.pdf.PdfAConformance;
import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LedgerReportingService {

    @Inject
    PdfGenerator pdfGenerator;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @io.quarkus.qute.Location("reports/compliance-art12")
    Template complianceArt12;

    @Inject
    @io.quarkus.qute.Location("reports/audit-trail")
    Template auditTrail;

    public byte[] renderComplianceReport(final ComplianceReport report, final OutputFormat format) {
        return switch (format) {
            case JSON -> report.format(ReportFormat.PLAIN_JSON).getBytes(UTF_8);
            case JSON_LD -> report.format(ReportFormat.JSON_LD).getBytes(UTF_8);
            case CSV -> report.format(ReportFormat.CSV).getBytes(UTF_8);
            case HTML -> renderHtml(complianceArt12, report);
            case PDF -> renderPdf(complianceArt12, report, "EU AI Act Art.12 Compliance Report");
        };
    }

    public byte[] renderAuditTrail(final AuditTrailExport export, final OutputFormat format) {
        return switch (format) {
            case JSON, JSON_LD -> serializeJson(export);
            case CSV -> auditTrailToCsv(export);
            case HTML -> renderHtml(auditTrail, export);
            case PDF -> renderPdf(auditTrail, export, "Audit Trail Export");
        };
    }

    private byte[] renderHtml(final Template template, final Object model) {
        return template.data("report", model).data("now", Instant.now()).render().getBytes(UTF_8);
    }

    private byte[] renderPdf(final Template template, final Object model, final String title) {
        final String html = template.data("report", model).data("now", Instant.now()).render();
        final PdfOptions opts = new PdfOptions(title, "casehub-ledger",
                Instant.now(), "compliance", PdfAConformance.PDFA_2_B);
        return pdfGenerator.generateFromHtml(html, opts)
                .orElseThrow(() -> new IllegalStateException(
                        "PDF generation unavailable — add platform-pdf to classpath"));
    }

    private byte[] serializeJson(final Object model) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(model);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private byte[] auditTrailToCsv(final AuditTrailExport export) {
        final StringBuilder sb = new StringBuilder();
        sb.append("subjectId,entryId,entryType,occurredAt,actorId,digest,sequenceNumber,chainValid\n");
        for (final SubjectAuditTrail subject : export.subjects()) {
            for (final AuditEntry e : subject.entries()) {
                sb.append(csvField(subject.subjectId().toString())).append(',');
                sb.append(csvField(e.entryId() != null ? e.entryId().toString() : "")).append(',');
                sb.append(csvField(e.entryType())).append(',');
                sb.append(csvField(e.occurredAt() != null ? e.occurredAt().toString() : "")).append(',');
                sb.append(csvField(e.actorId())).append(',');
                sb.append(csvField(e.digest())).append(',');
                sb.append(e.sequenceNumber()).append(',');
                sb.append(subject.chainValid()).append('\n');
            }
        }
        return sb.toString().getBytes(UTF_8);
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
