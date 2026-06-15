package io.casehub.ledger.runtime.repository.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;

import java.util.List;
import java.util.Optional;

@Alternative
@ApplicationScoped
public class JpaActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    LedgerSequenceAllocator sequenceAllocator;

    @Inject
    LedgerConfig ledgerConfig;

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId) {
        return em.createNamedQuery("ActorIdentityBindingEntry.findLatestByActorId",
                    ActorIdentityBindingEntry.class)
                .setParameter("actorId", actorId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId) {
        return em.createNamedQuery("ActorIdentityBindingEntry.findHistoryByActorId",
                    ActorIdentityBindingEntry.class)
                .setParameter("actorId", actorId)
                .getResultList();
    }

    @Override
    @Transactional
    public ActorIdentityBindingEntry save(final ActorIdentityBindingEntry entry) {
        if (entry.subjectId == null) {
            throw new IllegalArgumentException("ActorIdentityBindingEntry.subjectId must not be null");
        }
        entry.sequenceNumber = sequenceAllocator.nextSequenceNumber(entry.subjectId);
        if (ledgerConfig.hashChain().enabled()) {
            entry.digest = LedgerMerkleTree.leafHash(entry);
        }
        em.persist(entry);
        return entry;
    }
}
