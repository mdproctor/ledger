package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OutcomeRecorderUnconfiguredAttestorIT.NoAttestorProfile.class)
class OutcomeRecorderUnconfiguredAttestorIT {

    public static class NoAttestorProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "outcome-no-attestor-test";
        }
    }

    @Inject OutcomeRecorder recorder;

    @Test
    void nullAttestorId_noConfigDefault_throwsIllegalState() {
        assertThatThrownBy(() ->
                recorder.record(OutcomeRecord.of("plugin", UUID.randomUUID(), "strategy", SOUND, 0.7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default-attestor-id is not configured");
    }
}
