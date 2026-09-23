package io.casehub.ledger.runtime.service.routing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ObserverMethod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.api.model.ActorTrustScoreBase;

@SuppressWarnings("unchecked")
class TrustScoreRoutingPublisherPublishTest {

    private final Event<TrustScoreFullPayload> fullEvent = mock(Event.class);
    private final Event<TrustScoreDeltaPayload> deltaEvent = mock(Event.class);
    private final Event<TrustScoreComputedAt> notifyEvent = mock(Event.class);

    private TrustScoreRoutingPublisher publisher;

    @BeforeEach
    void setUp() {
        final BeanManager bm = mock(BeanManager.class);
        when(bm.resolveObserverMethods(any())).thenAnswer(inv -> Set.of(mock(ObserverMethod.class)));

        final LedgerConfig config = mock(LedgerConfig.class, Mockito.RETURNS_DEEP_STUBS);
        when(config.trustScore().routingEnabled()).thenReturn(true);
        when(config.trustScore().routingDeltaThreshold()).thenReturn(0.01);

        when(fullEvent.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(deltaEvent.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(notifyEvent.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher = new TrustScoreRoutingPublisher();
        publisher.fullEvent = fullEvent;
        publisher.deltaEvent = deltaEvent;
        publisher.notifyEvent = notifyEvent;
        publisher.config = config;
        publisher.beanManager = bm;
        publisher.detectObservers();
    }

    @Test
    void publish_firesAsyncForFullPayload() {
        publisher.publish(List.of(score("a", 0.8, 0.5)), Map.of(), Instant.now());
        verify(fullEvent).fire(any());
        verify(fullEvent).fireAsync(any());
    }

    @Test
    void publish_firesAsyncForDeltaPayload() {
        publisher.publish(List.of(score("a", 0.8, 0.5)), Map.of(), Instant.now());
        verify(deltaEvent).fire(any());
        verify(deltaEvent).fireAsync(any());
    }

    @Test
    void publish_firesAsyncForNotifyPayload() {
        publisher.publish(List.of(score("a", 0.8, 0.5)), Map.of(), Instant.now());
        verify(notifyEvent).fire(any());
        verify(notifyEvent).fireAsync(any());
    }

    @Test
    void publish_routingDisabled_firesNothing() {
        final LedgerConfig disabledConfig = mock(LedgerConfig.class, Mockito.RETURNS_DEEP_STUBS);
        when(disabledConfig.trustScore().routingEnabled()).thenReturn(false);
        publisher.config = disabledConfig;

        publisher.publish(List.of(score("a", 0.8, 0.5)), Map.of(), Instant.now());
        verify(fullEvent, never()).fire(any());
        verify(fullEvent, never()).fireAsync(any());
    }

    private static ActorTrustScoreBase score(final String actorId, final double trust, final double global) {
        final ActorTrustScoreBase s = new ActorTrustScoreBase();
        s.actorId = actorId;
        s.trustScore = trust;
        s.globalTrustScore = global;
        return s;
    }
}
