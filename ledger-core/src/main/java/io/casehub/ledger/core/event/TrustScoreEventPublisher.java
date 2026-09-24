package io.casehub.ledger.core.event;

public interface TrustScoreEventPublisher {
    void publishFull(TrustScoreFullPayload payload);
    void publishDelta(TrustScoreDeltaPayload payload);
    void publishNotify(TrustScoreComputedAt payload);
    boolean needsDeltaPayload();
}
