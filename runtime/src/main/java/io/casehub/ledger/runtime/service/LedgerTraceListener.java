package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;

import io.casehub.ledger.runtime.model.LedgerEntry;

@ApplicationScoped
public class LedgerTraceListener {

    @Inject
    LedgerEnricherPipeline enricherPipeline;

    @PrePersist
    public void prePersist(final Object entity) {
        if (!(entity instanceof LedgerEntry entry)) {
            return;
        }
        enricherPipeline.enrich(entry);
    }
}
