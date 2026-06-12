package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * JPA entity listener that guards against direct {@code em.persist()} calls on
 * {@link LedgerEntry}. All entries must go through {@code LedgerEntryRepository.save()},
 * which handles the full pipeline: enrichment, hashing, and signing.
 *
 * <p>When the hash chain is enabled and {@code digest} is null at {@code @PrePersist}
 * time, the entry was not processed by the save pipeline — this is a programming error.
 */
@ApplicationScoped
public class LedgerTraceListener {

    @Inject
    LedgerConfig ledgerConfig;

    @PrePersist
    public void prePersist(final Object entity) {
        if (!(entity instanceof LedgerEntry entry)) {
            return;
        }
        if (ledgerConfig.hashChain().enabled() && entry.digest == null) {
            throw new IllegalStateException(
                    "LedgerEntry must be persisted through LedgerEntryRepository, "
                    + "which handles sequence allocation, enrichment, hashing, and signing. "
                    + "Direct em.persist() bypasses the entire save pipeline.");
        }
    }
}
