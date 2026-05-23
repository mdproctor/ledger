package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * CDI bean that runs the {@link LedgerEntryEnricher} pipeline on a {@link LedgerEntry}.
 *
 * <p>
 * Enrichers are CDI beans discovered via {@code @Inject @Any Instance<LedgerEntryEnricher>}
 * and invoked in an unspecified order. Each enricher runs in isolation — a thrown exception
 * is logged and swallowed; the pipeline always completes and the save is never blocked.
 *
 * <p>
 * Invoked by {@link LedgerTraceListener} at JPA {@code @PrePersist} time, and directly
 * by in-memory {@code LedgerEntryRepository} implementations before storing an entry.
 */
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
