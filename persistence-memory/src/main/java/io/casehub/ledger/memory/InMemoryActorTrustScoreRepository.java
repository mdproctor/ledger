package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryActorTrustScoreRepository implements ActorTrustScoreRepository {

    private final ConcurrentHashMap<String, ActorTrustScore> store = new ConcurrentHashMap<>();

    private static String key(String actorId, ScoreType type, String cap, String dim) {
        return actorId + "|" + type + "|" + nvl(cap) + "|" + nvl(dim);
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    @Override
    public Optional<ActorTrustScore> findByActorId(final String actorId) {
        return Optional.ofNullable(store.get(key(actorId, ScoreType.GLOBAL, null, null)));
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityScore(final String actorId,
            final String capabilityTag) {
        return Optional.ofNullable(store.get(key(actorId, ScoreType.CAPABILITY, capabilityTag, null)));
    }

    @Override
    public Optional<ActorTrustScore> findDimensionScore(final String actorId,
            final String dimension) {
        return Optional.ofNullable(store.get(key(actorId, ScoreType.DIMENSION, null, dimension)));
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityDimension(final String actorId,
            final String capabilityTag, final String dimension) {
        return Optional.ofNullable(
                store.get(key(actorId, ScoreType.CAPABILITY_DIMENSION, capabilityTag, dimension)));
    }

    @Override
    public List<ActorTrustScore> findCapabilityDimensions(final String actorId,
            final String capabilityTag) {
        return store.values().stream()
                .filter(s -> actorId.equals(s.actorId))
                .filter(s -> ScoreType.CAPABILITY_DIMENSION.equals(s.scoreType))
                .filter(s -> capabilityTag.equals(s.capabilityKey))
                .toList();
    }

    @Override
    public List<ActorTrustScore> findByActorIdAndScoreType(final String actorId,
            final ScoreType scoreType) {
        return store.values().stream()
                .filter(s -> actorId.equals(s.actorId))
                .filter(s -> scoreType.equals(s.scoreType))
                .toList();
    }

    @Override
    public void upsert(final String actorId, final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType, final double trustScore,
            final int decisionCount, final int overturnedCount,
            final double alpha, final double beta,
            final int attestationPositive, final int attestationNegative,
            final Instant lastComputedAt) {

        final String k = key(actorId, scoreType, capabilityKey, dimensionKey);
        store.compute(k, (key, existing) -> {
            final ActorTrustScore score = existing != null ? existing : new ActorTrustScore();
            if (existing == null) {
                score.id = UUID.randomUUID();
                score.actorId = actorId;
                score.scoreType = scoreType;
                score.capabilityKey = capabilityKey;
                score.dimensionKey = dimensionKey;
            }
            score.actorType = actorType;
            score.trustScore = trustScore;
            score.alpha = alpha;
            score.beta = beta;
            score.decisionCount = decisionCount;
            score.overturnedCount = overturnedCount;
            score.attestationPositive = attestationPositive;
            score.attestationNegative = attestationNegative;
            score.lastComputedAt = lastComputedAt;
            return score;
        });
    }

    @Override
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
        store.computeIfPresent(key(actorId, ScoreType.GLOBAL, null, null),
                (k, score) -> { score.globalTrustScore = globalTrustScore; return score; });
    }

    @Override
    public List<ActorTrustScore> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant since) {
        return store.values().stream()
                .filter(s -> s.lastComputedAt != null && s.lastComputedAt.isAfter(since))
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
