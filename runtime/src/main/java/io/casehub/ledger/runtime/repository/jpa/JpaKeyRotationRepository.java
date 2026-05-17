package io.casehub.ledger.runtime.repository.jpa;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;

@ApplicationScoped
public class JpaKeyRotationRepository implements KeyRotationRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    @Transactional
    public KeyRotationEntry save(final KeyRotationEntry entry) {
        if (entry.id == null) {
            entry.id = UUID.randomUUID();
        }
        if (entry.subjectId == null && entry.actorId != null) {
            entry.subjectId = UUID.nameUUIDFromBytes(
                    entry.actorId.getBytes(StandardCharsets.UTF_8));
        }
        em.persist(entry);
        return entry;
    }

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
