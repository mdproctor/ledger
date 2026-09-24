package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.api.model.supplement.ProvenanceSupplement;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.ComplianceReport;
import io.casehub.ledger.core.compliance.DecisionRecord;

import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

public class ComplianceReportServiceCore {

    private final LedgerEntryRepository repo;
    private final VerificationServiceCore verificationService;

    public ComplianceReportServiceCore(LedgerEntryRepository repo,
                                       VerificationServiceCore verificationService) {
        this.repo = repo;
        this.verificationService = verificationService;
    }

    public ComplianceReport reportForActor(String actorId, Instant from, Instant to, String tenancyId) {
        List<LedgerEntry> entries = repo.findByActorId(actorId, from, to, tenancyId);
        List<DecisionRecord> decisions = entries.stream()
                .filter(e -> e.compliance().isPresent())
                .map(this::toDecisionRecord)
                .toList();
        String merkleRoot = buildActorMerkleRoot(entries, tenancyId);
        return new ComplianceReport(actorId, null, from, to, decisions.size(), decisions, merkleRoot);
    }

    public ComplianceReport reportForSubject(UUID subjectId, Instant from, Instant to, String tenancyId) {
        List<LedgerEntry> entries = repo.findBySubjectIdAndTimeRange(subjectId, from, to, tenancyId);
        List<DecisionRecord> decisions = entries.stream()
                .filter(e -> e.compliance().isPresent())
                .map(this::toDecisionRecord)
                .toList();
        String merkleRoot = resolveSubjectMerkleRoot(subjectId, tenancyId);
        return new ComplianceReport(null, subjectId, from, to, decisions.size(), decisions, merkleRoot);
    }

    private DecisionRecord toDecisionRecord(LedgerEntry entry) {
        ComplianceSupplement cs = entry.compliance().orElseThrow();
        ProvenanceSupplement ps = entry.provenance().orElse(null);
        return new DecisionRecord(
                entry.id, entry.occurredAt,
                cs.algorithmRef, cs.confidenceScore,
                cs.contestationUri, cs.humanOverrideAvailable,
                ps != null ? ps.sourceEntityType : null,
                ps != null ? ps.sourceEntityId : null);
    }

    private String buildActorMerkleRoot(List<LedgerEntry> entries, String tenancyId) {
        List<UUID> subjectIds = entries.stream()
                .map(e -> e.subjectId).filter(id -> id != null).distinct().toList();
        if (subjectIds.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner(";");
        for (UUID subjectId : subjectIds) {
            String root = resolveSubjectMerkleRoot(subjectId, tenancyId);
            if (root != null) joiner.add(subjectId + "=" + root);
        }
        return joiner.length() > 0 ? joiner.toString() : null;
    }

    private String resolveSubjectMerkleRoot(UUID subjectId, String tenancyId) {
        try {
            return verificationService.treeRoot(subjectId, tenancyId);
        } catch (Exception e) {
            return null;
        }
    }
}
