package io.casehub.ledger.api.spi;

import java.time.Instant;
import java.util.List;

import io.casehub.ledger.api.model.TrustScoreSnapshotBase;

public interface TrustScoreSnapshotRepository {

    void save(TrustScoreSnapshotBase snapshot);

    List<TrustScoreSnapshotBase> findGlobalSnapshots(String actorId);

    List<TrustScoreSnapshotBase> findCapabilitySnapshots(String actorId, String capabilityTag);

    List<TrustScoreSnapshotBase> findDimensionSnapshots(String actorId, String dimensionKey);

    List<TrustScoreSnapshotBase> findByActorAndTimeRange(String actorId, Instant from, Instant to);

    int deleteOlderThan(Instant cutoff);
}
