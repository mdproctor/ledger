package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.enricher.EnricherPipelineCore;
import io.casehub.ledger.core.enricher.LedgerEntryEnricher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class LedgerEnricherPipeline {

    private final EnricherPipelineCore core;

    @Inject
    LedgerEnricherPipeline(@Any Instance<LedgerEntryEnricher> enrichers) {
        this.core = new EnricherPipelineCore(enrichers.stream().toList());
    }

    public void enrich(final LedgerEntry entry) {
        core.enrich(entry);
    }
}
