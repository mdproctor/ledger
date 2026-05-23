package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.privacy.ActorIdentityProvider;
import io.casehub.ledger.runtime.privacy.DecisionContextSanitiser;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.runtime.service.LedgerEnricherPipeline;
import io.casehub.ledger.runtime.service.LedgerMerklePublisher;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryLedgerEntryRepository implements LedgerEntryRepository {

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    @Inject
    LedgerEnricherPipeline enricherPipeline;

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    @Inject
    DecisionContextSanitiser decisionContextSanitiser;

    @Inject
    LedgerMerklePublisher merklePublisher;

    @Inject
    LedgerConfig ledgerConfig;

    // package-private so InMemoryKeyRotationRepository can read it
    final ConcurrentHashMap<UUID, LedgerEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, LedgerAttestation> attestations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    @Override
    public LedgerEntry save(final LedgerEntry entry) {
        if (entry.id == null) {
            entry.id = UUID.randomUUID();
        }
        if (entry.occurredAt == null) {
            entry.occurredAt = Instant.now();
        }
        if (entry.actorId != null) {
            entry.actorId = actorIdentityProvider.tokenise(entry.actorId);
        }
        entry.compliance().ifPresent(cs -> {
            if (cs.decisionContext != null) {
                cs.decisionContext = decisionContextSanitiser.sanitise(cs.decisionContext);
                entry.refreshSupplementJson();
            }
        });

        entry.sequenceNumber = sequenceCounters
                .computeIfAbsent(entry.subjectId, k -> new AtomicInteger(0))
                .incrementAndGet();

        enricherPipeline.enrich(entry);

        if (ledgerConfig.hashChain().enabled()) {
            entry.digest = LedgerMerkleTree.leafHash(entry);
        }

        entries.put(entry.id, entry);

        if (ledgerConfig.hashChain().enabled()) {
            final List<LedgerMerkleFrontier> current = frontierRepo.findBySubjectId(entry.subjectId);
            final List<LedgerMerkleFrontier> newFrontier =
                    LedgerMerkleTree.append(entry.digest, current, entry.subjectId);
            frontierRepo.replace(entry.subjectId, newFrontier);
            final String newRoot = LedgerMerkleTree.treeRoot(newFrontier);
            merklePublisher.publish(entry.subjectId, entry.sequenceNumber, newRoot);
        }

        return entry;
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId) {
        return entries.values().stream()
                .filter(e -> subjectId.equals(e.subjectId))
                .sorted(Comparator.comparingInt(e -> e.sequenceNumber))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to) {
        return entries.values().stream()
                .filter(e -> subjectId.equals(e.subjectId))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId) {
        return entries.values().stream()
                .filter(e -> subjectId.equals(e.subjectId))
                .max(Comparator.comparingInt(e -> e.sequenceNumber));
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId) {
        return attestations.values().stream()
                .filter(a -> ledgerEntryId.equals(a.ledgerEntryId))
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation) {
        if (attestation.id == null) {
            attestation.id = UUID.randomUUID();
        }
        if (attestation.occurredAt == null) {
            attestation.occurredAt = Instant.now();
        }
        if (attestation.attestorId != null) {
            attestation.attestorId = actorIdentityProvider.tokenise(attestation.attestorId);
        }
        attestations.put(attestation.id, attestation);
        return attestation;
    }

    @Override
    public List<LedgerEntry> listAll() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public List<LedgerEntry> findAllEvents() {
        return entries.values().stream()
                .filter(e -> LedgerEntryType.EVENT.equals(e.entryType))
                .collect(Collectors.toList());
    }

    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return attestations.values().stream()
                .filter(a -> entryIds.contains(a.ledgerEntryId))
                .collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    @Override
    public List<LedgerEntry> findByActorId(final String actorId,
            final Instant from, final Instant to) {
        final String token = actorIdentityProvider.tokeniseForQuery(actorId);
        return entries.values().stream()
                .filter(e -> token.equals(e.actorId))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole,
            final Instant from, final Instant to) {
        return entries.values().stream()
                .filter(e -> actorRole.equals(e.actorRole))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByTimeRange(final Instant from, final Instant to) {
        return entries.values().stream()
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId) {
        return entries.values().stream()
                .filter(e -> entryId.equals(e.causedByEntryId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag) {
        return attestations.values().stream()
                .filter(a -> entryId.equals(a.ledgerEntryId))
                .filter(a -> capabilityTag.equals(a.capabilityTag))
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId) {
        return findAttestationsByEntryIdAndCapabilityTag(entryId, CapabilityTag.GLOBAL);
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag) {
        final String token = actorIdentityProvider.tokeniseForQuery(attestorId);
        return attestations.values().stream()
                .filter(a -> token.equals(a.attestorId))
                .filter(a -> capabilityTag.equals(a.capabilityTag))
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    public void clear() {
        entries.clear();
        attestations.clear();
        sequenceCounters.clear();
    }
}
