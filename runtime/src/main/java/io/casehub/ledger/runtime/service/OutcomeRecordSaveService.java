package io.casehub.ledger.runtime.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;

/**
 * Transactional inner service for {@link DefaultOutcomeRecorder}.
 *
 * <p>Package-private: not part of the public API. Exists solely to provide a clean
 * {@code @Transactional} boundary that commits before {@code DefaultOutcomeRecorder.record()}
 * returns — preventing race conditions if a future async trust update observer fires before
 * the writes are visible. See casehubio/ledger#115.
 *
 * <p>Quarkus ArC applies the {@code @Transactional} interceptor to package-private methods
 * via bytecode enhancement — no proxy required.
 */
@ApplicationScoped
class OutcomeRecordSaveService {

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Transactional
    void save(final OutcomeRecord record, final AttestorDefaults attestor) {
        final LedgerEntry entry = buildEntry(record);
        ledgerRepo.save(entry);  // repo assigns sequenceNumber, UUID, occurredAt, enriches

        final LedgerAttestation attestation = buildAttestation(record, entry, attestor);
        ledgerRepo.saveAttestation(attestation);
    }

    private PlainLedgerEntry buildEntry(final OutcomeRecord record) {
        final PlainLedgerEntry entry = new PlainLedgerEntry();
        entry.actorId    = record.actorId();
        entry.actorRole  = record.actorRole();
        entry.actorType  = record.actorType();
        entry.subjectId  = record.subjectId();
        entry.entryType  = LedgerEntryType.EVENT;
        entry.occurredAt = record.occurredAt();  // null → @PrePersist fills at persist time
        return entry;
    }

    private LedgerAttestation buildAttestation(final OutcomeRecord record,
            final LedgerEntry saved, final AttestorDefaults attestor) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id             = UUID.randomUUID();
        a.ledgerEntryId  = saved.id;
        a.subjectId      = saved.subjectId;
        a.attestorId     = attestor.attestorId();
        a.attestorType   = attestor.attestorType();
        a.verdict        = record.verdict();
        a.confidence     = record.confidence();
        a.capabilityTag  = record.capabilityTag();
        a.occurredAt     = record.occurredAt();  // null → LedgerAttestation @PrePersist fills it
        return a;
    }
}
