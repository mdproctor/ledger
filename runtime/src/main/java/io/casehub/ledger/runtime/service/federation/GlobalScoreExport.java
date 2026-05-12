package io.casehub.ledger.runtime.service.federation;

import java.time.Instant;

/** Bayesian Beta global trust score for an actor. */
public record GlobalScoreExport(
        double alpha,
        double beta,
        double trustScore,
        int decisionCount,
        int attestationPositive,
        int attestationNegative,
        Instant lastComputedAt) {
}
