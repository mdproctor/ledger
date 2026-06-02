package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;
import static io.casehub.ledger.api.model.AttestationVerdict.FLAGGED;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OutcomeRecorderIT.Profile.class)
class MultiCapabilityIT {

    @Inject OutcomeRecorder recorder;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject TrustScoreJob trustScoreJob;

    @Test
    void fourPlugins_distinctCapabilityScores_strategyBeatsEconomics() {
        final String strategy  = "quarkmind:strategy@v1-"  + UUID.randomUUID();
        final String economics = "quarkmind:economics@v1-" + UUID.randomUUID();
        final String tactics   = "quarkmind:tactics@v1-"   + UUID.randomUUID();
        final String scouting  = "quarkmind:scouting@v1-"  + UUID.randomUUID();

        // Strategy: 9 SOUND, 1 FLAGGED
        for (int i = 0; i < 9; i++) {
            recorder.record(OutcomeRecord.of(strategy, UUID.randomUUID(), "strategy", SOUND, 0.7));
        }
        recorder.record(OutcomeRecord.of(strategy, UUID.randomUUID(), "strategy", FLAGGED, 0.7));

        // Economics: 3 SOUND, 7 FLAGGED
        for (int i = 0; i < 3; i++) {
            recorder.record(OutcomeRecord.of(economics, UUID.randomUUID(), "economics", SOUND, 0.7));
        }
        for (int i = 0; i < 7; i++) {
            recorder.record(OutcomeRecord.of(economics, UUID.randomUUID(), "economics", FLAGGED, 0.7));
        }

        // Tactics: 5 SOUND, 5 FLAGGED
        for (int i = 0; i < 5; i++) {
            recorder.record(OutcomeRecord.of(tactics, UUID.randomUUID(), "tactics", SOUND, 0.7));
            recorder.record(OutcomeRecord.of(tactics, UUID.randomUUID(), "tactics", FLAGGED, 0.7));
        }

        // Scouting: 7 SOUND, 3 FLAGGED
        for (int i = 0; i < 7; i++) {
            recorder.record(OutcomeRecord.of(scouting, UUID.randomUUID(), "scouting", SOUND, 0.7));
        }
        for (int i = 0; i < 3; i++) {
            recorder.record(OutcomeRecord.of(scouting, UUID.randomUUID(), "scouting", FLAGGED, 0.7));
        }

        trustScoreJob.runComputation();

        final double strategyScore  = trustRepo.findCapabilityScore(strategy,  "strategy").orElseThrow().trustScore;
        final double economicsScore = trustRepo.findCapabilityScore(economics, "economics").orElseThrow().trustScore;
        final double tacticsScore   = trustRepo.findCapabilityScore(tactics,   "tactics").orElseThrow().trustScore;
        final double scoutingScore  = trustRepo.findCapabilityScore(scouting,  "scouting").orElseThrow().trustScore;

        assertThat(strategyScore).isGreaterThan(economicsScore);
        assertThat(scoutingScore).isGreaterThan(economicsScore);
        assertThat(tacticsScore).isGreaterThan(0.0);

        // Score assertions above (orElseThrow) already verify all four CAPABILITY rows exist.
    }
}
