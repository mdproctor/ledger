package io.casehub.ledger.runtime.model.jpa;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.supplement.LedgerSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.ledger.runtime.service.LedgerTraceListener;
import io.casehub.ledger.runtime.service.identity.LedgerIdentityEnforcementListener;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for ledger entries.
 *
 * <p>
 * Extends the api-tier {@link LedgerEntry} ({@code @MappedSuperclass}) and adds
 * JPA-specific machinery:
 * <ul>
 * <li>{@code @Entity(name = "LedgerEntry")} — preserves JPQL entity name for all
 *     {@code @NamedQuery} declarations</li>
 * <li>{@code @Inheritance(JOINED)} with discriminator column</li>
 * <li>{@code @EntityListeners} for the enrichment and identity enforcement pipelines</li>
 * <li>{@code @PrePersist} as defensive fallback for ID and timestamp assignment</li>
 * <li>{@code pendingIdentityStatus} — transient runtime implementation detail</li>
 * <li>Per-type {@code @OneToMany} supplement fields for JPA-managed supplement persistence</li>
 * </ul>
 *
 * <p>
 * Domain-specific subclasses (e.g. {@code WorkItemLedgerEntry} in Tarkus) extend
 * this class and add a sibling table joined on {@code id}. Internal subclasses
 * ({@code PlainLedgerEntry}, {@code KeyRotationEntry}, etc.) also extend this class.
 */
@NamedQuery(
        name = "LedgerEntry.listAll",
        query = "SELECT e FROM LedgerEntry e")
@NamedQuery(
        name = "LedgerEntry.findAllEvents",
        query = "SELECT e FROM LedgerEntry e WHERE e.entryType = :type")
@NamedQuery(
        name = "LedgerEntry.findEventsByActorId",
        query = "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.entryType = :type")
@NamedQuery(
        name = "LedgerEntry.findByTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findByIdAndTenancyId",
        query = "SELECT e FROM LedgerEntry e WHERE e.id = :id AND e.tenancyId = :tenancyId")
@NamedQuery(
        name = "LedgerEntry.findSequenceStats",
        query = """
                SELECT NEW io.casehub.ledger.core.model.SubjectSequenceStats(
                    e.subjectId, e.tenancyId, COUNT(e), MIN(e.sequenceNumber), MAX(e.sequenceNumber)
                )
                FROM LedgerEntry e
                GROUP BY e.subjectId, e.tenancyId
                """)
@NamedQuery(
        name = "LedgerEntry.findBySubjectId",
        query = "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC")
@NamedQuery(
        name = "LedgerEntry.findBySubjectIdAndTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findLatestBySubjectId",
        query = "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber DESC")
@NamedQuery(
        name = "LedgerEntry.findByActorIdAndTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findByActorRoleAndTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.actorRole = :actorRole AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findCausedBy",
        query = "SELECT e FROM LedgerEntry e WHERE e.causedByEntryId = :entryId AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.streamBySubjectId",
        query = "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC")
@NamedQuery(
        name = "LedgerEntry.streamByActorIdAndTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.occurredAt >= :from AND e.occurredAt <= :to AND e.tenancyId = :tenancyId ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findBySubjectIdPaged",
        query = "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.sequenceNumber > :afterSequence AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC")
@Entity(name = "LedgerEntry")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Table(name = "ledger_entry")
@EntityListeners({LedgerTraceListener.class, LedgerIdentityEnforcementListener.class})
public abstract class JpaLedgerEntry extends LedgerEntry {

    /**
     * Set by ActorIdentityValidationEnricher during enrichment — NOT persisted.
     * Read by LedgerIdentityEnforcementListener to enforce ENFORCE mode after enrichment runs.
     * Never null when actorDid is set and enrichment completed.
     */
    @Transient
    public IdentityBindingStatus pendingIdentityStatus;

    // ── Supplement JPA fields ─────────────────────────────────────────────────

    /**
     * JPA-managed compliance supplements. Mapped by the {@code jpaLedgerEntry}
     * field on {@link JpaComplianceSupplement}.
     */
    @OneToMany(mappedBy = "jpaLedgerEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JpaComplianceSupplement> complianceSupplements = new ArrayList<>();

    /**
     * JPA-managed provenance supplements. Mapped by the {@code jpaLedgerEntry}
     * field on {@link JpaProvenanceSupplement}.
     */
    @OneToMany(mappedBy = "jpaLedgerEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JpaProvenanceSupplement> provenanceSupplements = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Defensive fallback — assigns UUID primary key and sets {@code occurredAt}
     * before the entity is inserted. The save pipeline normally sets these earlier,
     * but direct {@code em.persist()} calls (if hash chain is disabled) should still
     * produce valid entities.
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = java.time.Instant.now();
        }
    }

    // ── Supplement synchronisation ────────────────────────────────────────────

    /**
     * Overrides the api-tier {@link LedgerEntry#attach(LedgerSupplement)} to
     * synchronise both the transient {@code supplements} list and the per-type
     * JPA collections. The JPA relationship is set on the supplement so that
     * cascade persist works correctly.
     *
     * @param supplement the supplement to attach; must not be null
     */
    @Override
    public void attach(final LedgerSupplement supplement) {
        Objects.requireNonNull(supplement, "supplement must not be null");

        // Set the JPA relationship on concrete supplement entities
        if (supplement instanceof final JpaComplianceSupplement jcs) {
            jcs.jpaLedgerEntry = this;
            complianceSupplements.removeIf(s -> true);
            complianceSupplements.add(jcs);
        } else if (supplement instanceof final JpaProvenanceSupplement jps) {
            jps.jpaLedgerEntry = this;
            provenanceSupplements.removeIf(s -> true);
            provenanceSupplements.add(jps);
        }

        // Delegate to api-tier attach for transient list + supplementJson sync
        super.attach(supplement);
    }

    /**
     * Populates the transient {@code supplements} list from the JPA-managed
     * per-type collections. Called explicitly after entity load when supplements
     * have been fetched (e.g. by batch loading in the repository). Not a
     * {@code @PostLoad} callback because the {@code @OneToMany} collections are
     * lazy and triggering them in PostLoad would cause N+1 queries.
     */
    public void syncSupplementsFromJpa() {
        supplements.clear();
        supplements.addAll(complianceSupplements);
        supplements.addAll(provenanceSupplements);
    }
}
