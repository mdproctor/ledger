package io.casehub.ledger.memory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryKeyRotationRepository implements KeyRotationRepository {

    @Inject
    InMemoryLedgerEntryRepository blocking;

    @Override
    public List<KeyRotationEntry> findByActorId(final String actorId) {
        return blocking.allEntries().stream()
                .filter(e -> e instanceof KeyRotationEntry)
                .map(e -> (KeyRotationEntry) e)
                .filter(e -> actorId.equals(e.actorId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(
            final String actorId, final String keyRef) {
        return blocking.allEntries().stream()
                .filter(e -> e instanceof KeyRotationEntry)
                .map(e -> (KeyRotationEntry) e)
                .filter(e -> actorId.equals(e.actorId))
                .filter(e -> keyRef.equals(e.previousKeyRef))
                .filter(e -> KeyRotationReason.COMPROMISED.equals(e.reason))
                .sorted(Comparator.comparing(e -> e.effectiveSince))
                .collect(Collectors.toList());
    }
}
