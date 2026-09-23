package io.casehub.ledger.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportMediaTypeTest {

    @Test
    void pdf() {
        assertThat(ReportMediaType.fromAcceptHeader("application/pdf")).isEqualTo(OutputFormat.PDF);
    }

    @Test
    void html() {
        assertThat(ReportMediaType.fromAcceptHeader("text/html")).isEqualTo(OutputFormat.HTML);
    }

    @Test
    void csv() {
        assertThat(ReportMediaType.fromAcceptHeader("text/csv")).isEqualTo(OutputFormat.CSV);
    }

    @Test
    void jsonLd() {
        assertThat(ReportMediaType.fromAcceptHeader("application/ld+json")).isEqualTo(OutputFormat.JSON_LD);
    }

    @Test
    void json() {
        assertThat(ReportMediaType.fromAcceptHeader("application/json")).isEqualTo(OutputFormat.JSON);
    }

    @Test
    void nullHeader() {
        assertThat(ReportMediaType.fromAcceptHeader(null)).isEqualTo(OutputFormat.JSON);
    }

    @Test
    void unknownType_defaultsToJson() {
        assertThat(ReportMediaType.fromAcceptHeader("image/png")).isEqualTo(OutputFormat.JSON);
    }
}
