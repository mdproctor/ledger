package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.jpa.LedgerMerkleFrontier;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpringJpaLedgerMerkleFrontierRepository implements LedgerMerkleFrontierRepository {

    private final EntityManager em;

    public SpringJpaLedgerMerkleFrontierRepository(EntityManager em) {
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<io.casehub.ledger.api.model.LedgerMerkleFrontier> findBySubjectId(UUID subjectId, String tenancyId) {
        return (List<io.casehub.ledger.api.model.LedgerMerkleFrontier>) (List<?>) em.createNamedQuery("LedgerMerkleFrontier.findBySubjectId", LedgerMerkleFrontier.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    @Transactional
    public void replace(UUID subjectId, List<? extends io.casehub.ledger.api.model.LedgerMerkleFrontier> newFrontier, String tenancyId) {
        Set<Integer> newLevels = newFrontier.stream()
                .map(n -> n.level)
                .collect(Collectors.toSet());

        if (newLevels.isEmpty()) {
            em.createQuery("DELETE FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.tenancyId = :tenancyId")
                    .setParameter("subjectId", subjectId)
                    .setParameter("tenancyId", tenancyId)
                    .executeUpdate();
            return;
        }

        em.createQuery("DELETE FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.tenancyId = :tenancyId AND f.level NOT IN :levels")
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setParameter("levels", newLevels)
                .executeUpdate();

        for (io.casehub.ledger.api.model.LedgerMerkleFrontier src : newFrontier) {
            em.createNamedQuery("LedgerMerkleFrontier.deleteBySubjectAndLevel")
                    .setParameter("subjectId", subjectId)
                    .setParameter("level", src.level)
                    .setParameter("tenancyId", tenancyId)
                    .executeUpdate();
            LedgerMerkleFrontier node = new LedgerMerkleFrontier();
            node.subjectId = src.subjectId;
            node.level = src.level;
            node.hash = src.hash;
            node.tenancyId = tenancyId;
            em.persist(node);
        }
    }
}
