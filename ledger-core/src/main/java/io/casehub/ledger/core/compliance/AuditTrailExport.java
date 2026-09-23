package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.List;

public record AuditTrailExport(
        String tenancyId,
        Instant from,
        Instant to,
        Instant generatedAt,
        List<SubjectAuditTrail> subjects,
        VerificationSummary verification) {
}
