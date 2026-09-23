package io.casehub.ledger.core.compliance;

import java.util.List;

public record VerificationSummary(
        int totalSubjects,
        int validChains,
        int invalidChains,
        int totalEntries) {

    public static VerificationSummary fromTrails(final List<SubjectAuditTrail> trails) {
        int valid = 0;
        int invalid = 0;
        int entries = 0;
        for (final SubjectAuditTrail t : trails) {
            if (t.chainValid()) {
                valid++;
            } else {
                invalid++;
            }
            entries += t.entries().size();
        }
        return new VerificationSummary(trails.size(), valid, invalid, entries);
    }
}
