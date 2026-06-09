package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.smallrye.mutiny.Uni;

/**
 * Reactive SPI for persisting and querying {@link LedgerEntry} records within a single tenant.
 *
 * <p>
 * Method signatures mirror {@link LedgerEntryRepository} with all return types wrapped in
 * {@link Uni}. Consumers building reactive Quarkus services should inject this interface
 * rather than {@link LedgerEntryRepository}.
 *
 * <p>
 * All methods accept a {@code tenancyId} as the final parameter, enforcing tenant-scoped
 * queries and writes. For cross-tenant queries, see {@link CrossTenantReactiveLedgerEntryRepository}.
 *
 * <p>
 * The default implementation {@code ReactiveJpaLedgerEntryRepository} is annotated
 * {@code @Alternative} — consumers activate it via {@code quarkus.arc.selected-alternatives}
 * or provide their own implementation.
 */
public interface ReactiveLedgerEntryRepository {

    /**
     * Persist a new ledger entry with automatic sequence number assignment.
     *
     * <p>The repository assigns {@code sequenceNumber} based on the entry's
     * {@code subjectId} — any value set by the caller is overwritten. Sequence
     * numbers are monotonically increasing and contiguous on insert within
     * committed transactions.
     *
     * @param entry the entry to persist; {@code subjectId} must not be {@code null}
     * @param tenancyId the tenant scope for this entry
     * @return the persisted entry with {@code sequenceNumber} assigned
     */
    Uni<LedgerEntry> save(LedgerEntry entry, String tenancyId);

    Uni<List<LedgerEntry>> findBySubjectId(UUID subjectId, String tenancyId);

    Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId);

    Uni<Optional<LedgerEntry>> findLatestBySubjectId(UUID subjectId, String tenancyId);

    Uni<Optional<LedgerEntry>> findEntryById(UUID id, String tenancyId);

    Uni<List<LedgerEntry>> findByActorId(String actorId, Instant from, Instant to, String tenancyId);

    Uni<List<LedgerEntry>> findByActorRole(String actorRole, Instant from, Instant to, String tenancyId);

    Uni<List<LedgerEntry>> findCausedBy(UUID entryId, String tenancyId);

    Uni<LedgerAttestation> saveAttestation(LedgerAttestation attestation, String tenancyId);

    Uni<List<LedgerAttestation>> findAttestationsByEntryId(UUID ledgerEntryId, String tenancyId);

    Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String capabilityTag, String tenancyId);

    Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId);

    Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String capabilityTag, String tenancyId);
}
