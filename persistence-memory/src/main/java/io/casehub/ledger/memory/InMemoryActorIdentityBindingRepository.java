package io.casehub.ledger.memory;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    private final CopyOnWriteArrayList<ActorIdentityBindingEntry> store = new CopyOnWriteArrayList<>();

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId) {
        return store.stream()
            .filter(e -> actorId.equals(e.actorId))
            .max(Comparator.comparing(e -> e.occurredAt));
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId) {
        return store.stream()
            .filter(e -> actorId.equals(e.actorId))
            .sorted(Comparator.comparing(e -> e.occurredAt))
            .toList();
    }

    @Override
    public ActorIdentityBindingEntry save(final ActorIdentityBindingEntry entry) {
        store.add(entry);
        return entry;
    }
}
