package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerEntry;

public interface LedgerEntryEnricher {

    void enrich(LedgerEntry entry);

    default int priority() {
        return Integer.MAX_VALUE;
    }

}
