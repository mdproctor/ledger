package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.quarkus.arc.DefaultBean;

/**
 * No-op {@link LedgerEntryRepository} that satisfies the CDI injection point when neither
 * the JPA implementation ({@code JpaLedgerEntryRepository}) nor an in-memory alternative
 * ({@code InMemoryLedgerEntryRepository}) is active.
 *
 * <p>
 * Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaLedgerEntryRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryLedgerEntryRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 *
 * <p>
 * This bean exists solely to satisfy the CDI injection point in deployments where ledger
 * persistence should be inactive (e.g. engine test modules that run without a datasource).
 * Use a JPA or in-memory implementation in any context where entries must actually be
 * persisted.
 */
@DefaultBean
@ApplicationScoped
public class NoOpLedgerEntryRepository implements LedgerEntryRepository {

    /**
     * No-op save — returns the entry unchanged. Does NOT:
     * assign {@code sequenceNumber}, set {@code tenancyId}, compute digest, run the enricher
     * pipeline, or call {@code EntityManager.persist()}. This bean exists solely to satisfy
     * the CDI injection point in deployments where the {@link LedgerEntryRepository} SPI
     * should be inactive. Use a JPA or in-memory implementation for any context where entries
     * must actually be persisted.
     */
    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        return entry;
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(
            final UUID subjectId, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(
            final UUID ledgerEntryId, final String tenancyId) {
        return List.of();
    }

    /**
     * No-op save — returns the attestation unchanged. Does NOT call
     * {@code EntityManager.persist()}. See class-level Javadoc.
     */
    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        return attestation;
    }

    @Override
    public List<LedgerEntry> findByActorId(
            final String actorId, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findByActorRole(
            final String actorRole, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(
            final UUID entryId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        return List.of();
    }
}
