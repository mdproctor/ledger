package io.casehub.ledger.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;

/**
 * JPA implementation of {@link ErasureReceiptRepository}.
 *
 * <p>Activate via {@code quarkus.arc.selected-alternatives}.
 * When neither this nor the in-memory alternative is active,
 * {@link io.casehub.ledger.runtime.repository.NoOpErasureReceiptRepository} handles reads.
 */
@Alternative
@ApplicationScoped
public class JpaErasureReceiptRepository implements ErasureReceiptRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public List<ErasureReceiptLedgerEntry> findByErasedActorId(
            final String erasedActorId, final String tenancyId) {
        return em.createNamedQuery(
                "ErasureReceiptLedgerEntry.findByErasedActorId", ErasureReceiptLedgerEntry.class)
                .setParameter("erasedActorId", erasedActorId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }
}
