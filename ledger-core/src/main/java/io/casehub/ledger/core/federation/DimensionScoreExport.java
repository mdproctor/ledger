package io.casehub.ledger.core.federation;

import java.time.Instant;

public record DimensionScoreExport(
        String dimension,
        double score,
        int sampleCount,
        Instant lastComputedAt) {
}
