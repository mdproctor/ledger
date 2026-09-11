package io.casehub.ledger.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;

@ApplicationScoped
public class AgentKeyRotatedEventCapture {

    private final List<AgentKeyRotatedEvent> syncEvents = new CopyOnWriteArrayList<>();
    private final List<AgentKeyRotatedEvent> asyncEvents = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch asyncLatch = new CountDownLatch(1);

    void onRotated(@Observes final AgentKeyRotatedEvent event) {
        syncEvents.add(event);
    }

    CompletionStage<Void> onRotatedAsync(@ObservesAsync final AgentKeyRotatedEvent event) {
        asyncEvents.add(event);
        asyncLatch.countDown();
        return CompletableFuture.completedFuture(null);
    }

    public List<AgentKeyRotatedEvent> syncEvents() { return syncEvents; }

    public List<AgentKeyRotatedEvent> asyncEvents() { return asyncEvents; }

    public CountDownLatch asyncLatch() { return asyncLatch; }

    public void reset() {
        syncEvents.clear();
        asyncEvents.clear();
        asyncLatch = new CountDownLatch(1);
    }
}
