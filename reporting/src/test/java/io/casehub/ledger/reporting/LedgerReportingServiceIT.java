package io.casehub.ledger.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.compliance.AuditEntry;
import io.casehub.ledger.core.compliance.AuditTrailExport;
import io.casehub.ledger.core.compliance.ComplianceReport;
import io.casehub.ledger.core.compliance.ComplianceSummary;
import io.casehub.ledger.core.compliance.DecisionRecord;
import io.casehub.ledger.core.compliance.SubjectAuditTrail;
import io.casehub.ledger.core.compliance.VerificationSummary;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class LedgerReportingServiceIT {

    @Inject
    LedgerReportingService service;

    @Test
    void renderComplianceReport_json_validOutput() {
        final byte[] result = service.renderComplianceReport(sampleReport(), OutputFormat.JSON);
        final String json = new String(result);
        assertThat(json).contains("\"totalDecisions\"");
        assertThat(json).contains("\"tenancyId\"");
    }

    @Test
    void renderComplianceReport_csv_hasHeaderAndRows() {
        final byte[] result = service.renderComplianceReport(sampleReport(), OutputFormat.CSV);
        final String csv = new String(result);
        assertThat(csv).contains("entryId,entryType");
        assertThat(csv).contains("alg-v1");
    }

    @Test
    void renderComplianceReport_html_containsHtmlTags() {
        final byte[] result = service.renderComplianceReport(sampleReport(), OutputFormat.HTML);
        final String html = new String(result);
        assertThat(html).contains("<html");
        assertThat(html).contains("Art.12");
        assertThat(html).contains("alg-v1");
    }

    @Test
    void renderComplianceReport_pdf_throwsWithNoOpGenerator() {
        assertThatThrownBy(() -> service.renderComplianceReport(sampleReport(), OutputFormat.PDF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PDF generation unavailable");
    }

    @Test
    void renderAuditTrail_json_validOutput() {
        final byte[] result = service.renderAuditTrail(sampleExport(), OutputFormat.JSON);
        final String json = new String(result);
        assertThat(json).contains("tenancyId");
        assertThat(json).contains("subjects");
    }

    @Test
    void renderAuditTrail_csv_hasHeaderAndRows() {
        final byte[] result = service.renderAuditTrail(sampleExport(), OutputFormat.CSV);
        final String csv = new String(result);
        assertThat(csv).contains("subjectId,entryId");
        assertThat(csv).contains("actor-1");
    }

    @Test
    void renderAuditTrail_html_containsHtmlTags() {
        final byte[] result = service.renderAuditTrail(sampleExport(), OutputFormat.HTML);
        final String html = new String(result);
        assertThat(html).contains("<html");
        assertThat(html).contains("Audit Trail");
    }

    private ComplianceReport sampleReport() {
        final DecisionRecord d = new DecisionRecord(
                UUID.randomUUID(), "PlainLedgerEntry", Instant.now(),
                "actor-1", "alg-v1", 0.9, null, null, true, null, null);
        final ComplianceSummary summary = ComplianceSummary.fromDecisions(List.of(d));
        return new ComplianceReport("actor-1", null, "default",
                Instant.now().minusSeconds(3600), Instant.now(),
                1, List.of(d), summary, null);
    }

    private AuditTrailExport sampleExport() {
        final AuditEntry entry = new AuditEntry(
                UUID.randomUUID(), "PlainLedgerEntry", Instant.now(),
                "actor-1", "abc123hash", 1);
        final UUID subjectId = UUID.randomUUID();
        final SubjectAuditTrail trail = new SubjectAuditTrail(
                subjectId, List.of(entry), "merkle-root-abc", true, null);
        final VerificationSummary verification = VerificationSummary.fromTrails(List.of(trail));
        return new AuditTrailExport("default",
                Instant.now().minusSeconds(3600), Instant.now(), Instant.now(),
                List.of(trail), verification);
    }
}
