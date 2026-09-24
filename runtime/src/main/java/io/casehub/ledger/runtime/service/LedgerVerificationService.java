package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.merkle.InclusionProof;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class LedgerVerificationService {

    private final io.casehub.ledger.core.service.VerificationServiceCore core;

    @Inject
    LedgerVerificationService(LedgerEntryRepository ledgerRepo, LedgerMerkleFrontierRepository frontierRepo) {
        this.core = new io.casehub.ledger.core.service.VerificationServiceCore(ledgerRepo, frontierRepo);
    }

    @Transactional
    public String treeRoot(UUID subjectId, String tenancyId) {
        return core.treeRoot(subjectId, tenancyId);
    }

    @Transactional
    public InclusionProof inclusionProof(UUID entryId, String tenancyId) {
        return core.inclusionProof(entryId, tenancyId);
    }

    @Transactional
    public boolean verify(UUID subjectId, String tenancyId) {
        return core.verify(subjectId, tenancyId);
    }
}
