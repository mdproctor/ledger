package io.casehub.ledger.core.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ComplianceSummaryTest {

    @Test
    void fromDecisions_countsCorrectly() {
        final List<DecisionRecord> decisions = List.of(
                decision("PlainLedgerEntry", "alg-v1", true),
                decision("PlainLedgerEntry", null, false),
                decision("WorkItemLedgerEntry", "alg-v2", true));

        final ComplianceSummary summary = ComplianceSummary.fromDecisions(decisions);

        assertThat(summary.totalDecisions()).isEqualTo(3);
        assertThat(summary.aiAssistedDecisions()).isEqualTo(2);
        assertThat(summary.humanOverrideCount()).isEqualTo(2);
        assertThat(summary.decisionsByType()).containsEntry("PlainLedgerEntry", 2);
        assertThat(summary.decisionsByType()).containsEntry("WorkItemLedgerEntry", 1);
    }

    @Test
    void fromDecisions_empty_allZero() {
        final ComplianceSummary summary = ComplianceSummary.fromDecisions(List.of());

        assertThat(summary.totalDecisions()).isEqualTo(0);
        assertThat(summary.aiAssistedDecisions()).isEqualTo(0);
        assertThat(summary.humanOverrideCount()).isEqualTo(0);
        assertThat(summary.decisionsByType()).isEmpty();
    }

    @Test
    void fromDecisions_nullAlgorithmRef_notCountedAsAiAssisted() {
        final List<DecisionRecord> decisions = List.of(
                decision("PlainLedgerEntry", null, true));

        final ComplianceSummary summary = ComplianceSummary.fromDecisions(decisions);

        assertThat(summary.aiAssistedDecisions()).isEqualTo(0);
        assertThat(summary.humanOverrideCount()).isEqualTo(1);
    }

    private static DecisionRecord decision(final String entryType,
            final String algorithmRef, final boolean humanOverride) {
        return new DecisionRecord(UUID.randomUUID(), entryType, Instant.now(),
                "actor-1", algorithmRef, algorithmRef != null ? 0.9 : null,
                null, null, humanOverride, null, null);
    }
}
