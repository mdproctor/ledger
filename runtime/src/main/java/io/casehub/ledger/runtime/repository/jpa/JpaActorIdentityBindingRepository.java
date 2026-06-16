package io.casehub.ledger.runtime.repository.jpa;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ActorIdentityBindingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of {@link ActorIdentityBindingRepository}.
 *
 * <p>Read-only — saves go through {@link io.casehub.ledger.runtime.repository.LedgerEntryRepository#save}.
 *
 * <p>Activate via {@code quarkus.arc.selected-alternatives=...JpaActorIdentityBindingRepository}.
 */
@Alternative
@ApplicationScoped
public class JpaActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId,
            final String tenancyId) {
        return em.createNamedQuery("ActorIdentityBindingEntry.findLatestByActorId",
                    ActorIdentityBindingEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId,
            final String tenancyId) {
        return em.createNamedQuery("ActorIdentityBindingEntry.findHistoryByActorId",
                    ActorIdentityBindingEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }
}
