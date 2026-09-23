package io.casehub.ledger.reporting;

public final class ReportMediaType {

    private ReportMediaType() {}

    public static OutputFormat fromAcceptHeader(final String accept) {
        if (accept == null) return OutputFormat.JSON;
        if (accept.contains("application/pdf")) return OutputFormat.PDF;
        if (accept.contains("text/html")) return OutputFormat.HTML;
        if (accept.contains("text/csv")) return OutputFormat.CSV;
        if (accept.contains("application/ld+json")) return OutputFormat.JSON_LD;
        return OutputFormat.JSON;
    }
}
