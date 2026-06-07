package io.casehub.ledger.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;

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

    @Inject
    PerActorTrustComputer(final TrustScoreCalculator calculator,
                          final ActorTrustScoreRepository trustRepo) {
        this.calculator = calculator;
        this.trustRepo = trustRepo;
    }

    PerActorTrustComputer(final DecayFunction decayFunction,
                          final ActorTrustScoreRepository trustRepo,
                          final GlobalScoreStrategy globalScoreStrategy,
                          final AttestationAggregator attestationAggregator,
                          final AttestationAggregator.Strategy aggregationStrategy) {
        this.calculator = new TrustScoreCalculator(
                decayFunction, attestationAggregator, globalScoreStrategy, aggregationStrategy);
        this.trustRepo = trustRepo;
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
            trustRepo.upsert(actorId, ActorTrustScore.ScoreType.CAPABILITY,
                    entry.getKey(), null, actorType, score.trustScore(),
                    score.decisionCount(), score.overturnedCount(),
                    score.alpha(), score.beta(),
                    score.attestationPositive(), score.attestationNegative(), now);
            results.add(buildScore(actorId, ActorTrustScore.ScoreType.CAPABILITY,
                    entry.getKey(), null, actorType, score, now));
        }

        // ── Persist dimension scores ─────────────────────────────────────────
        for (final Map.Entry<String, Double> entry : computed.dimensionScores().entrySet()) {
            trustRepo.upsert(actorId, ActorTrustScore.ScoreType.DIMENSION,
                    null, entry.getKey(), actorType, entry.getValue(),
                    0, 0, 0.0, 0.0, 0, 0, now);
            results.add(buildDimensionScore(actorId, null, entry.getKey(),
                    actorType, entry.getValue(), 0, 0, 0, now));
        }

        // ── Persist capability×dimension scores ──────────────────────────────
        for (final Map.Entry<String, Map<String, Double>> capEntry :
                computed.capabilityDimensionScores().entrySet()) {
            for (final Map.Entry<String, Double> dimEntry : capEntry.getValue().entrySet()) {
                trustRepo.upsert(actorId, ActorTrustScore.ScoreType.CAPABILITY_DIMENSION,
                        capEntry.getKey(), dimEntry.getKey(), actorType, dimEntry.getValue(),
                        0, 0, 0.0, 0.0, 0, 0, now);
                results.add(buildDimensionScore(actorId, capEntry.getKey(), dimEntry.getKey(),
                        actorType, dimEntry.getValue(), 0, 0, 0, now));
            }
        }

        // ── Persist global score ─────────────────────────────────────────────
        final TrustScoreComputer.ActorScore global = computed.globalScore();
        trustRepo.upsert(actorId, ActorTrustScore.ScoreType.GLOBAL, null, null,
                actorType, global.trustScore(),
                global.decisionCount(), global.overturnedCount(),
                global.alpha(), global.beta(),
                global.attestationPositive(), global.attestationNegative(), now);
        results.add(buildScore(actorId, ActorTrustScore.ScoreType.GLOBAL,
                null, null, actorType, global, now));

        return results;
    }

    private static ActorTrustScore buildScore(final String actorId,
            final ActorTrustScore.ScoreType scoreType,
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
                ? ActorTrustScore.ScoreType.CAPABILITY_DIMENSION
                : ActorTrustScore.ScoreType.DIMENSION;
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
