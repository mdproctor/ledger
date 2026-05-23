package io.casehub.ledger.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryLedgerMerkleFrontierRepository implements LedgerMerkleFrontierRepository {

    private final ConcurrentHashMap<UUID, List<LedgerMerkleFrontier>> frontierBySubject =
            new ConcurrentHashMap<>();

    @Override
    public List<LedgerMerkleFrontier> findBySubjectId(final UUID subjectId) {
        return new ArrayList<>(frontierBySubject.getOrDefault(subjectId, Collections.emptyList()));
    }

    @Override
    public void replace(final UUID subjectId, final List<LedgerMerkleFrontier> newFrontier) {
        frontierBySubject.put(subjectId, List.copyOf(newFrontier));
    }

    public void clear() {
        frontierBySubject.clear();
    }
}
