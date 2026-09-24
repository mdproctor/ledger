package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.SubjectSequenceStats;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpringJpaCrossTenantLedgerEntryRepository implements CrossTenantLedgerEntryRepository {

    private final EntityManager em;
    private final ActorIdentityProvider actorIdentityProvider;

    public SpringJpaCrossTenantLedgerEntryRepository(EntityManager em, ActorIdentityProvider actorIdentityProvider) {
        this.em = em;
        this.actorIdentityProvider = actorIdentityProvider;
    }

    @Override
    public List<LedgerEntry> listAll() {
        return em.createNamedQuery("LedgerEntry.listAll", LedgerEntry.class)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findAllEvents() {
        return em.createNamedQuery("LedgerEntry.findAllEvents", LedgerEntry.class)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findEventsByActorId(String actorId) {
        Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        return em.createNamedQuery("LedgerEntry.findEventsByActorId", LedgerEntry.class)
                .setParameter("actorId", tokenOpt.get())
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findByTimeRange(Instant from, Instant to) {
        return em.createNamedQuery("LedgerEntry.findByTimeRange", LedgerEntry.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(Set<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LedgerAttestation> all = em
                .createNamedQuery("LedgerAttestation.findByEntryIds", LedgerAttestation.class)
                .setParameter("entryIds", entryIds)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsByActorId(String actorId) {
        Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LedgerAttestation> all = em
                .createNamedQuery("LedgerAttestation.findByActorIdEvents", LedgerAttestation.class)
                .setParameter("actorId", tokenOpt.get())
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    @Override
    public List<SubjectSequenceStats> findSequenceStats() {
        return em.createNamedQuery("LedgerEntry.findSequenceStats", SubjectSequenceStats.class)
                .getResultList();
    }

    @Override
    public long countByActorId(String actorId, String tenancyId) {
        Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return 0L;
        }
        return em.createQuery("SELECT COUNT(e) FROM JpaLedgerEntry e WHERE e.actorId = :actorId AND e.tenancyId = :tenancyId", Long.class)
                .setParameter("actorId", tokenOpt.get())
                .setParameter("tenancyId", tenancyId)
                .getSingleResult();
    }
}
