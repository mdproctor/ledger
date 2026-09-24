package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.config.MetadataProperties;

import java.util.UUID;
import java.util.function.Function;

public class LedgerAppenderCore {

    private final LedgerEntryRepository repo;
    private final MetadataProperties metadataProperties;
    private final Function<AuditRecord, LedgerEntry> entryFactory;

    public LedgerAppenderCore(LedgerEntryRepository repo,
                              MetadataProperties metadataProperties,
                              Function<AuditRecord, LedgerEntry> entryFactory) {
        this.repo = repo;
        this.metadataProperties = metadataProperties;
        this.entryFactory = entryFactory;
    }

    public UUID append(AuditRecord record, String tenancyId) {
        if (record.metadata() != null && record.metadata().length() > metadataProperties.maxSize()) {
            throw new IllegalArgumentException(
                    "metadata exceeds maximum size of " + metadataProperties.maxSize()
                            + " bytes — got " + record.metadata().length());
        }
        LedgerEntry entry = entryFactory.apply(record);
        LedgerEntry saved = repo.save(entry, tenancyId);
        return saved.id;
    }
}
