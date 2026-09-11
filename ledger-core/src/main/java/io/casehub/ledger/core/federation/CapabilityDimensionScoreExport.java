package io.casehub.ledger.core.federation;

import java.time.Instant;

public record CapabilityDimensionScoreExport(
        String capabilityTag,
        String dimension,
        double score,
        int sampleCount,
        Instant lastComputedAt) {
}
