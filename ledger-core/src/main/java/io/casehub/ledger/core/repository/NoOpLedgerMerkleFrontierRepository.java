package io.casehub.ledger.core.repository;

import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;

import java.util.List;
import java.util.UUID;

/**
 * No-op {@link LedgerMerkleFrontierRepository} — returns empty frontier for all queries.
 *
 * <p>Framework modules wrap this with their DI annotations:
 * Quarkus {@code @DefaultBean}, Spring {@code @ConditionalOnMissingBean}.
 */
public class NoOpLedgerMerkleFrontierRepository implements LedgerMerkleFrontierRepository {

    @Override
    public List<LedgerMerkleFrontier> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return List.of();
    }

    @Override
    public void replace(final UUID subjectId, final List<? extends LedgerMerkleFrontier> newFrontier, final String tenancyId) {
    }
}
