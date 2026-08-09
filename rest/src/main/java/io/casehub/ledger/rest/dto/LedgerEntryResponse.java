package io.casehub.ledger.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID subjectId,
        String tenancyId,
        int sequenceNumber,
        String entryType,
        String actorId,
        String actorType,
        String actorRole,
        Instant occurredAt,
        String digest,
        String traceId,
        UUID causedByEntryId,
        String payload) {
}
