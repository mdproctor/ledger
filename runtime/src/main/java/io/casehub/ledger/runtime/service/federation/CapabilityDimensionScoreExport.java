package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;

/**
 * Per-capability quality dimension score for one actor.
 * Mirrors {@link DimensionScoreExport} but scoped to a specific capability tag.
 */
public record CapabilityDimensionScoreExport(
        String capabilityTag,
        String dimension,
        double score,
        int sampleCount,
        Instant lastComputedAt) {
}
