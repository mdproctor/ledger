package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OutcomeRecorderIT.Profile.class)
class OutcomeRecorderIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "outcome-recorder-test";
        }
    }

    @Inject OutcomeRecorder recorder;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject TrustScoreJob trustScoreJob;

    @Test
    void record_writesEntryAndAttestation_thenScoreComputedAfterJob() {
        final String pluginId = "quarkmind:strategy@v1-" + UUID.randomUUID();
        final UUID gameId = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(pluginId, gameId, "strategy", SOUND, 0.7));

        final var entries = ledgerRepo.findBySubjectId(gameId);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo(pluginId);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);

        final var attestations = ledgerRepo.findAttestationsByEntryId(entries.get(0).id);
        assertThat(attestations).hasSize(1);
        final var att = attestations.get(0);
        assertThat(att.verdict).isEqualTo(SOUND);
        assertThat(att.confidence).isEqualTo(0.7);
        assertThat(att.capabilityTag).isEqualTo("strategy");
        assertThat(att.attestorId).isEqualTo("quarkmind:game-engine@v1");

        trustScoreJob.runComputation();

        final var score = trustRepo.findCapabilityScore(pluginId, "strategy");
        assertThat(score).isPresent();
        assertThat(score.get().trustScore).isGreaterThan(0.5);
    }
}
