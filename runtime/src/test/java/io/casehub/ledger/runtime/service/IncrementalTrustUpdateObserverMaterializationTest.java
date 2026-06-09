package io.casehub.ledger.runtime.service;

import io.casehub.ledger.runtime.config.LedgerConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncrementalTrustUpdateObserverMaterializationTest {

    @Test
    void onAttestationRecorded_skipsWhenMaterializationDisabled() {
        final IncrementalTrustUpdateObserver observer = new IncrementalTrustUpdateObserver();
        final LedgerConfig config = mock(LedgerConfig.class, RETURNS_DEEP_STUBS);
        when(config.trustScore().enabled()).thenReturn(true);
        when(config.trustScore().incremental().enabled()).thenReturn(true);
        when(config.trustScore().materialization().enabled()).thenReturn(false);
        observer.config = config;

        final AttestationRecordedEvent event =
                new AttestationRecordedEvent("alice", UUID.randomUUID(), UUID.randomUUID());

        assertDoesNotThrow(() -> observer.onAttestationRecorded(event));
    }
}
