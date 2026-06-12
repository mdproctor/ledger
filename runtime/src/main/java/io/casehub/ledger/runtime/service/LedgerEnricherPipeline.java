package io.casehub.ledger.runtime.service;

import java.util.Comparator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableInstance;

/**
 * CDI bean that runs the {@link LedgerEntryEnricher} pipeline on a {@link LedgerEntry}.
 *
 * <p>
 * Enrichers are CDI beans discovered via {@code @Inject @Any InjectableInstance<LedgerEntryEnricher>}
 * and invoked in ascending {@code @Priority} order (see {@link LedgerEntryEnricher}).
 * Each enricher runs in isolation — a thrown exception
 * is logged and swallowed; the pipeline always completes and the save is never blocked.
 *
 * <p>
 * Invoked by {@code LedgerEntryRepository.save()} implementations before digest computation
 * and agent signing. The full pipeline is: enrichment, digest (leafHash), agent signature, persist.
 */
@ApplicationScoped
public class LedgerEnricherPipeline {

    private static final Logger log = Logger.getLogger(LedgerEnricherPipeline.class);

    @Inject
    @Any
    InjectableInstance<LedgerEntryEnricher> enrichers;

    public void enrich(final LedgerEntry entry) {
        enrichers.handlesStream()
                .sorted(Comparator.comparingInt(h ->
                        (h.getBean() instanceof InjectableBean<?> ib) ? ib.getPriority() : Integer.MAX_VALUE))
                .map(h -> h.get())
                .forEach(e -> {
                    try {
                        e.enrich(entry);
                    } catch (final Exception ex) {
                        log.warnf("LedgerEntryEnricher %s failed — entry will still be saved: %s",
                                e.getClass().getSimpleName(), ex.getMessage());
                    }
                });
    }
}
