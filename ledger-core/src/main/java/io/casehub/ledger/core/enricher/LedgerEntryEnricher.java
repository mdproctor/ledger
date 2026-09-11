package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerEntry;

public interface LedgerEntryEnricher {

    void enrich(LedgerEntry entry);
}
