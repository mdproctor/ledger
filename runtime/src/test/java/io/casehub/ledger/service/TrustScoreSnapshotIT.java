package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustScoreSnapshotIT.Profile.class)
class TrustScoreSnapshotIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "trust-score-test";
        }
    }

    @Inject
    TrustScoreJob trustScoreJob;

    @Inject
    LedgerEntryRepository repo;

    @Inject
    TrustScoreSnapshotRepository snapshotRepo;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void firstComputation_capturesSnapshotWithZeroPreviousScore() {
        final String actorId = "snapshot-first-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, repo, em);

        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> snapshots = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).actorId).isEqualTo(actorId);
        assertThat(snapshots.get(0).scoreType).isEqualTo(ScoreType.GLOBAL);
        assertThat(snapshots.get(0).score).isGreaterThan(0.5);
        assertThat(snapshots.get(0).previousScore).isEqualTo(0.0);
        assertThat(snapshots.get(0).capabilityTag).isNull();
    }

    @Test
    @Transactional
    void secondComputation_capturesPreviousScore() {
        final String actorId = "snapshot-prev-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(2, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, repo, em);
        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> first = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(first).hasSize(1);
        final double firstScore = first.get(0).score;

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.ENDORSED, repo, em);
        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> all = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).occurredAt).isAfterOrEqualTo(all.get(1).occurredAt);
        assertThat(all.get(0).previousScore).isEqualTo(firstScore);
    }

    @Test
    @Transactional
    void capabilitySnapshot_capturedSeparately() {
        final String actorId = "snapshot-cap-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, now.minus(1, ChronoUnit.DAYS),
                "code-review", repo, em);

        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> capSnapshots =
                snapshotRepo.findCapabilitySnapshots(actorId, "code-review");
        assertThat(capSnapshots).isNotEmpty();
        assertThat(capSnapshots.get(0).scoreType).isEqualTo(ScoreType.CAPABILITY);
        assertThat(capSnapshots.get(0).capabilityTag).isEqualTo("code-review");
        assertThat(capSnapshots.get(0).score).isGreaterThan(0.0);
    }

    @Test
    @Transactional
    void dimensionSnapshot_capturedSeparately() {
        final String actorId = "snapshot-dim-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecisionWithDimension(actorId, now.minus(1, ChronoUnit.DAYS),
                "review-thoroughness", 0.85, "code-review", repo, em);

        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> dimSnapshots =
                snapshotRepo.findDimensionSnapshots(actorId, "review-thoroughness");
        assertThat(dimSnapshots).isNotEmpty();
        assertThat(dimSnapshots.get(0).scoreType).isEqualTo(ScoreType.DIMENSION);
        assertThat(dimSnapshots.get(0).dimensionKey).isEqualTo("review-thoroughness");
        assertThat(dimSnapshots.get(0).score).isGreaterThan(0.0);
    }

    @Test
    @Transactional
    void findByActorAndTimeRange_returnsSnapshotsWithinWindow() {
        final String actorId = "snapshot-range-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(2, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, repo, em);
        trustScoreJob.runComputation();

        final List<TrustScoreSnapshotBase> inRange = snapshotRepo.findByActorAndTimeRange(
                actorId, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        assertThat(inRange).isNotEmpty();
        assertThat(inRange).allSatisfy(s -> {
            assertThat(s.occurredAt).isAfterOrEqualTo(now.minus(1, ChronoUnit.HOURS));
            assertThat(s.occurredAt).isBeforeOrEqualTo(now.plus(1, ChronoUnit.HOURS));
        });

        final List<TrustScoreSnapshotBase> outOfRange = snapshotRepo.findByActorAndTimeRange(
                actorId, now.minus(5, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS));
        assertThat(outOfRange).isEmpty();
    }

    @Test
    @Transactional
    void retention_deletesSnapshotsOlderThanCutoff() {
        final String actorId = "snapshot-retention-" + UUID.randomUUID();
        final Instant now = Instant.now();

        snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.GLOBAL,
                null, null, 0.7, 0.5, now.minus(400, ChronoUnit.DAYS)));
        snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.GLOBAL,
                null, null, 0.8, 0.7, now.minus(100, ChronoUnit.DAYS)));

        final int deleted = snapshotRepo.deleteOlderThan(now.minus(365, ChronoUnit.DAYS));
        assertThat(deleted).isEqualTo(1);

        final List<TrustScoreSnapshotBase> remaining = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).score).isEqualTo(0.8);
    }
}
