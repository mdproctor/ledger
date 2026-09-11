package io.casehub.ledger.core.federation;

import java.time.Instant;

public record GlobalScoreExport(
        double alpha,
        double beta,
        double trustScore,
        int decisionCount,
        int attestationPositive,
        int attestationNegative,
        Instant lastComputedAt) {
}
