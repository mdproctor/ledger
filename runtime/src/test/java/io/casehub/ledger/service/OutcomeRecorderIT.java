package io.casehub.ledger.service;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;
import io.casehub.ledger.api.model.AttestationVerdict;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        final var entries = ledgerRepo.findBySubjectId(gameId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo(pluginId);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);

        final var attestations = ledgerRepo.findAttestationsByEntryId(entries.get(0).id, DEFAULT_TENANT_ID);
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

    @Test
    void record_metadataFlowsToPersistedEntry() {
        final String pluginId = "test-agent-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(pluginId, subjectId, "routing", SOUND, 0.8)
                .withMetadata("{\"rationale\":\"highest score\",\"candidates\":[\"a\",\"b\"]}"));

        final var entries = ledgerRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).metadata)
                .isEqualTo("{\"rationale\":\"highest score\",\"candidates\":[\"a\",\"b\"]}");
    }

    @Test
    void record_nullMetadata_persistsNull() {
        final String pluginId = "test-agent-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(pluginId, subjectId, "strategy", SOUND, 0.7));

        final var entries = ledgerRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).metadata).isNull();
    }

    @Test
    void record_metadataWithinLimit_persists() {
        final String pluginId = "test-agent-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();
        final String metadata = "{\"k\":\"" + "x".repeat(100) + "\"}";

        recorder.record(OutcomeRecord.of(pluginId, subjectId, "strategy", SOUND, 0.7)
                .withMetadata(metadata));

        final var entries = ledgerRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).metadata).isEqualTo(metadata);
    }

    @Test
    void record_metadataExceedingLimit_throwsIAE() {
        final String pluginId = "test-agent-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();
        final String oversized = "x".repeat(65537);

        assertThatThrownBy(() -> recorder.record(
                OutcomeRecord.of(pluginId, subjectId, "strategy", SOUND, 0.7)
                        .withMetadata(oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata exceeds maximum size");
    }

    @Test
    void record_returnsEntryId() {
        final String pluginId  = "test-agent-" + UUID.randomUUID();
        final UUID   subjectId = UUID.randomUUID();

        final UUID entryId = recorder.record(OutcomeRecord.of(pluginId, subjectId, "strategy", SOUND, 0.7));

        assertThat(entryId).isNotNull();
        final var entries = ledgerRepo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id).isEqualTo(entryId);
    }

    @Test
    void addAttestation_appendsToExistingEntry() {
        final String pluginId  = "test-agent-" + UUID.randomUUID();
        final UUID   subjectId = UUID.randomUUID();

        final UUID entryId = recorder.record(OutcomeRecord.of(pluginId, subjectId, "strategy", SOUND, 0.7));

        recorder.addAttestation(entryId, AttestationVerdict.ENDORSED, 1.0, "strategy");

        final var attestations = ledgerRepo.findAttestationsByEntryId(entryId, DEFAULT_TENANT_ID);
        assertThat(attestations).hasSize(2);
        assertThat(attestations.get(1).verdict).isEqualTo(AttestationVerdict.ENDORSED);
        assertThat(attestations.get(1).confidence).isEqualTo(1.0);
        assertThat(attestations.get(1).capabilityTag).isEqualTo("strategy");
        assertThat(attestations.get(1).subjectId).isEqualTo(subjectId);
    }

    @Test
    void addAttestation_nonexistentEntry_throwsIAE() {
        final UUID fakeId = UUID.randomUUID();

        assertThatThrownBy(() -> recorder.addAttestation(fakeId, SOUND, 0.7, "strategy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }


}
