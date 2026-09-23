package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;

import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class NoOpTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    @Override
    public void save(final TrustScoreSnapshot snapshot) {
    }

    @Override
    public List<TrustScoreSnapshot> findGlobalSnapshots(final String actorId) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshot> findCapabilitySnapshots(final String actorId,
            final String capabilityTag) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshot> findDimensionSnapshots(final String actorId,
            final String dimensionKey) {
        return List.of();
    }

    @Override
    public List<TrustScoreSnapshot> findByActorAndTimeRange(final String actorId,
            final Instant from, final Instant to) {
        return List.of();
    }

    @Override
    public int deleteOlderThan(final Instant cutoff) {
        return 0;
    }
}
