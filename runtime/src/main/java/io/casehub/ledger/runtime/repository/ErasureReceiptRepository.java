package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.jpa.ErasureReceiptLedgerEntry;

/**
 * SPI for querying {@link ErasureReceiptLedgerEntry} records.
 *
 * <p>Receipt entries are persisted via
 * {@link io.casehub.ledger.api.spi.LedgerEntryRepository#save(io.casehub.ledger.api.model.LedgerEntry, String)},
 * which ensures Merkle chain inclusion, pseudonymisation, and enricher pipeline execution.
 * This SPI covers read access only.
 */
public interface ErasureReceiptRepository {

    /**
     * All erasure receipts for the given raw actor identity within the given tenant,
     * ordered by {@code occurredAt} ascending.
     */
    List<ErasureReceiptLedgerEntry> findByErasedActorId(String erasedActorId, String tenancyId);

    /**
     * Total number of erasure receipts within the given tenant.
     */
    long countByTenant(String tenancyId);
}
