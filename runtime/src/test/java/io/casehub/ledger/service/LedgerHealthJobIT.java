package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerAnomalyDetected;
import io.casehub.ledger.runtime.service.LedgerHealthJob;
import io.casehub.ledger.runtime.service.LedgerReconciliationMismatchDetected;
import io.casehub.ledger.runtime.service.LedgerReconciliationSource;
import io.casehub.ledger.runtime.service.LedgerSequenceGapDetected;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for {@link LedgerHealthJob} — sequence gap detection and reconciliation.
 */
@QuarkusTest
@TestProfile(LedgerHealthJobIT.HealthTestProfile.class)
class LedgerHealthJobIT {

    public static class HealthTestProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "health-test";
        }
    }

    /**
     * Captures CDI events fired by the health job.
     * Uses a static list so field access from the test bypasses the CDI proxy — same
     * pattern as CountingEnricher.count in LedgerEnricherPipelineIT.
     */
    @ApplicationScoped
    static class AnomalyEventCapture {
        static final List<LedgerAnomalyDetected> EVENTS = new ArrayList<>();

        void onAnomaly(@Observes final LedgerAnomalyDetected event) {
            EVENTS.add(event);
        }
    }

    /**
     * Test reconciliation source with static state for the same proxy-bypass reason.
     */
    @ApplicationScoped
    static class TestReconciliationSource implements LedgerReconciliationSource {
        static long domainCount = 0;
        static long ledgerCount = 0;
        static boolean active = false;

        @Override
        public String subjectType() {
            return "TestEntity";
        }

        @Override
        public long countDomainEntities() {
            return domainCount;
        }

        @Override
        public long countLedgerEntries() {
            return ledgerCount;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    @Inject LedgerHealthJob healthJob;
    @Inject LedgerEntryRepository repo;
    @Inject EntityManager em;

    @BeforeEach
    void reset() {
        AnomalyEventCapture.EVENTS.clear();
        TestReconciliationSource.active = false;
        TestReconciliationSource.domainCount = 0;
        TestReconciliationSource.ledgerCount = 0;
    }

    // ── Happy path: contiguous sequences — no event ───────────────────────────

    @Test
    @Transactional
    void contiguousSequences_noGapEvent() {
        final UUID subjectId = UUID.randomUUID();
        newEntry(subjectId);
        newEntry(subjectId);
        newEntry(subjectId);

        healthJob.run();

        final List<LedgerSequenceGapDetected> gaps = AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerSequenceGapDetected.class::isInstance)
                .map(LedgerSequenceGapDetected.class::cast)
                .filter(e -> subjectId.equals(e.subjectId()))
                .toList();
        assertThat(gaps).isEmpty();
    }

    // ── Correctness: sequence gap detected ────────────────────────────────────

    @Test
    @Transactional
    void sequenceGap_eventFired() {
        final UUID subjectId = UUID.randomUUID();
        final TestEntry e1 = newEntry(subjectId);
        final TestEntry e2 = newEntry(subjectId);
        final TestEntry e3 = newEntry(subjectId);

        // Create gap: change e2's sequence to 4 via native SQL
        em.createNativeQuery("UPDATE ledger_entry SET sequence_number = ?1 WHERE id = ?2")
                .setParameter(1, 4)
                .setParameter(2, e2.id)
                .executeUpdate();

        healthJob.run();

        final List<LedgerSequenceGapDetected> gaps = AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerSequenceGapDetected.class::isInstance)
                .map(LedgerSequenceGapDetected.class::cast)
                .filter(e -> subjectId.equals(e.subjectId()))
                .toList();
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).expectedCount()).isEqualTo(4); // max-min+1 = 4-1+1 = 4
        assertThat(gaps.get(0).actualCount()).isEqualTo(3);
        assertThat(gaps.get(0).tenancyId()).isEqualTo(DEFAULT_TENANT_ID);
    }

    // ── Correctness: only gapped subject fires event ───────────────────────────

    @Test
    @Transactional
    void multipleSubjects_onlyGappedFires() {
        final UUID cleanSubjectId = UUID.randomUUID();
        newEntry(cleanSubjectId);
        newEntry(cleanSubjectId);

        final UUID gappedSubjectId = UUID.randomUUID();
        final TestEntry g1 = newEntry(gappedSubjectId);
        final TestEntry g2 = newEntry(gappedSubjectId);

        em.createNativeQuery("UPDATE ledger_entry SET sequence_number = ?1 WHERE id = ?2")
                .setParameter(1, 3)
                .setParameter(2, g2.id)
                .executeUpdate();

        healthJob.run();

        final List<LedgerSequenceGapDetected> gapEvents = AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerSequenceGapDetected.class::isInstance)
                .map(LedgerSequenceGapDetected.class::cast)
                .toList();

        assertThat(gapEvents).anyMatch(e -> gappedSubjectId.equals(e.subjectId()));
        assertThat(gapEvents).noneMatch(e -> cleanSubjectId.equals(e.subjectId()));
    }

    // ── Robustness: single entry per subject — no gap possible ───────────────

    @Test
    @Transactional
    void singleEntry_noGapEvent() {
        final UUID subjectId = UUID.randomUUID();
        newEntry(subjectId);

        healthJob.run();

        assertThat(AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerSequenceGapDetected.class::isInstance)
                .map(LedgerSequenceGapDetected.class::cast)
                .filter(e -> subjectId.equals(e.subjectId()))
                .toList()).isEmpty();
    }

    // ── Robustness: reconciliation mismatch fires event ───────────────────────

    @Test
    void reconciliationMismatch_eventFired() {
        TestReconciliationSource.active = true;
        TestReconciliationSource.domainCount = 5;
        TestReconciliationSource.ledgerCount = 3;

        healthJob.run();

        final List<LedgerReconciliationMismatchDetected> reconciliationEvents =
                AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerReconciliationMismatchDetected.class::isInstance)
                .map(LedgerReconciliationMismatchDetected.class::cast)
                .toList();
        assertThat(reconciliationEvents).hasSize(1);
        assertThat(reconciliationEvents.get(0).domainCount()).isEqualTo(5);
        assertThat(reconciliationEvents.get(0).ledgerCount()).isEqualTo(3);
    }

    // ── Robustness: inactive reconciliation source — no event ─────────────────

    @Test
    void inactiveReconciliationSource_noEvent() {
        TestReconciliationSource.active = false;
        TestReconciliationSource.domainCount = 10;
        TestReconciliationSource.ledgerCount = 0;

        healthJob.run();

        assertThat(AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerReconciliationMismatchDetected.class::isInstance)
                .toList()).isEmpty();
    }

    // ── Robustness: matching reconciliation counts — no event ─────────────────

    @Test
    void reconciliationMatch_noEvent() {
        TestReconciliationSource.active = true;
        TestReconciliationSource.domainCount = 4;
        TestReconciliationSource.ledgerCount = 4;

        healthJob.run();

        assertThat(AnomalyEventCapture.EVENTS.stream()
                .filter(LedgerReconciliationMismatchDetected.class::isInstance)
                .toList()).isEmpty();
    }

    // ── fixture ───────────────────────────────────────────────────────────────

    private TestEntry newEntry(final UUID subjectId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "health-actor";
        e.actorType = ActorType.AGENT;
        e.actorRole = "HealthTester";
        e.occurredAt = Instant.now();
        return (TestEntry) repo.save(e, DEFAULT_TENANT_ID);
    }
}
