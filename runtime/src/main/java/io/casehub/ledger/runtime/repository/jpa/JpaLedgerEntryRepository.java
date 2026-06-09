package io.casehub.ledger.runtime.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.privacy.ActorIdentityProvider;
import io.casehub.ledger.runtime.privacy.DecisionContextSanitiser;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.runtime.service.AttestationRecordedEvent;
import io.casehub.ledger.runtime.service.LedgerMerklePublisher;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;

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
    DecisionContextSanitiser decisionContextSanitiser;

    @Inject
    LedgerSequenceAllocator sequenceAllocator;

    @Inject
    Event<AttestationRecordedEvent> attestationRecordedEvent;

    /** {@inheritDoc} */
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

        if (entry.actorId != null) {
            entry.actorId = actorIdentityProvider.tokenise(entry.actorId);
        }

        entry.compliance().ifPresent(cs -> {
            if (cs.decisionContext != null) {
                cs.decisionContext = decisionContextSanitiser.sanitise(cs.decisionContext);
                entry.refreshSupplementJson();
            }
        });

        entry.sequenceNumber = sequenceAllocator.nextSequenceNumber(entry.subjectId);

        if (ledgerConfig.hashChain().enabled()) {
            entry.digest = LedgerMerkleTree.leafHash(entry);
        }
        em.persist(entry);

        if (ledgerConfig.hashChain().enabled()) {
            updateMerkleFrontier(entry, tenancyId);
        }

        return entry;
    }

    private void updateMerkleFrontier(final LedgerEntry entry, final String tenancyId) {
        final List<LedgerMerkleFrontier> currentFrontier = frontierRepo.findBySubjectId(entry.subjectId, tenancyId);
        final List<LedgerMerkleFrontier> newFrontier = LedgerMerkleTree.append(
                entry.digest, currentFrontier, entry.subjectId);
        frontierRepo.replace(entry.subjectId, newFrontier, tenancyId);
        final String newRoot = LedgerMerkleTree.treeRoot(newFrontier);
        merklePublisher.publish(entry.subjectId, entry.sequenceNumber, newRoot);
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId, final Instant from, final Instant to,
            final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId" +
                " AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber DESC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.id = :id AND e.tenancyId = :tenancyId",
                LedgerEntry.class)
                .setParameter("id", id)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        return em.createQuery(
                "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id " +
                "WHERE a.ledgerEntryId = :entryId AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC",
                LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        // Validate the referenced entry belongs to this tenant before persisting
        final LedgerEntry entry = em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.id = :id AND e.tenancyId = :tenancyId", LedgerEntry.class)
                .setParameter("id", attestation.ledgerEntryId)
                .setParameter("tenancyId", tenancyId)
                .getResultStream().findFirst().orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "LedgerEntry " + attestation.ledgerEntryId + " not found in tenant " + tenancyId);
        }

        if (attestation.attestorId != null) {
            attestation.attestorId = actorIdentityProvider.tokenise(attestation.attestorId);
        }
        em.persist(attestation);

        if (entry.actorId != null) {
            attestationRecordedEvent.fire(
                    new AttestationRecordedEvent(entry.actorId, entry.id, attestation.id));
        }

        return attestation;
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorId(final String actorId,
            final Instant from, final Instant to, final String tenancyId) {
        final String token = actorIdentityProvider.tokeniseForQuery(actorId);
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId" +
                        " AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorId", token)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole,
            final Instant from, final Instant to, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorRole = :actorRole" +
                        " AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorRole", actorRole)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.causedByEntryId = :entryId AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("entryId", entryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(final UUID entryId,
            final String capabilityTag, final String tenancyId) {
        return em.createQuery(
                "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id " +
                "WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = :capabilityTag AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC",
                LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .setParameter("capabilityTag", capabilityTag)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenancyId) {
        return em.createQuery(
                "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id " +
                "WHERE a.ledgerEntryId = :entryId AND a.capabilityTag = '*' AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC",
                LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(final String attestorId,
            final String capabilityTag, final String tenancyId) {
        final String token = actorIdentityProvider.tokeniseForQuery(attestorId);
        return em.createQuery(
                "SELECT a FROM LedgerAttestation a JOIN LedgerEntry e ON a.ledgerEntryId = e.id " +
                "WHERE a.attestorId = :attestorId AND a.capabilityTag = :capabilityTag AND e.tenancyId = :tenancyId ORDER BY a.occurredAt ASC",
                LedgerAttestation.class)
                .setParameter("attestorId", token)
                .setParameter("capabilityTag", capabilityTag)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }
}
