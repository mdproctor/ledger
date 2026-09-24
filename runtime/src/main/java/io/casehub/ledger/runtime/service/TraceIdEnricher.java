package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerTraceIdProvider;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(10)
public class TraceIdEnricher implements LedgerEntryEnricher {

    private final io.casehub.ledger.core.service.TraceIdEnricherCore core;

    @Inject
    public TraceIdEnricher(LedgerTraceIdProvider traceIdProvider) {
        this.core = new io.casehub.ledger.core.service.TraceIdEnricherCore(traceIdProvider);
    }

    @Override
    public void enrich(LedgerEntry entry) {
        core.enrich(entry);
    }

    @Override
    public int priority() {
        return core.priority();
    }
}
