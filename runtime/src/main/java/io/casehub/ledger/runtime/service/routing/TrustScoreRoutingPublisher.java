package io.casehub.ledger.runtime.service.routing;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.core.event.TrustScoreComputedAt;
import io.casehub.ledger.core.event.TrustScoreDelta;
import io.casehub.ledger.core.event.TrustScoreDeltaPayload;
import io.casehub.ledger.core.event.TrustScoreFullPayload;
import io.casehub.ledger.runtime.config.LedgerConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TrustScoreRoutingPublisher implements io.casehub.ledger.core.event.TrustScoreEventPublisher {

    private static final Logger log = Logger.getLogger(TrustScoreRoutingPublisher.class);

    @Inject
    Event<TrustScoreFullPayload> fullEvent;

    @Inject
    Event<TrustScoreDeltaPayload> deltaEvent;

    @Inject
    Event<TrustScoreComputedAt> notifyEvent;

    @Inject
    LedgerConfig config;

    @Inject
    BeanManager beanManager;

    private boolean hasFullObservers;
    private boolean hasDeltaObservers;
    private boolean hasNotifyObservers;

    @PostConstruct
    void detectObservers() {
        hasFullObservers   = !beanManager
                                      .resolveObserverMethods(new TrustScoreFullPayload(List.of())).isEmpty();
        hasDeltaObservers  = !beanManager
                                      .resolveObserverMethods(new TrustScoreDeltaPayload(List.of())).isEmpty();
        hasNotifyObservers = !beanManager
                                      .resolveObserverMethods(new TrustScoreComputedAt(Instant.EPOCH, 0)).isEmpty();
    }

    @Override
    public boolean needsDeltaPayload() {
        return hasDeltaObservers;
    }

    @Override
    public void publishFull(TrustScoreFullPayload payload) {
        if (!hasFullObservers) {return;}
        try {
            fullEvent.fire(payload);
        } catch (Exception e) {
            log.warnf(e, "TrustScoreFullPayload sync observer failed");
        }
        try {
            fullEvent.fireAsync(payload)
                     .exceptionally(ex -> {
                         log.warnf(ex, "TrustScoreFullPayload async observer failed");
                         return null;
                     });
        } catch (Exception e) {
            log.warnf(e, "TrustScoreFullPayload fireAsync failed");
        }
    }

    @Override
    public void publishDelta(TrustScoreDeltaPayload payload) {
        if (!hasDeltaObservers) {return;}
        try {
            deltaEvent.fire(payload);
        } catch (Exception e) {
            log.warnf(e, "TrustScoreDeltaPayload sync observer failed");
        }
        try {
            deltaEvent.fireAsync(payload)
                      .exceptionally(ex -> {
                          log.warnf(ex, "TrustScoreDeltaPayload async observer failed");
                          return null;
                      });
        } catch (Exception e) {
            log.warnf(e, "TrustScoreDeltaPayload fireAsync failed");
        }
    }

    @Override
    public void publishNotify(TrustScoreComputedAt payload) {
        if (!hasNotifyObservers) {return;}
        try {
            notifyEvent.fire(payload);
        } catch (Exception e) {
            log.warnf(e, "TrustScoreComputedAt sync observer failed");
        }
        try {
            notifyEvent.fireAsync(payload)
                       .exceptionally(ex -> {
                           log.warnf(ex, "TrustScoreComputedAt async observer failed");
                           return null;
                       });
        } catch (Exception e) {
            log.warnf(e, "TrustScoreComputedAt fireAsync failed");
        }
    }

    /**
     * True when at least one TrustScoreDeltaPayload observer is registered.
     */
    public boolean needsPreviousSnapshot() {
        return hasDeltaObservers;
    }

    public void publish(List<ActorTrustScoreBase> current,
                        Map<String, ActorTrustScoreBase> previousSnapshot,
                        Instant computedAt) {

        if (!config.trustScore().routingEnabled()) {
            return;
        }

        publishNotify(new TrustScoreComputedAt(computedAt, current.size()));
        publishFull(new TrustScoreFullPayload(List.copyOf(current)));

        if (hasDeltaObservers) {
            double                threshold = config.trustScore().routingDeltaThreshold();
            List<TrustScoreDelta> deltas    = computeDeltas(current, previousSnapshot, threshold);
            publishDelta(new TrustScoreDeltaPayload(deltas));
        }
    }

    public static List<TrustScoreDelta> computeDeltas(
            List<ActorTrustScoreBase> current,
            Map<String, ActorTrustScoreBase> previousSnapshot,
            double threshold) {

        List<TrustScoreDelta> deltas = new ArrayList<>();
        for (ActorTrustScoreBase score : current) {
            ActorTrustScoreBase prev       = previousSnapshot.get(score.actorId);
            double              prevTrust  = prev != null ? prev.trustScore : 0.0;
            double              prevGlobal = prev != null ? prev.globalTrustScore : 0.0;
            if (Math.abs(score.trustScore - prevTrust) >= threshold) {
                deltas.add(new TrustScoreDelta(
                        score.actorId, prevTrust, score.trustScore,
                        prevGlobal, score.globalTrustScore));
            }
        }
        return deltas;
    }
}
