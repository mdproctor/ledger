package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.runtime.service.model.SubjectSequenceStats;

/**
 * In-memory implementation of {@link CrossTenantLedgerEntryRepository}.
 *
 * <p>Delegates to the blocking {@link InMemoryLedgerEntryRepository}'s internal
 * {@link InMemoryLedgerEntryRepository#allEntries()} and
 * {@link InMemoryLedgerEntryRepository#allAttestations()} accessors without
 * tenant filtering — cross-tenant sees everything.
 */
@CrossTenant
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryCrossTenantLedgerEntryRepository implements CrossTenantLedgerEntryRepository {

    @Inject
    InMemoryLedgerEntryRepository blocking;

    @Override
    public List<LedgerEntry> listAll() {
        return List.copyOf(blocking.allEntries());
    }

    @Override
    public List<LedgerEntry> findAllEvents() {
        return blocking.allEntries().stream()
                .filter(e -> e.entryType == LedgerEntryType.EVENT)
                .toList();
    }

    @Override
    public List<LedgerEntry> findEventsByActorId(final String actorId) {
        return blocking.allEntries().stream()
                .filter(e -> e.entryType == LedgerEntryType.EVENT)
                .filter(e -> actorId.equals(e.actorId))
                .toList();
    }

    @Override
    public List<LedgerEntry> findByTimeRange(final Instant from, final Instant to) {
        return blocking.allEntries().stream()
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .toList();
    }

    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return blocking.allAttestations().stream()
                .filter(a -> entryIds.contains(a.ledgerEntryId))
                .collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsByActorId(final String actorId) {
        final Set<UUID> eventIds = blocking.allEntries().stream()
                .filter(e -> e.entryType == LedgerEntryType.EVENT)
                .filter(e -> actorId.equals(e.actorId))
                .map(e -> e.id)
                .collect(Collectors.toSet());
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return blocking.allAttestations().stream()
                .filter(a -> eventIds.contains(a.ledgerEntryId))
                .collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    @Override
    public List<SubjectSequenceStats> findSequenceStats() {
        record Key(UUID subjectId, String tenancyId) {}
        return blocking.allEntries().stream()
                .collect(Collectors.groupingBy(e -> new Key(e.subjectId, e.tenancyId)))
                .entrySet().stream()
                .map(entry -> {
                    final Key k = entry.getKey();
                    final List<LedgerEntry> entries = entry.getValue();
                    final long count = entries.size();
                    final int min = entries.stream().mapToInt(e -> e.sequenceNumber).min().getAsInt();
                    final int max = entries.stream().mapToInt(e -> e.sequenceNumber).max().getAsInt();
                    return new SubjectSequenceStats(k.subjectId(), k.tenancyId(), count, min, max);
                })
                .toList();
    }
}
