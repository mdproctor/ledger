package io.casehub.ledger.runtime.repository.jpa;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.core.privacy.ContentSanitiser;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.runtime.service.LedgerEnricherPipeline;
import io.casehub.ledger.runtime.service.LedgerMerklePublisher;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hibernate ORM implementation of {@link LedgerEntryRepository} using EntityManager directly.
 *
 * <p>
 * All queries are tenant-scoped — every method receives a {@code tenancyId} parameter
 * and filters results to that tenant. Cross-tenant operations are provided by
 * {@link JpaCrossTenantLedgerEntryRepository}.
 *
 * <p>
 * Queries on {@link LedgerEntry} are polymorphic — Hibernate joins to all registered
 * subclass tables and returns the correct concrete type for each row.
 *
 * <p>
 * {@link LedgerEntry} is a plain {@code @Entity} (not a PanacheEntityBase subclass), so
 * Panache repository bytecode enhancement cannot be used here. All queries go through
 * {@link EntityManager} directly.
 *
 * <p>
 * Marked {@code @Alternative} so that domain-specific extensions (e.g. Tarkus's
 * {@code JpaWorkItemLedgerEntryRepository}) can provide a single, unambiguous
 * {@code LedgerEntryRepository} bean without CDI conflicts.
 *
 * <p>
 * <b>Activation:</b> when no domain-specific repository is present (standalone deployments,
 * test modules, or extensions that use runtime services like {@code TrustScoreJob} without
 * a domain repo), activate this class via one of:
 * <ul>
 * <li>{@code quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository}
 * in {@code application.properties} (Quarkus-native, preferred)</li>
 * <li>{@code <alternatives>} in {@code META-INF/beans.xml} (standard CDI)</li>
 * <li>Subclass with {@code @ApplicationScoped} (inherits all polymorphic query logic)</li>
 * </ul>
 * When a domain-specific {@code LedgerEntryRepository} is present, no activation is needed —
 * this class stays dormant.
 */
@ApplicationScoped
@Alternative
public class JpaLedgerEntryRepository implements LedgerEntryRepository {

    private static final Logger log = Logger.getLogger(JpaLedgerEntryRepository.class);

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Inject
    LedgerConfig ledgerConfig;

    @Inject
    LedgerMerklePublisher merklePublisher;

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    @Inject
    ContentSanitiser contentSanitiser;

    @Inject
    LedgerSequenceAllocator sequenceAllocator;

    @Inject
    LedgerEnricherPipeline enricherPipeline;

    @Inject
    AgentEntrySigner agentEntrySigner;

    @Inject
    Event<AttestationRecordedEvent> attestationRecordedEvent;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        entry.tenancyId = tenancyId;

        if (entry.subjectId == null) {
            throw new IllegalArgumentException("LedgerEntry.subjectId must not be null");
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

        entry.sequenceNumber = sequenceAllocator.nextSequenceNumber(entry.subjectId, tenancyId);

        // Pipeline: prepareKey → enrich → hash → sign → persist
        agentEntrySigner.prepareKey(entry);
        enricherPipeline.enrich(entry);

        if (ledgerConfig.hashChain().enabled()) {
            entry.digest = LedgerMerkleTree.leafHash(entry);
        }

        agentEntrySigner.sign(entry);

        em.persist(entry);

        // Supplements are @Transient on api LedgerEntry — persist each JPA supplement explicitly.
        // JpaLedgerEntry has @OneToMany with cascade but we persist explicitly for entries
        // that may not have used attach() (e.g. supplements added directly to the list).
        final io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry jpaEntry =
                (entry instanceof io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry jpa) ? jpa : null;
        for (final io.casehub.ledger.api.model.supplement.LedgerSupplement supplement : entry.supplements) {
            if (supplement instanceof final io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement jcs) {
                if (jpaEntry != null) {jcs.jpaLedgerEntry = jpaEntry;}
                em.persist(jcs);
            } else if (supplement instanceof final io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement jps) {
                if (jpaEntry != null) {jps.jpaLedgerEntry = jpaEntry;}
                em.persist(jps);
            }
        }

        if (ledgerConfig.hashChain().enabled()) {
            updateMerkleFrontier(entry, tenancyId);
        }

        return entry;
    }

    private void updateMerkleFrontier(final LedgerEntry entry, final String tenancyId) {
        final var currentFrontier = frontierRepo.findBySubjectId(entry.subjectId, tenancyId);
        final List<io.casehub.ledger.api.model.LedgerMerkleFrontier> newFrontier = LedgerMerkleTree.append(
                entry.digest, currentFrontier, entry.subjectId);
        frontierRepo.replace(entry.subjectId, newFrontier, tenancyId);
        final String newRoot = LedgerMerkleTree.treeRoot(newFrontier);
        merklePublisher.publish(entry.subjectId, entry.sequenceNumber, newRoot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectId", LedgerEntry.class)
                                            .setParameter("subjectId", subjectId)
                                            .setParameter("tenancyId", tenancyId)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId, final Instant from, final Instant to,
                                                         final String tenancyId) {
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectIdAndTimeRange", LedgerEntry.class)
                                            .setParameter("subjectId", subjectId)
                                            .setParameter("from", from)
                                            .setParameter("to", to)
                                            .setParameter("tenancyId", tenancyId)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        final Optional<LedgerEntry> result = em.createNamedQuery("LedgerEntry.findLatestBySubjectId", LedgerEntry.class)
                                               .setParameter("subjectId", subjectId)
                                               .setParameter("tenancyId", tenancyId)
                                               .setMaxResults(1)
                                               .getResultStream()
                                               .findFirst();
        result.ifPresent(this::loadSupplements);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        final Optional<LedgerEntry> result = em.createNamedQuery("LedgerEntry.findByIdAndTenancyId", LedgerEntry.class)
                                               .setParameter("id", id)
                                               .setParameter("tenancyId", tenancyId)
                                               .getResultStream()
                                               .findFirst();
        result.ifPresent(this::loadSupplements);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndTenancyId", LedgerAttestation.class)
                 .setParameter("entryId", ledgerEntryId)
                 .setParameter("tenancyId", tenancyId)
                 .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        final LedgerEntry entry = em.createNamedQuery("LedgerEntry.findByIdAndTenancyId", LedgerEntry.class)
                                    .setParameter("id", attestation.ledgerEntryId)
                                    .setParameter("tenancyId", tenancyId)
                                    .getResultStream().findFirst().orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "LedgerEntry " + attestation.ledgerEntryId + " not found in tenant " + tenancyId);
        }

        attestation.attestorId = actorIdentityProvider.tokenise(
                attestation.attestorId, attestation.attestorType);
        em.persist(attestation);

        if (entry.actorId != null) {
            final AttestationRecordedEvent payload =
                    new AttestationRecordedEvent(entry.actorId, entry.id, attestation.id);
            attestationRecordedEvent.fire(payload);
            attestationRecordedEvent.fireAsync(payload)
                                    .exceptionally(ex -> {
                                        log.debugf(ex, "AttestationRecordedEvent async observer failed");
                                        return null;
                                    });
        }

        return attestation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerEntry> findByActorId(final String actorId,
                                           final Instant from, final Instant to, final String tenancyId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        final String token = tokenOpt.get();
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findByActorIdAndTimeRange", LedgerEntry.class)
                                            .setParameter("actorId", token)
                                            .setParameter("from", from)
                                            .setParameter("to", to)
                                            .setParameter("tenancyId", tenancyId)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole,
                                             final Instant from, final Instant to, final String tenancyId) {
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findByActorRoleAndTimeRange", LedgerEntry.class)
                                            .setParameter("actorRole", actorRole)
                                            .setParameter("from", from)
                                            .setParameter("to", to)
                                            .setParameter("tenancyId", tenancyId)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findCausedBy", LedgerEntry.class)
                                            .setParameter("entryId", entryId)
                                            .setParameter("tenancyId", tenancyId)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(final UUID entryId,
                                                                             final String capabilityTag, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndCapabilityTagAndTenancyId", LedgerAttestation.class)
                 .setParameter("entryId", entryId)
                 .setParameter("capabilityTag", capabilityTag)
                 .setParameter("tenancyId", tenancyId)
                 .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findGlobalByEntryIdAndTenancyId", LedgerAttestation.class)
                 .setParameter("entryId", entryId)
                 .setParameter("tenancyId", tenancyId)
                 .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(final String attestorId,
                                                                                final String capabilityTag, final String tenancyId) {
        final Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(attestorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        final String token = tokenOpt.get();
        return em.createNamedQuery("LedgerAttestation.findByAttestorIdAndCapabilityTagAndTenancyId", LedgerAttestation.class)
                 .setParameter("attestorId", token)
                 .setParameter("capabilityTag", capabilityTag)
                 .setParameter("tenancyId", tenancyId)
                 .getResultList();
    }

    // ── Supplement loading ────────────────────────────────────────────────────


    @Override
    public List<LedgerAttestation> findPeerAttestationsByAttestorIds(final java.util.Set<String> attestorIds, final String tenancyId) {
        if (attestorIds == null || attestorIds.isEmpty()) {
            return List.of();
        }
        final java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        for (final String id : attestorIds) {
            actorIdentityProvider.tokeniseForQuery(id).ifPresent(tokens::add);
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                         "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id"
                         + " WHERE a.attestorId IN :attestorIds"
                         + " AND a.verdict IN (io.casehub.ledger.api.model.AttestationVerdict.ENDORSED,"
                         + " io.casehub.ledger.api.model.AttestationVerdict.CHALLENGED)"
                         + " AND e.tenancyId = :tenancyId",
                         LedgerAttestation.class)
                 .setParameter("attestorIds", tokens)
                 .setParameter("tenancyId", tenancyId)
                 .getResultList();
    }

    @Override
    public Map<String, Map<String, Long>> findPeerAttestationPairCounts(final java.util.Set<String> attestorIds, final String tenancyId) {
        if (attestorIds == null || attestorIds.isEmpty()) {
            return Map.of();
        }
        final java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        for (final String id : attestorIds) {
            actorIdentityProvider.tokeniseForQuery(id).ifPresent(tokens::add);
        }
        if (tokens.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked") final List<Object[]> rows = em.createQuery(
                                                                             "SELECT a.attestorId, e.actorId, COUNT(a) FROM LedgerAttestation a"
                                                                             + " JOIN LedgerEntry e ON a.ledgerEntryId = e.id"
                                                                             + " WHERE a.attestorId IN :attestorIds"
                                                                             + " AND a.verdict IN (io.casehub.ledger.api.model.AttestationVerdict.ENDORSED,"
                                                                             + " io.casehub.ledger.api.model.AttestationVerdict.CHALLENGED)"
                                                                             + " AND e.tenancyId = :tenancyId"
                                                                             + " GROUP BY a.attestorId, e.actorId")
                                                                     .setParameter("attestorIds", tokens)
                                                                     .setParameter("tenancyId", tenancyId)
                                                                     .getResultList();

        final Map<String, Map<String, Long>> result = new HashMap<>();
        for (final Object[] row : rows) {
            final String attestorId     = (String) row[0];
            final String subjectActorId = (String) row[1];
            final Long   count          = (Long) row[2];
            result.computeIfAbsent(attestorId, k -> new HashMap<>()).put(subjectActorId, count);
        }
        return result;
    }

    @Override
    public Stream<LedgerEntry> streamBySubjectId(final UUID subjectId, final String tenancyId) {
        return em.createNamedQuery("LedgerEntry.streamBySubjectId", LedgerEntry.class)
                 .setParameter("subjectId", subjectId)
                 .setParameter("tenancyId", tenancyId)
                 .getResultStream();
    }

    @Override
    public Stream<LedgerEntry> streamByActorId(final String actorId, final Instant from,
                                               final Instant to, final String tenancyId) {
        return em.createNamedQuery("LedgerEntry.streamByActorIdAndTimeRange", LedgerEntry.class)
                 .setParameter("actorId", actorId)
                 .setParameter("from", from)
                 .setParameter("to", to)
                 .setParameter("tenancyId", tenancyId)
                 .getResultStream();
    }

    @Override
    public List<LedgerEntry> findBySubjectIdPaged(final UUID subjectId, final int afterSequence,
                                                  final int limit, final String tenancyId) {
        final List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectIdPaged", LedgerEntry.class)
                                            .setParameter("subjectId", subjectId)
                                            .setParameter("afterSequence", afterSequence)
                                            .setParameter("tenancyId", tenancyId)
                                            .setMaxResults(limit)
                                            .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public Map<AttestationVerdict, Long> countByActorAndVerdict(final String actorId,
                                                                final Instant from, final Instant to, final String tenancyId) {
        final List<Object[]> rows = em.createNamedQuery("LedgerAttestation.countByActorAndVerdict", Object[].class)
                                      .setParameter("actorId", actorId)
                                      .setParameter("from", from)
                                      .setParameter("to", to)
                                      .setParameter("tenancyId", tenancyId)
                                      .getResultList();
        return rows.stream().collect(Collectors.toMap(
                r -> (AttestationVerdict) r[0],
                r -> (Long) r[1]));
    }

    @Override
    public Map<AttestationVerdict, Long> countBySubjectAndVerdict(final UUID subjectId,
                                                                  final Instant from, final Instant to, final String tenancyId) {
        final List<Object[]> rows = em.createNamedQuery("LedgerAttestation.countBySubjectAndVerdict", Object[].class)
                                      .setParameter("subjectId", subjectId)
                                      .setParameter("from", from)
                                      .setParameter("to", to)
                                      .setParameter("tenancyId", tenancyId)
                                      .getResultList();
        return rows.stream().collect(Collectors.toMap(
                r -> (AttestationVerdict) r[0],
                r -> (Long) r[1]));
    }

    @Override
    public AttestationSummary summariseAttestationsByActor(final String actorId,
                                                           final Instant from, final Instant to, final String tenancyId) {
        final List<Object[]> rows = em.createNamedQuery("LedgerAttestation.summariseByActor", Object[].class)
                                      .setParameter("actorId", actorId)
                                      .setParameter("from", from)
                                      .setParameter("to", to)
                                      .setParameter("tenancyId", tenancyId)
                                      .getResultList();
        if (rows.isEmpty()) {
            return AttestationSummary.EMPTY;
        }
        final Map<AttestationVerdict, Long> verdictCounts = rows.stream()
                                                                .collect(Collectors.toMap(r -> (AttestationVerdict) r[0], r -> (Long) r[1]));
        long   total       = 0;
        double weightedSum = 0.0;
        double min         = Double.MAX_VALUE;
        double max         = Double.MIN_VALUE;
        for (final Object[] row : rows) {
            final long   count  = (Long) row[1];
            final double avg    = (Double) row[2];
            final double rowMin = (Double) row[3];
            final double rowMax = (Double) row[4];
            total += count;
            weightedSum += avg * count;
            min = Math.min(min, rowMin);
            max = Math.max(max, rowMax);
        }
        return new AttestationSummary(verdictCounts, total, weightedSum / total, min, max);
    }


    /**
     * Loads supplements from their self-contained tables and attaches them to entries.
     * Called after JPQL queries since supplements are {@code @Transient} on LedgerEntry.
     */
    private void loadSupplements(final List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        final List<UUID> entryIds = entries.stream().map(e -> e.id).toList();
        final Map<UUID, LedgerEntry> byId = new HashMap<>();
        for (final LedgerEntry e : entries) {
            byId.put(e.id, e);
        }

        final List<JpaComplianceSupplement> complianceSupplements = em
                .createNamedQuery("JpaComplianceSupplement.findByEntryIds", JpaComplianceSupplement.class)
                .setParameter("ids", entryIds)
                .getResultList();
        for (final JpaComplianceSupplement cs : complianceSupplements) {
            final LedgerEntry entry = byId.get(cs.jpaLedgerEntry.id);
            if (entry != null) {
                entry.supplements.add(cs);
            }
        }

        final List<JpaProvenanceSupplement> provenanceSupplements = em
                .createNamedQuery("JpaProvenanceSupplement.findByEntryIds", JpaProvenanceSupplement.class)
                .setParameter("ids", entryIds)
                .getResultList();
        for (final JpaProvenanceSupplement ps : provenanceSupplements) {
            final LedgerEntry entry = byId.get(ps.jpaLedgerEntry.id);
            if (entry != null) {
                entry.supplements.add(ps);
            }
        }
    }

    /**
     * Loads supplements for a single entry.
     */
    private void loadSupplements(final LedgerEntry entry) {
        loadSupplements(List.of(entry));
    }
}
