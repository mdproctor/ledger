package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerTraceIdProvider;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;

public class TraceIdEnricherCore implements LedgerEntryEnricher {

    private final LedgerTraceIdProvider traceIdProvider;

    public TraceIdEnricherCore(LedgerTraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    @Override
    public void enrich(LedgerEntry entry) {
        if (entry.traceId != null) return;
        traceIdProvider.currentTraceId().ifPresent(id -> entry.traceId = id);
    }

    @Override
    public int priority() {
        return 10;
    }
}
