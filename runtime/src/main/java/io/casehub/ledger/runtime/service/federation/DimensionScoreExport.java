package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;

/**
 * Continuous quality dimension score for an actor.
 *
 * <p>
 * {@code sampleCount} is {@code attestationPositive + attestationNegative} from the source row.
 * The positive/negative split is not preserved — DIMENSION scores are approximated as priors
 * and recomputed from raw attestations on the next {@link io.casehub.ledger.runtime.service.TrustScoreJob} run.
 */
public record DimensionScoreExport(
        String dimension,
        double score,
        int sampleCount,
        Instant lastComputedAt) {
}
