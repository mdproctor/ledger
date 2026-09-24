package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.core.config.TrustScoreProperties;
import io.casehub.ledger.core.event.TrustScoreComputedAt;
import io.casehub.ledger.core.event.TrustScoreDelta;
import io.casehub.ledger.core.event.TrustScoreDeltaPayload;
import io.casehub.ledger.core.event.TrustScoreEventPublisher;
import io.casehub.ledger.core.event.TrustScoreFullPayload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrustScorePublisherCore {

    private static final Logger log = Logger.getLogger(TrustScorePublisherCore.class.getName());

    private final TrustScoreEventPublisher publisher;
    private final TrustScoreProperties properties;

    public TrustScorePublisherCore(TrustScoreEventPublisher publisher,
                                   TrustScoreProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    public boolean needsPreviousSnapshot() {
        return publisher.needsDeltaPayload();
    }

    public void publish(List<ActorTrustScoreBase> current,
                        Map<String, ActorTrustScoreBase> previousSnapshot,
                        Instant computedAt) {
        if (!properties.routingEnabled()) {
            return;
        }

        try {
            publisher.publishNotify(new TrustScoreComputedAt(computedAt, current.size()));
        } catch (Exception e) {
            log.log(Level.WARNING, "TrustScoreComputedAt publish failed", e);
        }

        try {
            publisher.publishFull(new TrustScoreFullPayload(List.copyOf(current)));
        } catch (Exception e) {
            log.log(Level.WARNING, "TrustScoreFullPayload publish failed", e);
        }

        if (publisher.needsDeltaPayload()) {
            try {
                double threshold = properties.routingDeltaThreshold();
                List<TrustScoreDelta> deltas = computeDeltas(current, previousSnapshot, threshold);
                publisher.publishDelta(new TrustScoreDeltaPayload(deltas));
            } catch (Exception e) {
                log.log(Level.WARNING, "TrustScoreDeltaPayload publish failed", e);
            }
        }
    }

    public static List<TrustScoreDelta> computeDeltas(
            List<ActorTrustScoreBase> current,
            Map<String, ActorTrustScoreBase> previousSnapshot,
            double threshold) {
        List<TrustScoreDelta> deltas = new ArrayList<>();
        for (ActorTrustScoreBase score : current) {
            if (score.scoreType != ScoreType.GLOBAL) {
                continue;
            }
            ActorTrustScoreBase prev = previousSnapshot.get(score.actorId);
            double prevTrust = prev != null ? prev.trustScore : 0.0;
            double prevGlobal = prev != null ? prev.globalTrustScore : 0.0;
            if (Math.abs(score.trustScore - prevTrust) >= threshold) {
                deltas.add(new TrustScoreDelta(
                        score.actorId, prevTrust, score.trustScore,
                        prevGlobal, score.globalTrustScore));
            }
        }
        return deltas;
    }
}
