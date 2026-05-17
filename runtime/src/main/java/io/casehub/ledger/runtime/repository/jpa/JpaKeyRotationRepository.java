package io.casehub.ledger.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class JpaKeyRotationRepository implements KeyRotationRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public List<KeyRotationEntry> findByActorId(final String actorId) {
        return em.createNamedQuery("KeyRotationEntry.findByActorId", KeyRotationEntry.class)
                .setParameter("actorId", actorId)
                .getResultList();
    }

    @Override
    public List<KeyRotationEntry> findCompromisedByActorIdAndKeyRef(
            final String actorId, final String keyRef) {
        return em.createNamedQuery(
                "KeyRotationEntry.findCompromisedByActorIdAndKeyRef", KeyRotationEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("keyRef", keyRef)
                .getResultList();
    }
}
