package io.casehub.ledger.memory;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    private final CopyOnWriteArrayList<TrustScoreSnapshotBase> store = new CopyOnWriteArrayList<>();

    @Override
    public void save(final TrustScoreSnapshotBase snapshot) {
        store.add(snapshot);
    }

    @Override
    public List<TrustScoreSnapshotBase> findGlobalSnapshots(final String actorId) {
        return store.stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> s.scoreType == ScoreType.GLOBAL)
                    .sorted(Comparator.comparing((TrustScoreSnapshotBase s) -> s.occurredAt).reversed())
                    .toList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findCapabilitySnapshots(final String actorId,
                                                                final String capabilityTag) {
        return store.stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> s.scoreType == ScoreType.CAPABILITY)
                    .filter(s -> capabilityTag.equals(s.capabilityTag))
                    .sorted(Comparator.comparing((TrustScoreSnapshotBase s) -> s.occurredAt).reversed())
                    .toList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findDimensionSnapshots(final String actorId,
                                                               final String dimensionKey) {
        return store.stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> s.scoreType == ScoreType.DIMENSION)
                    .filter(s -> dimensionKey.equals(s.dimensionKey))
                    .sorted(Comparator.comparing((TrustScoreSnapshotBase s) -> s.occurredAt).reversed())
                    .toList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findByActorAndTimeRange(final String actorId,
                                                                final Instant from, final Instant to) {
        return store.stream()
                    .filter(s -> actorId.equals(s.actorId))
                    .filter(s -> !s.occurredAt.isBefore(from) && !s.occurredAt.isAfter(to))
                    .sorted(Comparator.comparing((TrustScoreSnapshotBase s) -> s.occurredAt).reversed())
                    .toList();
    }

    @Override
    public int deleteOlderThan(final Instant cutoff) {
        final int before = store.size();
        store.removeIf(s -> s.occurredAt.isBefore(cutoff));
        return before - store.size();
    }

    public void clear() {
        store.clear();
    }
}
