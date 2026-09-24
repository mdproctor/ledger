package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.model.TrustScoreSnapshotBase;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.platform.api.identity.ActorType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PerActorTrustComputerCore {

    private final TrustScoreCalculator calculator;
    private final ActorTrustScoreRepository trustRepo;
    private final TrustScoreSnapshotRepository snapshotRepo;

    public PerActorTrustComputerCore(TrustScoreCalculator calculator,
                                     ActorTrustScoreRepository trustRepo,
                                     TrustScoreSnapshotRepository snapshotRepo) {
        this.calculator = calculator;
        this.trustRepo = trustRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public List<ActorTrustScoreBase> computeForActor(String actorId,
                                                      List<LedgerEntry> decisions,
                                                      Map<UUID, List<LedgerAttestation>> attestationsByEntry,
                                                      Instant now) {
        ActorType actorType = decisions.stream()
                .map(e -> e.actorType)
                .filter(t -> t != null)
                .findFirst()
                .orElse(ActorType.HUMAN);

        TrustScoreCalculator.ComputedScores computed =
                calculator.computeAll(decisions, attestationsByEntry, now);

        List<ActorTrustScoreBase> results = new ArrayList<>();

        for (Map.Entry<String, TrustScoreComputer.ActorScore> entry :
                computed.capabilityScores().entrySet()) {
            TrustScoreComputer.ActorScore score = entry.getValue();
            double previous = trustRepo.findCapabilityScore(actorId, entry.getKey())
                    .map(s -> s.trustScore).orElse(0.0);
            trustRepo.upsert(actorId, ScoreType.CAPABILITY,
                    entry.getKey(), null, actorType, score.trustScore(),
                    score.decisionCount(), score.overturnedCount(),
                    score.alpha(), score.beta(),
                    score.attestationPositive(), score.attestationNegative(), now);
            snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.CAPABILITY,
                    entry.getKey(), null, score.trustScore(), previous, now));
            results.add(buildScore(actorId, ScoreType.CAPABILITY,
                    entry.getKey(), null, actorType, score, now));
        }

        for (Map.Entry<String, Double> entry : computed.dimensionScores().entrySet()) {
            double previousDim = trustRepo.findDimensionScore(actorId, entry.getKey())
                    .map(s -> s.trustScore).orElse(0.0);
            trustRepo.upsert(actorId, ScoreType.DIMENSION,
                    null, entry.getKey(), actorType, entry.getValue(),
                    0, 0, 0.0, 0.0, 0, 0, now);
            snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.DIMENSION,
                    null, entry.getKey(), entry.getValue(), previousDim, now));
            results.add(buildDimensionScore(actorId, null, entry.getKey(),
                    actorType, entry.getValue(), now));
        }

        for (Map.Entry<String, Map<String, Double>> capEntry :
                computed.capabilityDimensionScores().entrySet()) {
            for (Map.Entry<String, Double> dimEntry : capEntry.getValue().entrySet()) {
                double previousCapDim = trustRepo.findCapabilityDimension(
                        actorId, capEntry.getKey(), dimEntry.getKey())
                        .map(s -> s.trustScore).orElse(0.0);
                trustRepo.upsert(actorId, ScoreType.CAPABILITY_DIMENSION,
                        capEntry.getKey(), dimEntry.getKey(), actorType, dimEntry.getValue(),
                        0, 0, 0.0, 0.0, 0, 0, now);
                snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.CAPABILITY_DIMENSION,
                        capEntry.getKey(), dimEntry.getKey(), dimEntry.getValue(), previousCapDim, now));
                results.add(buildDimensionScore(actorId, capEntry.getKey(), dimEntry.getKey(),
                        actorType, dimEntry.getValue(), now));
            }
        }

        TrustScoreComputer.ActorScore global = computed.globalScore();
        double previousGlobal = trustRepo.findByActorId(actorId)
                .map(s -> s.trustScore).orElse(0.0);
        trustRepo.upsert(actorId, ScoreType.GLOBAL, null, null,
                actorType, global.trustScore(),
                global.decisionCount(), global.overturnedCount(),
                global.alpha(), global.beta(),
                global.attestationPositive(), global.attestationNegative(), now);
        snapshotRepo.save(new TrustScoreSnapshotBase(actorId, ScoreType.GLOBAL,
                null, null, global.trustScore(), previousGlobal, now));
        results.add(buildScore(actorId, ScoreType.GLOBAL,
                null, null, actorType, global, now));

        return results;
    }

    private static ActorTrustScoreBase buildScore(String actorId,
                                                   ScoreType scoreType,
                                                   String capabilityKey, String dimensionKey,
                                                   ActorType actorType,
                                                   TrustScoreComputer.ActorScore score, Instant now) {
        ActorTrustScoreBase s = new ActorTrustScoreBase();
        s.actorId = actorId;
        s.scoreType = scoreType;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score.trustScore();
        s.decisionCount = score.decisionCount();
        s.overturnedCount = score.overturnedCount();
        s.alphaValue = score.alpha();
        s.betaValue = score.beta();
        s.attestationPositive = score.attestationPositive();
        s.attestationNegative = score.attestationNegative();
        s.lastComputedAt = now;
        return s;
    }

    private static ActorTrustScoreBase buildDimensionScore(String actorId,
                                                            String capabilityKey, String dimensionKey,
                                                            ActorType actorType,
                                                            double score, Instant now) {
        ActorTrustScoreBase s = new ActorTrustScoreBase();
        s.actorId = actorId;
        s.scoreType = capabilityKey != null
                ? ScoreType.CAPABILITY_DIMENSION
                : ScoreType.DIMENSION;
        s.capabilityKey = capabilityKey;
        s.dimensionKey = dimensionKey;
        s.actorType = actorType;
        s.trustScore = score;
        s.lastComputedAt = now;
        return s;
    }
}
