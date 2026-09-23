package io.casehub.ledger.runtime.repository.jpa;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;

@ApplicationScoped
@Alternative
public class JpaTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public void save(final TrustScoreSnapshot snapshot) {
        em.persist(snapshot);
    }

    @Override
    public List<TrustScoreSnapshot> findGlobalSnapshots(final String actorId) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorGlobal", TrustScoreSnapshot.class)
                .setParameter("actorId", actorId)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshot> findCapabilitySnapshots(final String actorId,
            final String capabilityTag) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndCapability", TrustScoreSnapshot.class)
                .setParameter("actorId", actorId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshot> findDimensionSnapshots(final String actorId,
            final String dimensionKey) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndDimension", TrustScoreSnapshot.class)
                .setParameter("actorId", actorId)
                .setParameter("dimensionKey", dimensionKey)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshot> findByActorAndTimeRange(final String actorId,
            final Instant from, final Instant to) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndTimeRange", TrustScoreSnapshot.class)
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
