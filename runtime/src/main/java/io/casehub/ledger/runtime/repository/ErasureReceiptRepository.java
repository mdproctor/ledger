package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;

/**
 * SPI for querying {@link ErasureReceiptLedgerEntry} records.
 *
 * <p>Receipt entries are persisted via
 * {@link LedgerEntryRepository#save(io.casehub.ledger.runtime.model.LedgerEntry, String)},
 * which ensures Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 * This SPI covers read access only.
 */
public interface ErasureReceiptRepository {

    /**
     * All erasure receipts for the given raw actor identity within the given tenant,
     * ordered by {@code occurredAt} ascending.
     */
    List<ErasureReceiptLedgerEntry> findByErasedActorId(String erasedActorId, String tenancyId);
}
