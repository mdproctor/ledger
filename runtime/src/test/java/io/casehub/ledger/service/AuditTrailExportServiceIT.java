package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.AuditTrailExport;
import io.casehub.ledger.runtime.service.AuditTrailExportService;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AuditTrailExportServiceIT {

    @Inject AuditTrailExportService exportService;
    @Inject LedgerEntryRepository repo;

    @Test
    @Transactional
    void generateForTenancy_twoSubjects_bothIncluded() {
        final UUID subject1 = UUID.randomUUID();
        final UUID subject2 = UUID.randomUUID();
        final String tenancyId = "audit-two-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(subject1, "actor-1"), tenancyId);
        repo.save(entry(subject2, "actor-2"), tenancyId);

        final AuditTrailExport export = exportService.generateForTenancy(tenancyId, from, to);

        assertThat(export.tenancyId()).isEqualTo(tenancyId);
        assertThat(export.from()).isEqualTo(from);
        assertThat(export.to()).isEqualTo(to);
        assertThat(export.subjects()).hasSize(2);
        assertThat(export.verification().totalSubjects()).isEqualTo(2);
        assertThat(export.verification().totalEntries()).isEqualTo(2);
    }

    @Test
    @Transactional
    void generateForTenancy_emptyTenancy_emptyResult() {
        final String tenancyId = "audit-empty-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        final AuditTrailExport export = exportService.generateForTenancy(tenancyId, from, to);

        assertThat(export.subjects()).isEmpty();
        assertThat(export.verification().totalSubjects()).isEqualTo(0);
        assertThat(export.verification().totalEntries()).isEqualTo(0);
    }

    @Test
    @Transactional
    void generateForTenancy_verificationPerSubject() {
        final UUID subject = UUID.randomUUID();
        final String tenancyId = "audit-verify-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(subject, "actor-v"), tenancyId);

        final AuditTrailExport export = exportService.generateForTenancy(tenancyId, from, to);

        assertThat(export.subjects()).hasSize(1);
        assertThat(export.subjects().get(0).subjectId()).isEqualTo(subject);
        assertThat(export.subjects().get(0).merkleRoot()).isNotNull();
        assertThat(export.subjects().get(0).chainValid()).isTrue();
        assertThat(export.subjects().get(0).entries()).hasSize(1);
    }

    @Test
    @Transactional
    void generateForTenancy_isolatedByTenancy() {
        final UUID subject = UUID.randomUUID();
        final String tenantA = "audit-isoA-" + UUID.randomUUID();
        final String tenantB = "audit-isoB-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(subject, "actor-a"), tenantA);
        repo.save(entry(subject, "actor-b"), tenantB);

        final AuditTrailExport exportA = exportService.generateForTenancy(tenantA, from, to);

        assertThat(exportA.subjects()).hasSize(1);
        assertThat(exportA.subjects().get(0).entries()).allMatch(e -> "actor-a".equals(e.actorId()));
    }

    private static TestEntry entry(final UUID subjectId, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "AuditExporter";
        e.occurredAt = Instant.now();
        return e;
    }
}
