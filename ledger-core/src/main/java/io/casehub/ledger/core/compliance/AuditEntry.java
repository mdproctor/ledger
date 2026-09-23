package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        UUID entryId,
        String entryType,
        Instant occurredAt,
        String actorId,
        String digest,
        int sequenceNumber) {
}
