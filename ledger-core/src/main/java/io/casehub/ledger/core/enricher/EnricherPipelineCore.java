package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerEntry;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnricherPipelineCore {

    private static final Logger log = Logger.getLogger(EnricherPipelineCore.class.getName());

    private final List<LedgerEntryEnricher> enrichers;

    public EnricherPipelineCore(List<LedgerEntryEnricher> enrichers) {
        this.enrichers = enrichers.stream()
                .sorted(Comparator.comparingInt(LedgerEntryEnricher::priority))
                .toList();
    }

    public void enrich(LedgerEntry entry) {
        for (var enricher : enrichers) {
            try {
                enricher.enrich(entry);
            } catch (Exception ex) {
                log.log(Level.WARNING, "Enricher {0} failed — entry will still be saved: {1}",
                        new Object[]{enricher.getClass().getSimpleName(), ex.getMessage()});
            }
        }
    }
}
