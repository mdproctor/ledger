package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.TrustScoreComputer;

/**
 * Pure JUnit 5 unit test verifying that {@code confidence} scales the Bayesian Beta
 * weight contribution proportionally. No Quarkus runtime, no CDI.
 *
 * <p>TrustScoreComputer initialises α = β = 1.0 (Jeffreys prior). After one SOUND
 * attestation at confidence C with zero age (weight = 1.0), stored alpha = 1.0 + C.
 * The ratio (A.alpha − 1.0) / (B.alpha − 1.0) equals the confidence ratio.
 */
class TrustScoreComputerConfidenceTest {

    private static final int HALF_LIFE_DAYS = 90;
    private final TrustScoreComputer computer = new TrustScoreComputer(HALF_LIFE_DAYS);
    private final Instant now = Instant.now();

    // Concrete subclass for testing (LedgerEntry is abstract)
    private static class EventEntry extends LedgerEntry {
    }

    private EventEntry decision(final String actorId) {
        final EventEntry e = new EventEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = now;
        return e;
    }

    private LedgerAttestation attestation(final UUID entryId,
            final AttestationVerdict verdict, final double confidence) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.subjectId = UUID.randomUUID();
        a.attestorId = "test-attestor";
        a.attestorType = ActorType.SYSTEM;
        a.verdict = verdict;
        a.confidence = confidence;
        a.capabilityTag = "strategy";
        a.occurredAt = now;  // zero age → decay weight = 1.0
        return a;
    }

    @Test
    void gameConfidence_contributes7xMoreThanTickConfidence() {
        // Actor A: game-level (confidence = 0.7)
        final EventEntry decisionA = decision("actor-A");
        final LedgerAttestation attA = attestation(decisionA.id, AttestationVerdict.SOUND, 0.7);
        final TrustScoreComputer.ActorScore scoreA = computer.compute(
                List.of(decisionA), Map.of(decisionA.id, List.of(attA)), now);

        // Actor B: tick-level (confidence = 0.1)
        final EventEntry decisionB = decision("actor-B");
        final LedgerAttestation attB = attestation(decisionB.id, AttestationVerdict.SOUND, 0.1);
        final TrustScoreComputer.ActorScore scoreB = computer.compute(
                List.of(decisionB), Map.of(decisionB.id, List.of(attB)), now);

        // Prior is 1.0 for both (TrustScoreComputer initialises α = β = 1.0).
        // Subtract prior to isolate the attestation contribution.
        final double contributionA = scoreA.alpha() - 1.0;
        final double contributionB = scoreB.alpha() - 1.0;

        assertThat(contributionA).isCloseTo(0.7, within(0.001));
        assertThat(contributionB).isCloseTo(0.1, within(0.001));
        assertThat(contributionA / contributionB).isCloseTo(7.0, within(0.01));
    }

    @Test
    void highConfidence_yieldsHigherScore() {
        final EventEntry d = decision("actor");
        final LedgerAttestation highConf = attestation(d.id, AttestationVerdict.SOUND, 1.0);
        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(highConf)), now);
        assertThat(score.trustScore()).isGreaterThan(0.5);
    }
}
