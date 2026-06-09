package io.casehub.ledger.runtime.service;

import io.casehub.ledger.runtime.config.LedgerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustScoreJobMaterializationTest {

    @Test
    void computeTrustScores_skipsWhenMaterializationDisabled() {
        final TrustScoreJob job = new TrustScoreJob();
        final LedgerConfig config = mock(LedgerConfig.class, RETURNS_DEEP_STUBS);
        when(config.trustScore().enabled()).thenReturn(true);
        when(config.trustScore().materialization().enabled()).thenReturn(false);
        job.config = config;

        assertDoesNotThrow(job::computeTrustScores);
    }

    @Test
    void computeTrustScores_proceedsWhenMaterializationEnabled() {
        final TrustScoreJob job = new TrustScoreJob();
        final LedgerConfig config = mock(LedgerConfig.class, RETURNS_DEEP_STUBS);
        when(config.trustScore().enabled()).thenReturn(true);
        when(config.trustScore().materialization().enabled()).thenReturn(true);
        job.config = config;

        assertThrows(NullPointerException.class, job::computeTrustScores);
    }
}
