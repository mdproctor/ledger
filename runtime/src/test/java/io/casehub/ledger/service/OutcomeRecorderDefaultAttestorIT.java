package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OutcomeRecorderIT.Profile.class)
class OutcomeRecorderDefaultAttestorIT {

    @Inject OutcomeRecorder recorder;
    @Inject LedgerEntryRepository ledgerRepo;

    @Test
    void nullAttestorId_usesConfigDefault() {
        final UUID gameId = UUID.randomUUID();
        recorder.record(OutcomeRecord.of("quarkmind:strategy@v1", gameId, "strategy", SOUND, 0.7));

        final var entry = ledgerRepo.findBySubjectId(gameId).get(0);
        final var attestations = ledgerRepo.findAttestationsByEntryId(entry.id);
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).attestorId).isEqualTo("quarkmind:game-engine@v1");
    }

    @Test
    void explicitAttestorId_overridesConfigDefault() {
        final UUID gameId = UUID.randomUUID();
        recorder.record(
                OutcomeRecord.of("quarkmind:strategy@v1", gameId, "strategy", SOUND, 0.7)
                        .withAttestor("custom-attestor@v1", ActorType.SYSTEM));

        final var entry = ledgerRepo.findBySubjectId(gameId).get(0);
        final var attestations = ledgerRepo.findAttestationsByEntryId(entry.id);
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).attestorId).isEqualTo("custom-attestor@v1");
    }
}
