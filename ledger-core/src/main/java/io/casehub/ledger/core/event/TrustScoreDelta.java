package io.casehub.ledger.core.event;

public record TrustScoreDelta(
        String actorId,
        double previousScore,
        double newScore,
        double previousGlobalScore,
        double newGlobalScore) {
}
