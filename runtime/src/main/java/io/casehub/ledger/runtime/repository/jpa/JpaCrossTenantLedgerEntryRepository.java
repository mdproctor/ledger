package io.casehub.ledger.runtime.repository.jpa;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.service.model.SubjectSequenceStats;

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
@CrossTenant
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
        return em.createNamedQuery("LedgerEntry.listAll", LedgerEntry.class)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findAllEvents() {
        return em.createNamedQuery("LedgerEntry.findAllEvents", LedgerEntry.class)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findEventsByActorId(final String actorId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        final String token = tokenOpt.get();
        return em.createNamedQuery("LedgerEntry.findEventsByActorId", LedgerEntry.class)
                .setParameter("actorId", token)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByTimeRange(final Instant from, final Instant to) {
        return em.createNamedQuery("LedgerEntry.findByTimeRange", LedgerEntry.class)
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

    /** {@inheritDoc} */
    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsByActorId(final String actorId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return Collections.emptyMap();
        }
        final String token = tokenOpt.get();
        final List<LedgerAttestation> all = em
                .createNamedQuery("LedgerAttestation.findByActorIdEvents", LedgerAttestation.class)
                .setParameter("actorId", token)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    /** {@inheritDoc} */
    @Override
    public List<SubjectSequenceStats> findSequenceStats() {
        return em.createNamedQuery("LedgerEntry.findSequenceStats", SubjectSequenceStats.class)
                .getResultList();
    }
}
