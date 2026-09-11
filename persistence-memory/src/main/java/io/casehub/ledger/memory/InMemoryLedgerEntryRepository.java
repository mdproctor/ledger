package io.casehub.ledger.memory;

import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.core.privacy.ContentSanitiser;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.runtime.service.LedgerEnricherPipeline;
import io.casehub.ledger.runtime.service.LedgerMerklePublisher;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-memory implementation of {@link LedgerEntryRepository} for zero-datasource deployments.
 *
 * <p>Accumulates entries, attestations, and sequence counters in-process. Suitable for
 * short-lived processes (test suites, single game sessions). For long-running processes
 * that span multiple sessions, call {@link #clear()} at each session boundary to prevent
 * unbounded growth (see casehubio/ledger#117).
 *
 * <p>All query methods are tenant-scoped — they filter results by {@code tenancyId}.
 * Cross-tenant queries are handled by {@link InMemoryCrossTenantLedgerEntryRepository},
 * which delegates to this class's internal {@link #allEntries()} and {@link #allAttestations()}
 * accessors.
 *
 * <p>Activated via {@code @Alternative @Priority(1)} — consumers add this class to
 * {@code quarkus.arc.selected-alternatives} to displace the JPA default.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryLedgerEntryRepository implements LedgerEntryRepository {

    private static final Logger log = Logger.getLogger(InMemoryLedgerEntryRepository.class);

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    @Inject
    LedgerEnricherPipeline enricherPipeline;

    @Inject
    AgentEntrySigner agentEntrySigner;

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    @Inject
    ContentSanitiser contentSanitiser;

    @Inject
    LedgerMerklePublisher merklePublisher;

    @Inject
    LedgerConfig ledgerConfig;

    @Inject
    Event<AttestationRecordedEvent> attestationRecordedEvent;

    // package-private so InMemoryKeyRotationRepository can read it
    final ConcurrentHashMap<UUID, LedgerEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, LedgerAttestation> attestations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SubjectKey, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    // Per-subject lock: serialises the sequence-allocation → hash → frontier-update pipeline
    // so that concurrent saves for the same (subject, tenant) always produce a correct Merkle chain.
    // Different subjects or different tenants run fully concurrently.
    private final ConcurrentHashMap<SubjectKey, Object> subjectLocks = new ConcurrentHashMap<>();

    private record SubjectKey(UUID subjectId, String tenancyId) {}

    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        // Pre-work — per-entry stateless, no ordering constraint, runs concurrently across subjects.
        entry.tenancyId = tenancyId;
        if (entry.id == null) {
            entry.id = UUID.randomUUID();
        }
        if (entry.occurredAt == null) {
            entry.occurredAt = Instant.now();
        }
        entry.actorId = actorIdentityProvider.tokenise(entry.actorId, entry.actorType);
        entry.compliance().ifPresent(cs -> {
            if (cs.decisionContext != null) {
                cs.decisionContext = contentSanitiser.sanitise(cs.decisionContext);
                entry.refreshSupplementJson();
            }
        });
        // prepareKey is per-entry stateless (reads actorId, writes agentPublicKey/agentKeyRef
        // on this entry only). Runs pre-lock; enricherPipeline.enrich() reads these fields.
        agentEntrySigner.prepareKey(entry);

        // Per-subject serialised section — sequence assignment, Merkle chain update, and
        // everything in between must be atomic per subject to guarantee chain correctness.
        final SubjectKey subjectKey = new SubjectKey(entry.subjectId, entry.tenancyId);
        final Object lock = subjectLocks.computeIfAbsent(subjectKey, k -> new Object());
        synchronized (lock) {
            entry.sequenceNumber = sequenceCounters
                    .computeIfAbsent(subjectKey, k -> new AtomicInteger(0))
                    .incrementAndGet();

            enricherPipeline.enrich(entry);

            if (ledgerConfig.hashChain().enabled()) {
                entry.digest = LedgerMerkleTree.leafHash(entry);
            }

            agentEntrySigner.sign(entry);

            // entries.put() inside the lock: from the perspective of concurrent saves to the
            // same subject, entry and frontier are both written before the lock releases.
            // Concurrent reads (findBySubjectId) do not hold this lock and may observe a
            // window between entry visibility and frontier update — same as JPA READ COMMITTED.
            entries.put(entry.id, entry);

            if (ledgerConfig.hashChain().enabled()) {
                final var current =
                        frontierRepo.findBySubjectId(entry.subjectId, tenancyId);
                final var newFrontier =
                        LedgerMerkleTree.append(entry.digest, current, entry.subjectId);
                frontierRepo.replace(entry.subjectId, newFrontier, tenancyId);
                merklePublisher.publish(entry.subjectId, entry.sequenceNumber,
                        LedgerMerkleTree.treeRoot(newFrontier));
            }
        }

        return entry;
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> subjectId.equals(e.subjectId))
                .sorted(Comparator.comparingInt(e -> e.sequenceNumber))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to, final String tenancyId) {
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> subjectId.equals(e.subjectId))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> subjectId.equals(e.subjectId))
                .max(Comparator.comparingInt(e -> e.sequenceNumber));
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        return Optional.ofNullable(entries.get(id))
                .filter(e -> tenancyId.equals(e.tenancyId));
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        // Validate the entry belongs to this tenant
        final LedgerEntry entry = entries.get(ledgerEntryId);
        if (entry == null || !tenancyId.equals(entry.tenancyId)) {
            return List.of();
        }
        return attestations.values().stream()
                .filter(a -> ledgerEntryId.equals(a.ledgerEntryId))
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        if (attestation.id == null) {
            attestation.id = UUID.randomUUID();
        }
        if (attestation.occurredAt == null) {
            attestation.occurredAt = Instant.now();
        }
        attestation.attestorId = actorIdentityProvider.tokenise(
                attestation.attestorId, attestation.attestorType);

        // Validate the entry belongs to the specified tenant
        final LedgerEntry entry = entries.get(attestation.ledgerEntryId);
        if (entry != null && !tenancyId.equals(entry.tenancyId)) {
            throw new IllegalArgumentException(
                    "LedgerEntry " + attestation.ledgerEntryId + " does not belong to tenant " + tenancyId);
        }

        attestations.put(attestation.id, attestation);

        if (entry != null && entry.actorId != null) {
            final AttestationRecordedEvent payload =
                    new AttestationRecordedEvent(entry.actorId, entry.id, attestation.id);
            attestationRecordedEvent.fire(payload);
            attestationRecordedEvent.fireAsync(payload)
                    .exceptionally(ex -> { log.debugf(ex, "AttestationRecordedEvent async observer failed"); return null; });
        } else if (entry == null) {
            log.warnf("saveAttestation: no LedgerEntry found for ledgerEntryId=%s — "
                    + "AttestationRecordedEvent not fired", attestation.ledgerEntryId);
        }

        return attestation;
    }

    @Override
    public List<LedgerEntry> findByActorId(final String actorId,
            final Instant from, final Instant to, final String tenancyId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        final String token = tokenOpt.get();
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> token.equals(e.actorId))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole,
            final Instant from, final Instant to, final String tenancyId) {
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> actorRole.equals(e.actorRole))
                .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        return entries.values().stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> entryId.equals(e.causedByEntryId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        // Validate the entry belongs to this tenant
        final LedgerEntry entry = entries.get(entryId);
        if (entry == null || !tenancyId.equals(entry.tenancyId)) {
            return List.of();
        }
        return attestations.values().stream()
                .filter(a -> entryId.equals(a.ledgerEntryId))
                .filter(a -> capabilityTag.equals(a.capabilityTag))
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenancyId) {
        return findAttestationsByEntryIdAndCapabilityTag(entryId, CapabilityTag.GLOBAL, tenancyId);
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(attestorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        final String token = tokenOpt.get();
        // Filter attestations to those whose linked entry belongs to this tenant
        return attestations.values().stream()
                .filter(a -> token.equals(a.attestorId))
                .filter(a -> capabilityTag.equals(a.capabilityTag))
                .filter(a -> {
                    final LedgerEntry e = entries.get(a.ledgerEntryId);
                    return e != null && tenancyId.equals(e.tenancyId);
                })
                .sorted(Comparator.comparing(a -> a.occurredAt))
                .collect(Collectors.toList());
    }

    @Override
    public Stream<LedgerEntry> streamBySubjectId(final UUID subjectId, final String tenancyId) {
        return entries.values().stream()
                      .filter(e -> subjectId.equals(e.subjectId) && tenancyId.equals(e.tenancyId))
                      .sorted(Comparator.comparingInt(e -> e.sequenceNumber));
    }

    @Override
    public Stream<LedgerEntry> streamByActorId(final String actorId, final Instant from,
                                               final Instant to, final String tenancyId) {
        return entries.values().stream()
                      .filter(e -> actorId.equals(e.actorId)
                                   && tenancyId.equals(e.tenancyId)
                                   && !e.occurredAt.isBefore(from)
                                   && !e.occurredAt.isAfter(to))
                      .sorted(Comparator.comparing(e -> e.occurredAt));
    }

    @Override
    public List<LedgerEntry> findBySubjectIdPaged(final UUID subjectId, final int afterSequence,
                                                  final int limit, final String tenancyId) {
        return entries.values().stream()
                      .filter(e -> subjectId.equals(e.subjectId)
                                   && tenancyId.equals(e.tenancyId)
                                   && e.sequenceNumber > afterSequence)
                      .sorted(Comparator.comparingInt(e -> e.sequenceNumber))
                      .limit(limit)
                      .toList();
    }

    @Override
    public Map<AttestationVerdict, Long> countByActorAndVerdict(final String actorId,
                                                                final Instant from, final Instant to, final String tenancyId) {
        final Set<UUID> entryIds = entries.values().stream()
                                          .filter(e -> actorId.equals(e.actorId)
                                                       && tenancyId.equals(e.tenancyId)
                                                       && !e.occurredAt.isBefore(from)
                                                       && !e.occurredAt.isAfter(to))
                                          .map(e -> e.id)
                                          .collect(Collectors.toSet());
        return attestations.values().stream()
                           .filter(a -> entryIds.contains(a.ledgerEntryId))
                           .collect(Collectors.groupingBy(a -> a.verdict, Collectors.counting()));
    }


    

    @Override
    public Map<AttestationVerdict, Long> countBySubjectAndVerdict(final UUID subjectId,
                                                                  final Instant from, final Instant to, final String tenancyId) {
        final Set<UUID> entryIds = entries.values().stream()
                                          .filter(e -> subjectId.equals(e.subjectId)
                                                       && tenancyId.equals(e.tenancyId)
                                                       && !e.occurredAt.isBefore(from)
                                                       && !e.occurredAt.isAfter(to))
                                          .map(e -> e.id)
                                          .collect(Collectors.toSet());
        return attestations.values().stream()
                           .filter(a -> entryIds.contains(a.ledgerEntryId))
                           .collect(Collectors.groupingBy(a -> a.verdict, Collectors.counting()));
    }

    @Override
    public AttestationSummary summariseAttestationsByActor(final String actorId,
                                                           final Instant from, final Instant to, final String tenancyId) {
        final Set<UUID> entryIds = entries.values().stream()
                                          .filter(e -> actorId.equals(e.actorId)
                                                       && tenancyId.equals(e.tenancyId)
                                                       && !e.occurredAt.isBefore(from)
                                                       && !e.occurredAt.isAfter(to))
                                          .map(e -> e.id)
                                          .collect(Collectors.toSet());
        final List<LedgerAttestation> matched = attestations.values().stream()
                                                            .filter(a -> entryIds.contains(a.ledgerEntryId))
                                                            .toList();
        if (matched.isEmpty()) {
            return AttestationSummary.EMPTY;
        }
        final Map<AttestationVerdict, Long> verdictCounts = matched.stream()
                                                                   .collect(Collectors.groupingBy(a -> a.verdict, Collectors.counting()));
        final double mean = matched.stream().mapToDouble(a -> a.confidence).average().orElse(0.0);
        final double min  = matched.stream().mapToDouble(a -> a.confidence).min().orElse(0.0);
        final double max  = matched.stream().mapToDouble(a -> a.confidence).max().orElse(0.0);
        return new AttestationSummary(verdictCounts, matched.size(), mean, min, max);
    }


    /** Package-private accessor — called by sibling in-memory repositories and cross-tenant delegates. */
    Collection<LedgerEntry> allEntries() {
        return entries.values();
    }

    /**
     * Package-private accessor — called by cross-tenant delegate for attestation queries.
     * Returns {@code Collection<runtime.LedgerAttestation>} because cross-tenant consumers
     * operate on the concrete JPA entity type. The stored objects are always
     * {@code runtime.model.LedgerAttestation} instances (passed in via {@code saveAttestation}).
     */
    @SuppressWarnings("unchecked")
    Collection<io.casehub.ledger.runtime.model.LedgerAttestation> allAttestations() {
        return (Collection<io.casehub.ledger.runtime.model.LedgerAttestation>) (Collection<?>) attestations.values();
    }

    /**
     * Resets all in-memory state: entries, attestations, sequence counters, and the Merkle
     * frontier. Call at session boundaries (e.g. game-over) to prevent unbounded growth in
     * long-running deployments. Also called by {@code @BeforeEach} in test suites.
     */
    public void clear() {
        entries.clear();
        attestations.clear();
        sequenceCounters.clear();
        subjectLocks.clear();
        if (frontierRepo instanceof InMemoryLedgerMerkleFrontierRepository m) {
            m.clear();
        }
    }
}
