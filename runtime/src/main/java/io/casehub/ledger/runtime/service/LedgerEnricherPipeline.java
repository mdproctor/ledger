package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;

@ApplicationScoped
public class LedgerEnricherPipeline {

    private static final Logger log = Logger.getLogger(LedgerEnricherPipeline.class);

    @Inject
    @Any
    Instance<LedgerEntryEnricher> enrichers;

    public void enrich(final LedgerEntry entry) {
        for (final LedgerEntryEnricher enricher : enrichers) {
            try {
                enricher.enrich(entry);
            } catch (final Exception ex) {
                log.warnf("LedgerEntryEnricher %s failed — entry will still be saved: %s",
                        enricher.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }
}
