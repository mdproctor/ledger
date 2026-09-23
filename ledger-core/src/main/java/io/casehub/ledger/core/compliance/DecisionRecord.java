package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.UUID;

public record DecisionRecord(
        UUID entryId,
        String entryType,
        Instant occurredAt,
        String actorId,
        String algorithmRef,
        Double confidenceScore,
        String planRef,
        String contestationUri,
        Boolean humanOverrideAvailable,
        String sourceEntityType,
        String sourceEntityId) {
}
