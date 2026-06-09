package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * SPI for persisting and querying {@link LedgerEntry} and {@link LedgerAttestation} records.
 *
 * <p>
 * Queries operate on the base {@link LedgerEntry} type — polymorphic results include all
 * registered subclasses. The built-in implementation ({@code JpaLedgerEntryRepository})
 * uses Hibernate ORM + EntityManager directly. Alternative implementations can be
 * substituted via CDI.
 */
public interface LedgerEntryRepository {

    /**
     * Persist a new ledger entry with automatic sequence number assignment.
     *
     * <p>The repository assigns {@code sequenceNumber} based on the entry's
     * {@code subjectId} — any value set by the caller is overwritten. Sequence
     * numbers are monotonically increasing and contiguous on insert within
     * committed transactions. Retention deletion may remove entries from the
     * start of the sequence without affecting the contiguity invariant.
     *
     * @param entry the entry to persist; {@code subjectId} must not be {@code null}
     * @param tenancyId the tenant scope
     * @return the persisted entry with {@code sequenceNumber} assigned
     */
    LedgerEntry save(LedgerEntry entry, String tenancyId);

    /**
     * Return all ledger entries for the given subject in sequence order (ascending).
     *
     * @param subjectId the aggregate identifier
     * @param tenancyId the tenant scope
     * @return ordered list of entries; empty if none exist
     */
    List<LedgerEntry> findBySubjectId(UUID subjectId, String tenancyId);

    /**
     * Return all ledger entries for the given subject whose {@code occurredAt} falls
     * within [{@code from}, {@code to}] inclusive, ordered by {@code occurredAt} ascending.
     *
     * @param subjectId the aggregate identifier
     * @param from start of the time range (inclusive)
     * @param to end of the time range (inclusive)
     * @param tenancyId the tenant scope
     * @return ordered list; empty if none match
     */
    List<LedgerEntry> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId);

    /**
     * Return the most recent ledger entry for the given subject, or empty if none.
     *
     * @param subjectId the aggregate identifier
     * @param tenancyId the tenant scope
     * @return the latest entry, or empty if no entries exist
     */
    Optional<LedgerEntry> findLatestBySubjectId(UUID subjectId, String tenancyId);

    /**
     * Return a ledger entry by its primary key.
     *
     * @param id the ledger entry UUID primary key
     * @param tenancyId the tenant scope
     * @return the entry if found, or empty
     */
    Optional<LedgerEntry> findEntryById(UUID id, String tenancyId);

    /**
     * Return all attestations for the given ledger entry, ordered by occurrence time ascending.
     *
     * @param ledgerEntryId the ledger entry UUID
     * @param tenancyId the tenant scope
     * @return ordered list of attestations; empty if none exist
     */
    List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId, String tenancyId);

    /**
     * Persist a new attestation and return the saved instance.
     *
     * @param attestation the attestation to persist; must not be {@code null}
     * @param tenancyId the tenant scope
     * @return the persisted attestation
     */
    LedgerAttestation saveAttestation(LedgerAttestation attestation, String tenancyId);

    /**
     * Return all ledger entries for the given actor whose {@code occurredAt} falls
     * within [{@code from}, {@code to}] inclusive, ordered by {@code occurredAt} ascending.
     *
     * <p>
     * Provides the auditor-facing reconstructability required by EU AI Act Art.12 and
     * GDPR Art.22: "show everything actor X did between dates Y and Z."
     *
     * <p>
     * Uses {@link Instant} rather than {@code LocalDateTime} — {@code occurredAt} is stored
     * as {@code Instant} throughout the codebase; {@code LocalDateTime} would require implicit
     * timezone conversion, creating a silent correctness hazard for distributed systems.
     *
     * @param actorId the actor identity to filter by
     * @param from start of the time range (inclusive)
     * @param to end of the time range (inclusive)
     * @param tenancyId the tenant scope
     * @return ordered list of entries; empty if none match
     */
    List<LedgerEntry> findByActorId(String actorId, Instant from, Instant to, String tenancyId);

    /**
     * Return all ledger entries for the given actor role whose {@code occurredAt} falls
     * within [{@code from}, {@code to}] inclusive, ordered by {@code occurredAt} ascending.
     *
     * @param actorRole the functional role to filter by (e.g. {@code "Classifier"})
     * @param from start of the time range (inclusive)
     * @param to end of the time range (inclusive)
     * @param tenancyId the tenant scope
     * @return ordered list of entries; empty if none match
     */
    List<LedgerEntry> findByActorRole(String actorRole, Instant from, Instant to, String tenancyId);

    /**
     * Return all ledger entries causally triggered by the given entry,
     * ordered by {@code occurredAt} ascending. One hop only — recursive
     * traversal is the caller's responsibility.
     *
     * @param entryId the entry whose direct effects to retrieve
     * @param tenancyId the tenant scope
     * @return ordered list; empty if none
     */
    List<LedgerEntry> findCausedBy(UUID entryId, String tenancyId);

    /**
     * Return all attestations for the given ledger entry scoped to the given capability tag,
     * ordered by occurrence time ascending. Use {@link io.casehub.ledger.api.model.CapabilityTag#GLOBAL}
     * to query global attestations explicitly.
     *
     * @param entryId the ledger entry UUID
     * @param capabilityTag the capability tag to filter by; never {@code null}
     * @param tenancyId the tenant scope
     * @return ordered list; empty if none match
     */
    List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String capabilityTag, String tenancyId);

    /**
     * Return all global attestations for the given ledger entry (where
     * {@code capabilityTag = }{@link io.casehub.ledger.api.model.CapabilityTag#GLOBAL}),
     * ordered by occurrence time ascending.
     *
     * @param entryId the ledger entry UUID
     * @param tenancyId the tenant scope
     * @return ordered list; empty if none exist
     */
    List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId);

    /**
     * Return all attestations written by the given attestor for the given capability tag,
     * across all ledger entries, ordered by occurrence time ascending.
     *
     * <p>Used by B2 trust scoring to aggregate per-actor, per-capability attestation history.
     *
     * @param attestorId the attestor identity
     * @param capabilityTag the capability tag to filter by; never {@code null}
     * @param tenancyId the tenant scope
     * @return ordered list; empty if none match
     */
    List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String capabilityTag, String tenancyId);
}
