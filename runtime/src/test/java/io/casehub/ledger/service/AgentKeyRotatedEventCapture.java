package io.casehub.ledger.service;

import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test CDI bean that captures {@link AgentKeyRotatedEvent} fired during tests.
 *
 * <p>
 * Provides both synchronous ({@link Observes}) and asynchronous ({@link ObservesAsync})
 * observers so it captures events regardless of whether the producer calls
 * {@code fire()} or {@code fireAsync()}.
 */
@ApplicationScoped
public class AgentKeyRotatedEventCapture {

    private final List<AgentKeyRotatedEvent> events = new CopyOnWriteArrayList<>();

    void onRotated(@Observes final AgentKeyRotatedEvent event) {
        events.add(event);
    }

    void onRotatedAsync(@ObservesAsync final AgentKeyRotatedEvent event) {
        events.add(event);
    }

    public List<AgentKeyRotatedEvent> events() { return events; }

    public void reset() { events.clear(); }
}
