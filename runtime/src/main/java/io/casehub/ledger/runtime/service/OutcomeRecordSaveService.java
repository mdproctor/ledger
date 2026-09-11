package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.model.AttestorDefaults;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Inner service for {@link DefaultOutcomeRecorder}.
 *
 * <p>Package-private: not part of the public API. Delegates to
 * {@link LedgerEntryRepository#save} ({@code @Transactional(REQUIRED)}) and
 * {@link LedgerEntryRepository#saveAttestation} ({@code @Transactional(REQUIRES_NEW)}).
 * Each write commits independently — attestation failures do not roll back the entry.
 *
 * <p>Quarkus ArC applies the {@code @Transactional} interceptor to package-private methods
 * via bytecode enhancement — no proxy required.
 */
@ApplicationScoped
class OutcomeRecordSaveService {

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    LedgerConfig config;

    UUID save(final OutcomeRecord record, final AttestorDefaults attestor, final String tenancyId) {
        validateMetadataSize(record.metadata());
        final LedgerEntry entry = buildEntry(record);
        ledgerRepo.save(entry, tenancyId);
        java.util.Objects.requireNonNull(entry.id,
                                         "LedgerEntryRepository.save() must assign entry.id before returning — "
                                         + "custom implementations must honour this contract");

        final LedgerAttestation attestation = buildAttestation(record, entry, attestor);
        ledgerRepo.saveAttestation(attestation, tenancyId);
        return entry.id;
    }

    void saveAttestationOnly(final UUID entryId, final AttestationVerdict verdict,
                             final double confidence, final String capabilityTag,
                             final AttestorDefaults attestor, final String tenancyId) {
        final LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                                            .orElseThrow(() -> new IllegalArgumentException(
                                                    "LedgerEntry " + entryId + " does not exist in tenancy " + tenancyId));

        final LedgerAttestation a = new LedgerAttestation();
        a.ledgerEntryId = entry.id;
        a.subjectId     = entry.subjectId;
        a.attestorId    = attestor.attestorId();
        a.attestorType  = attestor.attestorType();
        a.verdict       = verdict;
        a.confidence    = confidence;
        a.capabilityTag = capabilityTag;
        a.occurredAt    = null;
        ledgerRepo.saveAttestation(a, tenancyId);
    }


    private void validateMetadataSize(final String metadata) {
        if (metadata != null && metadata.length() > config.metadata().maxSize()) {
            throw new IllegalArgumentException(
                    "metadata exceeds maximum size of " + config.metadata().maxSize()
                    + " bytes — got " + metadata.length());
        }
    }

    private PlainLedgerEntry buildEntry(final OutcomeRecord record) {
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.actorId    = record.actorId();
        entry.actorRole  = record.actorRole();
        entry.actorType  = record.actorType();
        entry.subjectId  = record.subjectId();
        entry.entryType  = record.entryType();
        entry.occurredAt = record.occurredAt();
        entry.metadata   = record.metadata();
        return entry;}

    private LedgerAttestation buildAttestation(final OutcomeRecord record,
                                               final LedgerEntry saved, final AttestorDefaults attestor) {
        final LedgerAttestation a = new LedgerAttestation();
        a.ledgerEntryId = saved.id;
        a.subjectId     = saved.subjectId;
        a.attestorId    = attestor.attestorId();
        a.attestorType  = attestor.attestorType();
        a.verdict       = record.verdict();
        a.confidence    = record.confidence();
        a.capabilityTag = record.capabilityTag();
        a.occurredAt    = record.occurredAt();
        return a;
    }
}
