package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.AuditEntry;
import io.casehub.ledger.core.compliance.AuditTrailExport;
import io.casehub.ledger.core.compliance.SubjectAuditTrail;
import io.casehub.ledger.core.compliance.VerificationSummary;

@ApplicationScoped
public class AuditTrailExportService {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    LedgerVerificationService verificationService;

    @Inject
    LedgerProvExportService provExportService;

    @Transactional
    public AuditTrailExport generateForTenancy(
            final String tenancyId, final Instant from, final Instant to) {
        final List<UUID> subjectIds = repo.findDistinctSubjectIds(tenancyId);
        final List<SubjectAuditTrail> trails = subjectIds.stream()
                .map(sid -> buildSubjectTrail(sid, from, to, tenancyId))
                .toList();
        final VerificationSummary verification = VerificationSummary.fromTrails(trails);
        return new AuditTrailExport(tenancyId, from, to, Instant.now(), trails, verification);
    }

    private SubjectAuditTrail buildSubjectTrail(
            final UUID subjectId, final Instant from, final Instant to,
            final String tenancyId) {
        final List<LedgerEntry> entries =
                repo.findBySubjectIdAndTimeRange(subjectId, from, to, tenancyId);
        final List<AuditEntry> auditEntries = entries.stream()
                .map(this::toAuditEntry)
                .toList();
        boolean chainValid;
        String merkleRoot;
        try {
            chainValid = verificationService.verify(subjectId, tenancyId);
            merkleRoot = verificationService.treeRoot(subjectId, tenancyId);
        } catch (final Exception e) {
            chainValid = false;
            merkleRoot = null;
        }
        String provJsonLd;
        try {
            provJsonLd = provExportService.exportSubject(subjectId, tenancyId);
        } catch (final Exception e) {
            provJsonLd = null;
        }
        return new SubjectAuditTrail(subjectId, auditEntries, merkleRoot, chainValid, provJsonLd);
    }

    private AuditEntry toAuditEntry(final LedgerEntry entry) {
        return new AuditEntry(
                entry.id,
                entry.getClass().getSimpleName(),
                entry.occurredAt,
                entry.actorId,
                entry.digest,
                entry.sequenceNumber);
    }
}
