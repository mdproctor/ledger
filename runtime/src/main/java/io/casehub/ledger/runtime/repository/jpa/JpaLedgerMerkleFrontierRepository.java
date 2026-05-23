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

    @Override
    public List<LedgerMerkleFrontier> findBySubjectId(final UUID subjectId) {
        return em.createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", subjectId)
                .getResultList();
    }

    @Override
    @Transactional
    public void replace(final UUID subjectId, final List<LedgerMerkleFrontier> newFrontier) {
        final Set<Integer> newLevels = newFrontier.stream()
                .map(n -> n.level)
                .collect(Collectors.toSet());

        if (!newLevels.isEmpty()) {
            em.createQuery(
                    "DELETE FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.level NOT IN :levels")
                    .setParameter("subjectId", subjectId)
                    .setParameter("levels", newLevels)
                    .executeUpdate();
        }

        for (final LedgerMerkleFrontier node : newFrontier) {
            em.createNamedQuery("LedgerMerkleFrontier.deleteBySubjectAndLevel")
                    .setParameter("subjectId", subjectId)
                    .setParameter("level", node.level)
                    .executeUpdate();
            em.persist(node);
        }
    }
}
