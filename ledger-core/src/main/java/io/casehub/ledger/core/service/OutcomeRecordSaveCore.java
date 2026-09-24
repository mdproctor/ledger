package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.config.MetadataProperties;
import io.casehub.ledger.core.model.AttestorDefaults;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class OutcomeRecordSaveCore {

    private final LedgerEntryRepository ledgerRepo;
    private final MetadataProperties metadataProperties;
    private final Function<OutcomeRecord, LedgerEntry> entryFactory;

    public OutcomeRecordSaveCore(LedgerEntryRepository ledgerRepo,
                                 MetadataProperties metadataProperties,
                                 Function<OutcomeRecord, LedgerEntry> entryFactory) {
        this.ledgerRepo = ledgerRepo;
        this.metadataProperties = metadataProperties;
        this.entryFactory = entryFactory;
    }

    public UUID save(OutcomeRecord record, AttestorDefaults attestor, String tenancyId) {
        validateMetadataSize(record.metadata());
        LedgerEntry entry = entryFactory.apply(record);
        ledgerRepo.save(entry, tenancyId);
        Objects.requireNonNull(entry.id,
                "LedgerEntryRepository.save() must assign entry.id before returning");

        LedgerAttestation attestation = buildAttestation(record, entry, attestor);
        ledgerRepo.saveAttestation(attestation, tenancyId);
        return entry.id;
    }

    public void saveAttestationOnly(UUID entryId, AttestationVerdict verdict,
                                    double confidence, String capabilityTag,
                                    AttestorDefaults attestor, String tenancyId) {
        LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "LedgerEntry " + entryId + " does not exist in tenancy " + tenancyId));

        LedgerAttestation a = new LedgerAttestation();
        a.ledgerEntryId = entry.id;
        a.subjectId = entry.subjectId;
        a.attestorId = attestor.attestorId();
        a.attestorType = attestor.attestorType();
        a.verdict = verdict;
        a.confidence = confidence;
        a.capabilityTag = capabilityTag;
        ledgerRepo.saveAttestation(a, tenancyId);
    }

    private void validateMetadataSize(String metadata) {
        if (metadata != null && metadata.length() > metadataProperties.maxSize()) {
            throw new IllegalArgumentException(
                    "metadata exceeds maximum size of " + metadataProperties.maxSize()
                            + " bytes — got " + metadata.length());
        }
    }

    private LedgerAttestation buildAttestation(OutcomeRecord record, LedgerEntry saved, AttestorDefaults attestor) {
        LedgerAttestation a = new LedgerAttestation();
        a.ledgerEntryId = saved.id;
        a.subjectId = saved.subjectId;
        a.attestorId = attestor.attestorId();
        a.attestorType = attestor.attestorType();
        a.verdict = record.verdict();
        a.confidence = record.confidence();
        a.capabilityTag = record.capabilityTag();
        a.occurredAt = record.occurredAt();
        return a;
    }
}
