package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.config.LedgerProperties;
import io.casehub.ledger.core.enricher.EnricherPipelineCore;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;
import io.casehub.ledger.core.model.AttestationRecordedEvent;
import io.casehub.ledger.core.privacy.ContentSanitiser;
import io.casehub.ledger.core.service.MerklePublisherCore;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.jpa.JpaComplianceSupplement;
import io.casehub.ledger.jpa.JpaLedgerEntry;
import io.casehub.ledger.jpa.JpaProvenanceSupplement;
import io.casehub.ledger.jpa.LedgerSequenceAllocator;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringJpaLedgerEntryRepository implements LedgerEntryRepository {

    private final EntityManager em;
    private final LedgerProperties properties;
    private final LedgerMerkleFrontierRepository frontierRepo;
    private final ActorIdentityProvider actorIdentityProvider;
    private final ContentSanitiser contentSanitiser;
    private final LedgerSequenceAllocator sequenceAllocator;
    private final EnricherPipelineCore enricherPipeline;
    private final AgentEntrySigner agentEntrySigner;
    private final MerklePublisherCore merklePublisher;
    private final Consumer<AttestationRecordedEvent> attestationEventConsumer;

    public SpringJpaLedgerEntryRepository(EntityManager em,
                                           LedgerProperties properties,
                                           LedgerMerkleFrontierRepository frontierRepo,
                                           ActorIdentityProvider actorIdentityProvider,
                                           ContentSanitiser contentSanitiser,
                                           LedgerSequenceAllocator sequenceAllocator,
                                           EnricherPipelineCore enricherPipeline,
                                           AgentEntrySigner agentEntrySigner,
                                           MerklePublisherCore merklePublisher,
                                           Consumer<AttestationRecordedEvent> attestationEventConsumer) {
        this.em = em;
        this.properties = properties;
        this.frontierRepo = frontierRepo;
        this.actorIdentityProvider = actorIdentityProvider;
        this.contentSanitiser = contentSanitiser;
        this.sequenceAllocator = sequenceAllocator;
        this.enricherPipeline = enricherPipeline;
        this.agentEntrySigner = agentEntrySigner;
        this.merklePublisher = merklePublisher;
        this.attestationEventConsumer = attestationEventConsumer;
    }

    @Override
    @Transactional
    public LedgerEntry save(LedgerEntry entry, String tenancyId) {
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

        agentEntrySigner.prepareKey(entry);
        enricherPipeline.enrich(entry);

        if (properties.hashChain().enabled()) {
            entry.digest = LedgerMerkleTree.leafHash(entry);
        }

        agentEntrySigner.sign(entry);

        em.persist(entry);

        JpaLedgerEntry jpaEntry = (entry instanceof JpaLedgerEntry jpa) ? jpa : null;
        for (io.casehub.ledger.api.model.supplement.LedgerSupplement supplement : entry.supplements) {
            if (supplement instanceof JpaComplianceSupplement jcs) {
                if (jpaEntry != null) { jcs.jpaLedgerEntry = jpaEntry; }
                em.persist(jcs);
            } else if (supplement instanceof JpaProvenanceSupplement jps) {
                if (jpaEntry != null) { jps.jpaLedgerEntry = jpaEntry; }
                em.persist(jps);
            }
        }

        if (properties.hashChain().enabled()) {
            updateMerkleFrontier(entry, tenancyId);
        }

        return entry;
    }

    private void updateMerkleFrontier(LedgerEntry entry, String tenancyId) {
        var currentFrontier = frontierRepo.findBySubjectId(entry.subjectId, tenancyId);
        List<io.casehub.ledger.api.model.LedgerMerkleFrontier> newFrontier = LedgerMerkleTree.append(
                entry.digest, currentFrontier, entry.subjectId);
        frontierRepo.replace(entry.subjectId, newFrontier, tenancyId);
        String newRoot = LedgerMerkleTree.treeRoot(newFrontier);
        merklePublisher.publish(entry.subjectId, entry.sequenceNumber, newRoot);
    }

    @Override
    public List<LedgerEntry> findBySubjectId(UUID subjectId, String tenancyId) {
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectId", LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId) {
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectIdAndTimeRange", LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(UUID subjectId, String tenancyId) {
        Optional<LedgerEntry> result = em.createNamedQuery("LedgerEntry.findLatestBySubjectId", LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
        result.ifPresent(this::loadSupplements);
        return result;
    }

    @Override
    public Optional<LedgerEntry> findEntryById(UUID id, String tenancyId) {
        Optional<LedgerEntry> result = em.createNamedQuery("LedgerEntry.findByIdAndTenancyId", LedgerEntry.class)
                .setParameter("id", id)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst();
        result.ifPresent(this::loadSupplements);
        return result;
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId, String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndTenancyId", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LedgerAttestation saveAttestation(LedgerAttestation attestation, String tenancyId) {
        LedgerEntry entry = em.createNamedQuery("LedgerEntry.findByIdAndTenancyId", LedgerEntry.class)
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
            attestationEventConsumer.accept(
                    new AttestationRecordedEvent(entry.actorId, entry.id, attestation.id));
        }

        return attestation;
    }

    @Override
    public List<LedgerEntry> findByActorId(String actorId, Instant from, Instant to, String tenancyId) {
        Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(actorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findByActorIdAndTimeRange", LedgerEntry.class)
                .setParameter("actorId", tokenOpt.get())
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public List<LedgerEntry> findByActorRole(String actorRole, Instant from, Instant to, String tenancyId) {
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findByActorRoleAndTimeRange", LedgerEntry.class)
                .setParameter("actorRole", actorRole)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public List<LedgerEntry> findCausedBy(UUID entryId, String tenancyId) {
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findCausedBy", LedgerEntry.class)
                .setParameter("entryId", entryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String capabilityTag, String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndCapabilityTagAndTenancyId", LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .setParameter("capabilityTag", capabilityTag)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findGlobalByEntryIdAndTenancyId", LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String capabilityTag, String tenancyId) {
        Optional<String> tokenOpt = actorIdentityProvider.tokeniseForQuery(attestorId);
        if (tokenOpt.isEmpty()) {
            return List.of();
        }
        return em.createNamedQuery("LedgerAttestation.findByAttestorIdAndCapabilityTagAndTenancyId", LedgerAttestation.class)
                .setParameter("attestorId", tokenOpt.get())
                .setParameter("capabilityTag", capabilityTag)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findPeerAttestationsByAttestorIds(Set<String> attestorIds, String tenancyId) {
        if (attestorIds == null || attestorIds.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String id : attestorIds) {
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
    public Map<String, Map<String, Long>> findPeerAttestationPairCounts(Set<String> attestorIds, String tenancyId) {
        if (attestorIds == null || attestorIds.isEmpty()) {
            return Map.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String id : attestorIds) {
            actorIdentityProvider.tokeniseForQuery(id).ifPresent(tokens::add);
        }
        if (tokens.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
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

        Map<String, Map<String, Long>> result = new HashMap<>();
        for (Object[] row : rows) {
            result.computeIfAbsent((String) row[0], k -> new HashMap<>()).put((String) row[1], (Long) row[2]);
        }
        return result;
    }

    @Override
    public Stream<LedgerEntry> streamBySubjectId(UUID subjectId, String tenancyId) {
        return em.createNamedQuery("LedgerEntry.streamBySubjectId", LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultStream();
    }

    @Override
    public Stream<LedgerEntry> streamByActorId(String actorId, Instant from, Instant to, String tenancyId) {
        return em.createNamedQuery("LedgerEntry.streamByActorIdAndTimeRange", LedgerEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultStream();
    }

    @Override
    public List<LedgerEntry> findBySubjectIdPaged(UUID subjectId, int afterSequence, int limit, String tenancyId) {
        List<LedgerEntry> results = em.createNamedQuery("LedgerEntry.findBySubjectIdPaged", LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("afterSequence", afterSequence)
                .setParameter("tenancyId", tenancyId)
                .setMaxResults(limit)
                .getResultList();
        loadSupplements(results);
        return results;
    }

    @Override
    public Map<AttestationVerdict, Long> countByActorAndVerdict(String actorId, Instant from, Instant to, String tenancyId) {
        List<Object[]> rows = em.createNamedQuery("LedgerAttestation.countByActorAndVerdict", Object[].class)
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
    public Map<AttestationVerdict, Long> countBySubjectAndVerdict(UUID subjectId, Instant from, Instant to, String tenancyId) {
        List<Object[]> rows = em.createNamedQuery("LedgerAttestation.countBySubjectAndVerdict", Object[].class)
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
    public AttestationSummary summariseAttestationsByActor(String actorId, Instant from, Instant to, String tenancyId) {
        List<Object[]> rows = em.createNamedQuery("LedgerAttestation.summariseByActor", Object[].class)
                .setParameter("actorId", actorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
        if (rows.isEmpty()) {
            return AttestationSummary.EMPTY;
        }
        Map<AttestationVerdict, Long> verdictCounts = rows.stream()
                .collect(Collectors.toMap(r -> (AttestationVerdict) r[0], r -> (Long) r[1]));
        long total = 0;
        double weightedSum = 0.0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Object[] row : rows) {
            long count = (Long) row[1];
            double avg = (Double) row[2];
            double rowMin = (Double) row[3];
            double rowMax = (Double) row[4];
            total += count;
            weightedSum += avg * count;
            min = Math.min(min, rowMin);
            max = Math.max(max, rowMax);
        }
        return new AttestationSummary(verdictCounts, total, weightedSum / total, min, max);
    }

    private void loadSupplements(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<UUID> entryIds = entries.stream().map(e -> e.id).toList();
        Map<UUID, LedgerEntry> byId = new HashMap<>();
        for (LedgerEntry e : entries) {
            byId.put(e.id, e);
        }

        List<JpaComplianceSupplement> complianceSupplements = em
                .createNamedQuery("JpaComplianceSupplement.findByEntryIds", JpaComplianceSupplement.class)
                .setParameter("ids", entryIds)
                .getResultList();
        for (JpaComplianceSupplement cs : complianceSupplements) {
            LedgerEntry entry = byId.get(cs.jpaLedgerEntry.id);
            if (entry != null) {
                entry.supplements.add(cs);
            }
        }

        List<JpaProvenanceSupplement> provenanceSupplements = em
                .createNamedQuery("JpaProvenanceSupplement.findByEntryIds", JpaProvenanceSupplement.class)
                .setParameter("ids", entryIds)
                .getResultList();
        for (JpaProvenanceSupplement ps : provenanceSupplements) {
            LedgerEntry entry = byId.get(ps.jpaLedgerEntry.id);
            if (entry != null) {
                entry.supplements.add(ps);
            }
        }
    }

    private void loadSupplements(LedgerEntry entry) {
        loadSupplements(List.of(entry));
    }
}
