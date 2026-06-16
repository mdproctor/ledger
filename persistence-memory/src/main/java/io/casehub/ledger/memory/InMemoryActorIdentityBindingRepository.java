package io.casehub.ledger.memory;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link ActorIdentityBindingRepository}.
 *
 * <p>Read-only — delegates to {@link InMemoryLedgerEntryRepository#allEntries()} filtered by
 * type, actorId, and tenancyId. Saves go through {@link InMemoryLedgerEntryRepository#save},
 * which assigns {@code sequenceNumber}, computes {@code digest}, and updates the Merkle frontier.
 *
 * <p>Mirrors the {@code InMemoryKeyRotationRepository} pattern.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    @Inject
    InMemoryLedgerEntryRepository blocking;

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId,
            final String tenancyId) {
        return blocking.allEntries().stream()
            .filter(e -> e instanceof ActorIdentityBindingEntry)
            .map(e -> (ActorIdentityBindingEntry) e)
            .filter(e -> actorId.equals(e.actorId) && tenancyId.equals(e.tenancyId))
            .max(Comparator.comparingInt(e -> e.sequenceNumber));
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId,
            final String tenancyId) {
        return blocking.allEntries().stream()
            .filter(e -> e instanceof ActorIdentityBindingEntry)
            .map(e -> (ActorIdentityBindingEntry) e)
            .filter(e -> actorId.equals(e.actorId) && tenancyId.equals(e.tenancyId))
            .sorted(Comparator.comparingInt(e -> e.sequenceNumber))
            .toList();
    }
}
