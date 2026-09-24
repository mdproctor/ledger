package io.casehub.ledger.runtime.repository.jpa;

import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Alternative
public class JpaTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public void save(final TrustScoreSnapshotBase snapshot) {
        if (snapshot instanceof io.casehub.ledger.jpa.TrustScoreSnapshot entity) {
            em.persist(entity);
        } else {
            em.persist(new io.casehub.ledger.jpa.TrustScoreSnapshot(
                    snapshot.actorId, snapshot.scoreType,
                    snapshot.capabilityTag, snapshot.dimensionKey,
                    snapshot.score, snapshot.previousScore, snapshot.occurredAt));
        }
    }

    @Override
    public List<TrustScoreSnapshotBase> findGlobalSnapshots(final String actorId) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorGlobal", TrustScoreSnapshotBase.class)
                 .setParameter("actorId", actorId)
                 .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findCapabilitySnapshots(final String actorId,
                                                                final String capabilityTag) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndCapability", TrustScoreSnapshotBase.class)
                 .setParameter("actorId", actorId)
                 .setParameter("capabilityTag", capabilityTag)
                 .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findDimensionSnapshots(final String actorId,
                                                               final String dimensionKey) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndDimension", TrustScoreSnapshotBase.class)
                 .setParameter("actorId", actorId)
                 .setParameter("dimensionKey", dimensionKey)
                 .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findByActorAndTimeRange(final String actorId,
                                                                final Instant from, final Instant to) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndTimeRange", TrustScoreSnapshotBase.class)
                 .setParameter("actorId", actorId)
                 .setParameter("from", from)
                 .setParameter("to", to)
                 .getResultList();
    }

    @Override
    public int deleteOlderThan(final Instant cutoff) {
        return em.createNamedQuery("TrustScoreSnapshot.deleteOlderThan")
                 .setParameter("cutoff", cutoff)
                 .executeUpdate();
    }
}
