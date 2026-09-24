package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.ComplianceReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;


@ApplicationScoped
public class LedgerComplianceReportService {

    private final io.casehub.ledger.core.service.ComplianceReportServiceCore core;

    @Inject
    LedgerComplianceReportService(LedgerEntryRepository repo,
                                  io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository frontierRepo) {
        var verificationCore = new io.casehub.ledger.core.service.VerificationServiceCore(repo, frontierRepo);
        this.core = new io.casehub.ledger.core.service.ComplianceReportServiceCore(repo, verificationCore);
    }

    @Transactional
    public ComplianceReport reportForActor(String actorId, Instant from, Instant to, String tenancyId) {
        return core.reportForActor(actorId, from, to, tenancyId);
    }

    @Transactional
    public ComplianceReport reportForSubject(UUID subjectId, Instant from, Instant to, String tenancyId) {
        return core.reportForSubject(subjectId, from, to, tenancyId);
    }

    @Transactional
    public ComplianceReport reportForTenancy(String tenancyId, Instant from, Instant to) {
        return core.reportForTenancy(tenancyId, from, to);
    }
}
