package io.casehub.ledger.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;

import io.casehub.ledger.core.model.AttestationRecordedEvent;

@ApplicationScoped
public class AttestationRecordedEventCapture {

    private final List<AttestationRecordedEvent> syncEvents = new CopyOnWriteArrayList<>();
    private final List<AttestationRecordedEvent> asyncEvents = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch asyncLatch = new CountDownLatch(1);

    void onSync(@Observes final AttestationRecordedEvent event) {
        syncEvents.add(event);
    }

    CompletionStage<Void> onAsync(@ObservesAsync final AttestationRecordedEvent event) {
        asyncEvents.add(event);
        asyncLatch.countDown();
        return CompletableFuture.completedFuture(null);
    }

    public List<AttestationRecordedEvent> syncEvents() { return syncEvents; }

    public List<AttestationRecordedEvent> asyncEvents() { return asyncEvents; }

    public CountDownLatch asyncLatch() { return asyncLatch; }

    public void reset() {
        syncEvents.clear();
        asyncEvents.clear();
        asyncLatch = new CountDownLatch(1);
    }
}
