package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.GlobalScoreStrategy;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.core.trust.TrustScoreComputer;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.ledger.runtime.repository.TrustScoreSnapshotRepository;

import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes all trust score types for a single actor and persists the results.
 *
 * <p>Delegates pure computation to {@link TrustScoreCalculator}, then upserts results
 * into {@link ActorTrustScoreRepository}. Used by both {@link TrustScoreJob} (batch)
 * and {@link IncrementalTrustUpdateObserver} (per-attestation).
 */
@ApplicationScoped
class PerActorTrustComputer {

    private final TrustScoreCalculator calculator;
    private final ActorTrustScoreRepository trustRepo;
    private final TrustScoreSnapshotRepository snapshotRepo;

    @Inject
    PerActorTrustComputer(final TrustScoreCalculator calculator,
                          final ActorTrustScoreRepository trustRepo,
                          final TrustScoreSnapshotRepository snapshotRepo) {
        this.calculator   = calculator;
        this.trustRepo    = trustRepo;
        this.snapshotRepo = snapshotRepo;
    }

    PerActorTrustComputer(final DecayFunction decayFunction,
                          final ActorTrustScoreRepository trustRepo,
                          final TrustScoreSnapshotRepository snapshotRepo,
                          final GlobalScoreStrategy globalScoreStrategy,
                          final io.casehub.ledger.api.spi.AttestorCredibilityPolicy credibilityPolicy) {
        this.calculator = new TrustScoreCalculator(decayFunction, globalScoreStrategy, credibilityPolicy);
        this.trustRepo = trustRepo;
        this.snapshotRepo = snapshotRepo;
    }

    List<ActorTrustScore> computeForActor(final String actorId,
                                          final List<LedgerEntry> decisions,
                                          final Map<UUID, List<LedgerAttestation>> attestationsByEntry,
                                          final Instant now) {
        final ActorType actorType = decisions.stream()
                                             .map(e -> e.actorType)
                                             .filter(t -> t != null)
                                             .findFirst()
                                             .orElse(ActorType.HUMAN);

        final TrustScoreCalculator.ComputedScores computed =
                calculator.computeAll(decisions, attestationsByEntry, now);

        final List<ActorTrustScore> results = new ArrayList<>();

        // ── Persist capability scores ────────────────────────────────────────
        for (final Map.Entry<String, TrustScoreComputer.ActorScore> entry :
                computed.capabilityScores().entrySet()) {
            final TrustScoreComputer.ActorScore score = entry.getValue();
            final double previous = trustRepo.findCapabilityScore(actorId, entry.getKey())
                                             .map(s -> s.trustScore).orElse(0.0);
            trustRepo.upsert(actorId, ScoreType.CAPABILITY,
                             entry.getKey(), null, actorType, score.trustScore(),
                             score.decisionCount(), score.overturnedCount(),
                             score.alpha(), score.beta(),
                             score.attestationPositive(), score.attestationNegative(), now);
            snapshotRepo.save(new TrustScoreSnapshot(actorId, ScoreType.CAPABILITY,
                                                     entry.getKey(), null,
                                                     score.trustScore(), previous, now));
            results.add(buildScore(actorId, ScoreType.CAPABILITY,
                                   entry.getKey(), null, actorType, score, now));
        }

        // ── Persist dimension scores ─────────────────────────────────────────
        for (final Map.Entry<String, Double> entry : computed.dimensionScores().entrySet()) {
            final double previousDim = trustRepo.findDimensionScore(actorId, entry.getKey())
                                                .map(s -> s.trustScore).orElse(0.0);
            trustRepo.upsert(actorId, ScoreType.DIMENSION,
                             null, entry.getKey(), actorType, entry.getValue(),
                             0, 0, 0.0, 0.0, 0, 0, now);
            snapshotRepo.save(new TrustScoreSnapshot(actorId, ScoreType.DIMENSION,
                                                     null, entry.getKey(),
                                                     entry.getValue(), previousDim, now));
            results.add(buildDimensionScore(actorId, null, entry.getKey(),
                                            actorType, entry.getValue(), 0, 0, 0, now));
        }

        // ── Persist capability×dimension scores ──────────────────────────────
        for (final Map.Entry<String, Map<String, Double>> capEntry :
                computed.capabilityDimensionScores().entrySet()) {
            for (final Map.Entry<String, Double> dimEntry : capEntry.getValue().entrySet()) {
                final double previousCapDim = trustRepo.findCapabilityDimension(
                        actorId, capEntry.getKey(), dimEntry.getKey())
                        .map(s -> s.trustScore).orElse(0.0);
                trustRepo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION,
                                 capEntry.getKey(), dimEntry.getKey(), actorType, dimEntry.getValue(),
                                 0, 0, 0.0, 0.0, 0, 0, now);
                snapshotRepo.save(new TrustScoreSnapshot(actorId, ScoreType.CAPABILITY_DIMENSION,
                                                         capEntry.getKey(), dimEntry.getKey(),
                                                         dimEntry.getValue(), previousCapDim, now));
                results.add(buildDimensionScore(actorId, capEntry.getKey(), dimEntry.getKey(),
                                                actorType, dimEntry.getValue(), 0, 0, 0, now));
            }
        }

        // ── Persist global score ─────────────────────────────────────────────
        final TrustScoreComputer.ActorScore global = computed.globalScore();
        final double previousGlobal = trustRepo.findByActorId(actorId)
                                               .map(s -> s.trustScore).orElse(0.0);
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
                         actorType, global.trustScore(),
                         global.decisionCount(), global.overturnedCount(),
                         global.alpha(), global.beta(),
                         global.attestationPositive(), global.attestationNegative(), now);
        snapshotRepo.save(new TrustScoreSnapshot(actorId, ScoreType.GLOBAL,
                                                 null, null,
                                                 global.trustScore(), previousGlobal, now));
        results.add(buildScore(actorId, ScoreType.GLOBAL,
                               null, null, actorType, global, now));

        return results;
    }

    private static ActorTrustScore buildScore(final String actorId,
            final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType,
            final TrustScoreComputer.ActorScore score, final Instant now) {
        final ActorTrustScore s = new ActorTrustScore();
        s.actorId = actorId;
        s.scoreType = scoreType;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score.trustScore();
        s.decisionCount = score.decisionCount();
        s.overturnedCount = score.overturnedCount();
        s.alpha = score.alpha();
        s.beta = score.beta();
        s.attestationPositive = score.attestationPositive();
        s.attestationNegative = score.attestationNegative();
        s.lastComputedAt = now;
        return s;
    }

    private static ActorTrustScore buildDimensionScore(final String actorId,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType,
            final double score, final int decisionCount,
            final int positive, final int negative, final Instant now) {
        final ActorTrustScore s = new ActorTrustScore();
        s.actorId = actorId;
        s.scoreType = capabilityKey != null
                ? ScoreType.CAPABILITY_DIMENSION
                : ScoreType.DIMENSION;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score;
        s.decisionCount = decisionCount;
        s.overturnedCount = 0;
        s.alpha = 0.0;
        s.beta = 0.0;
        s.attestationPositive = positive;
        s.attestationNegative = negative;
        s.lastComputedAt = now;
        return s;
    }
}
