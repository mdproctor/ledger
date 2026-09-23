package io.casehub.ledger.core.compliance;

import java.util.List;
import java.util.UUID;

public record SubjectAuditTrail(
        UUID subjectId,
        List<AuditEntry> entries,
        String merkleRoot,
        boolean chainValid,
        String provJsonLd) {
}
