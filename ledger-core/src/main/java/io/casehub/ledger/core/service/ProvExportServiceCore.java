package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.LedgerProvSerializer;

import java.util.List;
import java.util.UUID;

public class ProvExportServiceCore {

    private final LedgerEntryRepository ledgerRepo;

    public ProvExportServiceCore(LedgerEntryRepository ledgerRepo) {
        this.ledgerRepo = ledgerRepo;
    }

    public String exportSubject(UUID subjectId, String tenancyId) {
        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(subjectId, tenancyId);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No entries found for subject: " + subjectId);
        }
        return LedgerProvSerializer.toProvJsonLd(subjectId, entries);
    }
}
