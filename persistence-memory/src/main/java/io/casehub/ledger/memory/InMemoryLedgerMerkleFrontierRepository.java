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

    private record FrontierKey(UUID subjectId, String tenancyId) {}

    private final ConcurrentHashMap<FrontierKey, List<LedgerMerkleFrontier>> frontierByKey =
            new ConcurrentHashMap<>();

    @Override
    public List<LedgerMerkleFrontier> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return new ArrayList<>(frontierByKey.getOrDefault(new FrontierKey(subjectId, tenancyId), Collections.emptyList()));
    }

    @Override
    public void replace(final UUID subjectId, final List<LedgerMerkleFrontier> newFrontier,
            final String tenancyId) {
        frontierByKey.put(new FrontierKey(subjectId, tenancyId), List.copyOf(newFrontier));
    }

    public void clear() {
        frontierByKey.clear();
    }
}
