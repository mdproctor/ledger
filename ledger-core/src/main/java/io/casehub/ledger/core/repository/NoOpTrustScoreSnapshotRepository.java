package io.casehub.ledger.core.repository;

import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;

import java.time.Instant;
import java.util.List;

/**
 * No-op {@link TrustScoreSnapshotRepository} — discards saves, returns empty results.
 *
 * <p>Framework modules wrap this with their DI annotations:
 * Quarkus {@code @DefaultBean}, Spring {@code @ConditionalOnMissingBean}.
 */
public class NoOpTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    @Override
    public void save(final TrustScoreSnapshotBase snapshot) {
    }

    @Override
    public List<TrustScoreSnapshotBase> findGlobalSnapshots(final String actorId) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshotBase> findCapabilitySnapshots(final String actorId,
                                                                final String capabilityTag) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshotBase> findDimensionSnapshots(final String actorId,
                                                               final String dimensionKey) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshotBase> findByActorAndTimeRange(final String actorId,
                                                                final Instant from, final Instant to) {
        return List.of();
    }

    @Override
    public int deleteOlderThan(final Instant cutoff) {
        return 0;
    }
}
