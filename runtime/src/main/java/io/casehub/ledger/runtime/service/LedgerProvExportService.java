package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class LedgerProvExportService {

    private final io.casehub.ledger.core.service.ProvExportServiceCore core;

    @Inject
    LedgerProvExportService(LedgerEntryRepository ledgerRepo) {
        this.core = new io.casehub.ledger.core.service.ProvExportServiceCore(ledgerRepo);
    }

    @Transactional
    public String exportSubject(UUID subjectId, String tenancyId) {
        return core.exportSubject(subjectId, tenancyId);
    }
}
