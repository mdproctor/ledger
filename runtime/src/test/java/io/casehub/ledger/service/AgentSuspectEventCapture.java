package io.casehub.ledger.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;

import io.casehub.ledger.runtime.service.AgentSignatureSuspectEvent;

/**
 * Test CDI bean that captures {@link AgentSignatureSuspectEvent} fired during tests.
 * Registered automatically by the Quarkus test container.
 */
@ApplicationScoped
public class AgentSuspectEventCapture {

    private final List<AgentSignatureSuspectEvent> syncEvents = new CopyOnWriteArrayList<>();
    private volatile AgentSignatureSuspectEvent lastAsyncEvent;
    private volatile CountDownLatch asyncLatch = new CountDownLatch(1);

    void onSuspectSync(@Observes final AgentSignatureSuspectEvent event) {
        syncEvents.add(event);
    }

    CompletionStage<Void> onSuspectAsync(@ObservesAsync final AgentSignatureSuspectEvent event) {
        lastAsyncEvent = event;
        asyncLatch.countDown();
        return CompletableFuture.completedFuture(null);
    }

    public List<AgentSignatureSuspectEvent> syncEvents() {
        return syncEvents;
    }

    public AgentSignatureSuspectEvent lastAsyncEvent() {
        return lastAsyncEvent;
    }

    public CountDownLatch asyncLatch() {
        return asyncLatch;
    }

    public void reset() {
        syncEvents.clear();
        lastAsyncEvent = null;
        asyncLatch = new CountDownLatch(1);
    }
}
