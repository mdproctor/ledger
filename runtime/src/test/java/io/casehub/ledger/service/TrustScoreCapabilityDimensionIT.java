package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * End-to-end integration tests for CAPABILITY_DIMENSION composite trust scoring (#76).
 */
@QuarkusTest
@TestProfile(TrustScoreIT.TrustScoreTestProfile.class)
class TrustScoreCapabilityDimensionIT {

    @Inject TrustScoreJob trustScoreJob;
    @Inject TrustGateService trustGateService;
    @Inject LedgerEntryRepository repo;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject EntityManager em;

    // ── Happy path: composite attestation produces CAPABILITY_DIMENSION row ──────

    @Test
    @Transactional
    void compositeAttestation_producesCapabilityDimensionRow() {
        final String actorId = "agent-cd-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.92);

        trustScoreJob.runComputation();

        final var row = trustRepo.findCapabilityDimension(actorId, "security-review", "thoroughness");
        assertThat(row).isPresent();
        assertThat(row.get().scoreType).isEqualTo(ScoreType.CAPABILITY_DIMENSION);
        assertThat(row.get().capabilityKey).isEqualTo("security-review");
        assertThat(row.get().dimensionKey).isEqualTo("thoroughness");
        assertThat(row.get().trustScore).isCloseTo(0.92, within(0.05));
    }

    // ── Happy path: multiple capability+dimension combinations ────────────────

    @Test
    @Transactional
    void multipleComposites_produceSeparateRows() {
        final String actorId = "agent-cd-multi-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.9);
        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "false-positive-rate", 0.1);
        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "architecture-review", "thoroughness", 0.6);

        trustScoreJob.runComputation();

        final List<ActorTrustScore> rows =
                trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION);
        assertThat(rows).hasSize(3);
    }

    // ── Isolation: CAPABILITY and DIMENSION rows are unaffected ──────────────

    @Test
    @Transactional
    void compositePass_doesNotAffectCapabilityOrDimensionRows() {
        final String actorId = "agent-cd-iso-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, now.minus(1, ChronoUnit.DAYS).plusSeconds(60),
                "security-review", repo, em);

        LedgerTestFixtures.seedDecisionWithDimension(actorId, now.minus(1, ChronoUnit.DAYS),
                "thoroughness", 0.8, CapabilityTag.GLOBAL, repo, em);

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.9);

        trustScoreJob.runComputation();

        assertThat(trustRepo.findCapabilityScore(actorId, "security-review")).isPresent();
        assertThat(trustRepo.findDimensionScore(actorId, "thoroughness")).isPresent();
        assertThat(trustRepo.findCapabilityDimension(actorId, "security-review", "thoroughness")).isPresent();
    }

    // ── Isolation: only-capability attestation produces no CAPABILITY_DIMENSION row

    @Test
    @Transactional
    void capabilityOnlyAttestation_producesNoCapabilityDimensionRow() {
        final String actorId = "agent-cd-caponly-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, now.minus(1, ChronoUnit.DAYS).plusSeconds(60),
                "security-review", repo, em);

        trustScoreJob.runComputation();

        assertThat(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION))
                .isEmpty();
    }

    // ── Isolation: GLOBAL capability tag does not produce CAPABILITY_DIMENSION row

    @Test
    @Transactional
    void globalCapabilityTag_producesNoDimensionRow() {
        final String actorId = "agent-cd-global-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecisionWithDimension(actorId, now.minus(1, ChronoUnit.DAYS),
                "thoroughness", 0.8, CapabilityTag.GLOBAL, repo, em);

        trustScoreJob.runComputation();

        assertThat(trustRepo.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY_DIMENSION))
                .isEmpty();
        assertThat(trustRepo.findDimensionScore(actorId, "thoroughness")).isPresent();
    }

    // ── Decay: older attestation has lower weight ─────────────────────────────

    @Test
    @Transactional
    void compositeScore_decays_olderAttestationHasLessWeight() {
        final String actorId = "agent-cd-decay-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 1.0);
        seedComposite(actorId, now.minus(365, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.0);

        trustScoreJob.runComputation();

        final var score = trustGateService.qualityScore(actorId, "security-review", "thoroughness");
        assertThat(score).isPresent();
        assertThat(score.get()).isGreaterThan(0.5);
    }

    // ── TrustGateService integration ─────────────────────────────────────────

    @Test
    @Transactional
    void trustGateService_qualityScore_returnsComputedScore() {
        final String actorId = "agent-cd-gate-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.88);

        trustScoreJob.runComputation();

        assertThat(trustGateService.qualityScore(actorId, "security-review", "thoroughness"))
                .isPresent();
        assertThat(trustGateService.qualityScore(actorId, "security-review", "thoroughness").get())
                .isCloseTo(0.88, within(0.05));
    }

    @Test
    @Transactional
    void trustGateService_qualityScores_returnsAllDimensionsForCapability() {
        final String actorId = "agent-cd-bulk-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.9);
        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "false-positive-rate", 0.1);
        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "architecture-review", "thoroughness", 0.5);

        trustScoreJob.runComputation();

        final Map<String, Double> secScores =
                trustGateService.qualityScores(actorId, "security-review");
        assertThat(secScores).containsOnlyKeys("thoroughness", "false-positive-rate");
        assertThat(secScores.get("thoroughness")).isCloseTo(0.9, within(0.05));

        final Map<String, Double> archScores =
                trustGateService.qualityScores(actorId, "architecture-review");
        assertThat(archScores).containsOnlyKeys("thoroughness");
    }

    @Test
    @Transactional
    void trustGateService_meetsQualityThreshold_trueWhenScoreSufficient() {
        final String actorId = "agent-cd-thresh-" + UUID.randomUUID();
        final Instant now = Instant.now();

        seedComposite(actorId, now.minus(1, ChronoUnit.DAYS),
                "security-review", "thoroughness", 0.9);

        trustScoreJob.runComputation();

        assertThat(trustGateService.meetsQualityThreshold(
                actorId, "security-review", "thoroughness", 0.75)).isTrue();
        assertThat(trustGateService.meetsQualityThreshold(
                actorId, "security-review", "thoroughness", 0.95)).isFalse();
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private void seedComposite(final String actorId, final Instant decisionTime,
            final String capabilityTag, final String dimension, final double score) {
        LedgerTestFixtures.seedDecisionWithDimension(
                actorId, decisionTime, dimension, score, capabilityTag, repo, em);
    }
}
