package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.ReactiveOutcomeRecorder;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OutcomeRecorderIT.Profile.class)
class ReactiveOutcomeRecorderIT {

    @Inject ReactiveOutcomeRecorder reactiveRecorder;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject TrustScoreJob trustScoreJob;

    @Test
    void reactiveRecord_completesAfterBothWritesCommit() {
        final String pluginId = "quarkmind:economics@v1-" + UUID.randomUUID();
        final UUID gameId = UUID.randomUUID();

        reactiveRecorder.record(
                OutcomeRecord.of(pluginId, gameId, "economics", SOUND, 0.7)
        ).await().indefinitely();

        final var entries = ledgerRepo.findBySubjectId(gameId);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo(pluginId);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);

        final var attestations = ledgerRepo.findAttestationsByEntryId(entries.get(0).id);
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).capabilityTag).isEqualTo("economics");
        assertThat(attestations.get(0).confidence).isEqualTo(0.7);

        trustScoreJob.runComputation();

        final var score = trustRepo.findCapabilityScore(pluginId, "economics");
        assertThat(score).isPresent();
        assertThat(score.get().trustScore).isGreaterThan(0.5);
    }
}
