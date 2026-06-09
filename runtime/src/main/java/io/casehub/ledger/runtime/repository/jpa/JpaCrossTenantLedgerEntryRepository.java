package io.casehub.ledger.runtime.repository.jpa;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.privacy.ActorIdentityProvider;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;

/**
 * JPA implementation of {@link CrossTenantLedgerEntryRepository}.
 *
 * <p>
 * Provides cross-tenant read operations for system-level beans (trust computation,
 * health checks, retention, compliance export). Queries intentionally omit tenant
 * filtering — they span all tenants.
 *
 * <p>
 * This bean is {@code @ApplicationScoped} (not {@code @Alternative}) because cross-tenant
 * operations are a system-level concern that should always be available when JPA is active.
 */
@ApplicationScoped
public class JpaCrossTenantLedgerEntryRepository implements CrossTenantLedgerEntryRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> listAll() {
        return em.createQuery("SELECT e FROM LedgerEntry e", LedgerEntry.class)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findAllEvents() {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.entryType = :type",
                LedgerEntry.class)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findEventsByActorId(final String actorId) {
        final String token = actorIdentityProvider.tokeniseForQuery(actorId);
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.entryType = :type",
                LedgerEntry.class)
                .setParameter("actorId", token)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByTimeRange(final Instant from, final Instant to) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.occurredAt >= :from AND e.occurredAt <= :to" +
                        " ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        final List<LedgerAttestation> all = em
                .createNamedQuery("LedgerAttestation.findByEntryIds", LedgerAttestation.class)
                .setParameter("entryIds", entryIds)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }
}
