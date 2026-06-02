package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.runtime.config.LedgerConfig;

/**
 * Default blocking implementation of {@link OutcomeRecorder}.
 *
 * <p>{@code @DefaultBean} — replaced by any {@code @ApplicationScoped} bean implementing
 * {@link OutcomeRecorder} that the consuming application provides.
 *
 * <p>Transaction boundary: this method is NOT {@code @Transactional}. It delegates to
 * {@link OutcomeRecordSaveService#save} which IS {@code @Transactional} and commits before
 * returning. This ensures that for JPA consumers both writes are committed and visible before
 * this method returns. For in-memory consumers {@code @Transactional} is a no-op.
 */
@DefaultBean
@ApplicationScoped
public class DefaultOutcomeRecorder implements OutcomeRecorder {

    @Inject
    OutcomeRecordSaveService saveService;

    @Inject
    LedgerConfig config;

    @Override
    public void record(final OutcomeRecord record) {
        final AttestorDefaults attestor = resolveAttestor(record);
        saveService.save(record, attestor);
    }

    private AttestorDefaults resolveAttestor(final OutcomeRecord record) {
        if (record.attestorId() != null) {
            return new AttestorDefaults(record.attestorId(), record.attestorType());
        }
        final String id = config.outcome().defaultAttestorId().orElseThrow(() ->
                new IllegalStateException(
                        "OutcomeRecord.attestorId is null and "
                                + "casehub.ledger.outcome.default-attestor-id is not configured. "
                                + "Set one of them before calling record()."));
        final ActorType type = config.outcome().defaultAttestorType();
        return new AttestorDefaults(id, type);
    }
}
