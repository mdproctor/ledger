package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.jpa.TrustScoreSnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public class SpringJpaTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    private final EntityManager em;

    public SpringJpaTrustScoreSnapshotRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(TrustScoreSnapshotBase snapshot) {
        if (snapshot instanceof TrustScoreSnapshot entity) {
            em.persist(entity);
        } else {
            em.persist(new TrustScoreSnapshot(
                    snapshot.actorId, snapshot.scoreType,
                    snapshot.capabilityTag, snapshot.dimensionKey,
                    snapshot.score, snapshot.previousScore, snapshot.occurredAt));
        }
    }

    @Override
    public List<TrustScoreSnapshotBase> findGlobalSnapshots(String actorId) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorGlobal", TrustScoreSnapshotBase.class)
                .setParameter("actorId", actorId)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findCapabilitySnapshots(String actorId, String capabilityTag) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndCapability", TrustScoreSnapshotBase.class)
                .setParameter("actorId", actorId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findDimensionSnapshots(String actorId, String dimensionKey) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndDimension", TrustScoreSnapshotBase.class)
                .setParameter("actorId", actorId)
                .setParameter("dimensionKey", dimensionKey)
                .getResultList();
    }

    @Override
    public List<TrustScoreSnapshotBase> findByActorAndTimeRange(String actorId, Instant from, Instant to) {
        return em.createNamedQuery("TrustScoreSnapshot.findByActorAndTimeRange", TrustScoreSnapshotBase.class)
                .setParameter("actorId", actorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return em.createNamedQuery("TrustScoreSnapshot.deleteOlderThan")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
