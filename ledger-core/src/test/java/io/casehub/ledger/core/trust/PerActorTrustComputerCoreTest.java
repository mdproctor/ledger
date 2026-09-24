package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PerActorTrustComputerCoreTest {

    @Test
    void computeForActorPersistsGlobalScore() {
        var trustRepo = mock(ActorTrustScoreRepository.class);
        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);

        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy());

        var computer = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);

        var entry = stubDecision("actor-1");
        var now = Instant.now();

        List<ActorTrustScoreBase> results = computer.computeForActor(
                "actor-1", List.of(entry), Map.of(), now);

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(s -> s.scoreType == ScoreType.GLOBAL);

        verify(trustRepo).upsert(eq("actor-1"), eq(ScoreType.GLOBAL),
                any(), any(), eq(ActorType.AGENT), any(Double.class),
                any(Integer.class), any(Integer.class), any(Double.class), any(Double.class),
                any(Integer.class), any(Integer.class), any(Instant.class));

        verify(snapshotRepo).save(any(TrustScoreSnapshotBase.class));
    }

    @Test
    void returnsScoresWithCorrectActorId() {
        var trustRepo = mock(ActorTrustScoreRepository.class);
        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);
        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy());

        var computer = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);
        var results = computer.computeForActor(
                "test-actor", List.of(stubDecision("test-actor")), Map.of(), Instant.now());

        assertThat(results).allSatisfy(s ->
                assertThat(s.actorId).isEqualTo("test-actor"));
    }

    @Test
    void handlesAttestationsInComputation() {
        var trustRepo    = mock(ActorTrustScoreRepository.class);
        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);
        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy());

        var computer = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);

        var entry       = stubDecision("actor-1");
        var attestation = new LedgerAttestation();
        attestation.id            = UUID.randomUUID();
        attestation.ledgerEntryId = entry.id;
        attestation.attestorId    = "attestor-1";
        attestation.verdict       = io.casehub.ledger.api.model.AttestationVerdict.ENDORSED;
        attestation.confidence    = 1.0;
        attestation.occurredAt    = Instant.now();

        var results = computer.computeForActor(
                "actor-1", List.of(entry),
                Map.of(entry.id, List.of(attestation)), Instant.now());

        assertThat(results).isNotEmpty();
        var globalScore = results.stream()
                                 .filter(s -> s.scoreType == ScoreType.GLOBAL)
                                 .findFirst().orElseThrow();
        assertThat(globalScore.trustScore).isGreaterThan(0.0);
    }

    private static LedgerEntry stubDecision(String actorId) {
        return new LedgerEntry() {{
            this.id             = UUID.randomUUID();
            this.subjectId      = UUID.randomUUID();
            this.sequenceNumber = 1;
            this.entryType      = LedgerEntryType.EVENT;
            this.actorId        = actorId;
            this.actorType      = ActorType.AGENT;
            this.occurredAt     = Instant.now();
        }};
    }
}
