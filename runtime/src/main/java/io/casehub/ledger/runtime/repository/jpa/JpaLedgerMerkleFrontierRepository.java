package io.casehub.ledger.runtime.repository.jpa;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;

@ApplicationScoped
@Alternative
public class JpaLedgerMerkleFrontierRepository implements LedgerMerkleFrontierRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @SuppressWarnings("unchecked")
    @Override
    public List<io.casehub.ledger.api.model.LedgerMerkleFrontier> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return (List<io.casehub.ledger.api.model.LedgerMerkleFrontier>) (List<?>) em.createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    @Transactional
    public void replace(final UUID subjectId, final List<? extends io.casehub.ledger.api.model.LedgerMerkleFrontier> newFrontier, final String tenancyId) {
        final Set<Integer> newLevels = newFrontier.stream()
                .map(n -> n.level)
                .collect(Collectors.toSet());

        if (newLevels.isEmpty()) {
            // Empty replacement — delete all existing nodes for this subject+tenant.
            em.createQuery(
                    "DELETE FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.tenancyId = :tenancyId")
                    .setParameter("subjectId", subjectId)
                    .setParameter("tenancyId", tenancyId)
                    .executeUpdate();
            return;
        }

        em.createQuery(
                "DELETE FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.tenancyId = :tenancyId AND f.level NOT IN :levels")
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setParameter("levels", newLevels)
                .executeUpdate();

        for (final io.casehub.ledger.api.model.LedgerMerkleFrontier src : newFrontier) {
            em.createNamedQuery("LedgerMerkleFrontier.deleteBySubjectAndLevel")
                    .setParameter("subjectId", subjectId)
                    .setParameter("level", src.level)
                    .setParameter("tenancyId", tenancyId)
                    .executeUpdate();
            final LedgerMerkleFrontier node = new LedgerMerkleFrontier();
            node.subjectId = src.subjectId;
            node.level = src.level;
            node.hash = src.hash;
            node.tenancyId = tenancyId;
            em.persist(node);
        }
    }
}
