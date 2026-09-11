package io.casehub.ledger.core.compliance;

import java.time.Instant;
import java.util.UUID;

public record DecisionRecord(
        UUID entryId,
        Instant occurredAt,
        String algorithmRef,
        Double confidenceScore,
        String contestationUri,
        Boolean humanOverrideAvailable,
        String sourceEntityType,
        String sourceEntityId) {
}
