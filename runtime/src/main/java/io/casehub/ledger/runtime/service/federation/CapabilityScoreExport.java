package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;

/** Bayesian Beta trust score for an actor scoped to a single capability tag. */
public record CapabilityScoreExport(
        String capabilityTag,
        double alpha,
        double beta,
        double trustScore,
        int decisionCount,
        int attestationPositive,
        int attestationNegative,
        Instant lastComputedAt) {
}
