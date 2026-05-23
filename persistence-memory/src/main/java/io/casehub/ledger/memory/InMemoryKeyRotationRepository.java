package io.casehub.ledger.memory;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;

/**
 * Stub in-memory implementation of {@link KeyRotationRepository}.
 * Real implementation comes in Task 7.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryKeyRotationRepository implements KeyRotationRepository {

    @Inject
    InMemoryLedgerEntryRepository ledgerRepo;

    @Override
    public List<KeyRotationEntry> findByActorId(final String actorId) {
        return ledgerRepo.entries.values().stream()
                .filter(KeyRotationEntry.class::isInstance)
                .map(KeyRotationEntry.class::cast)
                .filter(e -> actorId.equals(e.actorId))
                .sorted(java.util.Comparator.comparing(e -> e.occurredAt))
                .toList();
    }

    @Override
    public List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(
            final String actorId, final String keyRef) {
        return ledgerRepo.entries.values().stream()
                .filter(KeyRotationEntry.class::isInstance)
                .map(KeyRotationEntry.class::cast)
                .filter(e -> actorId.equals(e.actorId)
                        && keyRef.equals(e.previousKeyRef)
                        && KeyRotationReason.COMPROMISED.equals(e.reason))
                .sorted(java.util.Comparator.comparing(e -> e.effectiveSince))
                .toList();
    }
}
