package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.core.config.OutcomeProperties;
import io.casehub.ledger.core.model.AttestorDefaults;
import io.casehub.platform.api.identity.ActorType;

import java.util.UUID;
import java.util.function.Supplier;

public class OutcomeRecorderCore {

    private final OutcomeRecordSaveCore saveCore;
    private final OutcomeProperties outcomeProperties;
    private final Supplier<String> tenancyIdSupplier;

    public OutcomeRecorderCore(OutcomeRecordSaveCore saveCore,
                               OutcomeProperties outcomeProperties,
                               Supplier<String> tenancyIdSupplier) {
        this.saveCore = saveCore;
        this.outcomeProperties = outcomeProperties;
        this.tenancyIdSupplier = tenancyIdSupplier;
    }

    public UUID record(OutcomeRecord record) {
        return record(record, tenancyIdSupplier.get());
    }

    public UUID record(OutcomeRecord record, String tenancyId) {
        AttestorDefaults attestor = resolveAttestor(record);
        return saveCore.save(record, attestor, tenancyId);
    }

    public void addAttestation(UUID entryId, AttestationVerdict verdict,
                               double confidence, String capabilityTag) {
        AttestorDefaults attestor = resolveDefaultAttestor();
        saveCore.saveAttestationOnly(entryId, verdict, confidence, capabilityTag,
                attestor, tenancyIdSupplier.get());
    }

    private AttestorDefaults resolveAttestor(OutcomeRecord record) {
        if (record.attestorId() != null) {
            return new AttestorDefaults(record.attestorId(), record.attestorType());
        }
        return resolveDefaultAttestor();
    }

    private AttestorDefaults resolveDefaultAttestor() {
        String id = outcomeProperties.defaultAttestorId().orElseThrow(() ->
                new IllegalStateException(
                        "OutcomeRecord.attestorId is null and "
                                + "casehub.ledger.outcome.default-attestor-id is not configured."));
        ActorType type = outcomeProperties.defaultAttestorType();
        return new AttestorDefaults(id, type);
    }
}
