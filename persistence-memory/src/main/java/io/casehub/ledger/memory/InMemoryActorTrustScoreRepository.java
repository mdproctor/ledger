package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

/**
 * Stub in-memory implementation of {@link ActorTrustScoreRepository}.
 * Real implementation comes in Task 6.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryActorTrustScoreRepository implements ActorTrustScoreRepository {

    private final ConcurrentHashMap<UUID, ActorTrustScore> scores = new ConcurrentHashMap<>();

    @Override
    public Optional<ActorTrustScore> findByActorId(final String actorId) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && ScoreType.GLOBAL.equals(s.scoreType))
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityScore(final String actorId, final String capabilityTag) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && ScoreType.CAPABILITY.equals(s.scoreType)
                        && capabilityTag.equals(s.capabilityKey))
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findDimensionScore(final String actorId, final String dimension) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && ScoreType.DIMENSION.equals(s.scoreType)
                        && dimension.equals(s.dimensionKey))
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityDimension(final String actorId,
            final String capabilityTag, final String dimension) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && ScoreType.CAPABILITY_DIMENSION.equals(s.scoreType)
                        && capabilityTag.equals(s.capabilityKey) && dimension.equals(s.dimensionKey))
                .findFirst();
    }

    @Override
    public List<ActorTrustScore> findCapabilityDimensions(final String actorId, final String capabilityTag) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && ScoreType.CAPABILITY_DIMENSION.equals(s.scoreType)
                        && capabilityTag.equals(s.capabilityKey))
                .toList();
    }

    @Override
    public List<ActorTrustScore> findByActorIdAndScoreType(final String actorId, final ScoreType scoreType) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId) && scoreType.equals(s.scoreType))
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

        Optional<ActorTrustScore> existing = findByKey(actorId, scoreType, capabilityKey, dimensionKey);
        final ActorTrustScore score = existing.orElseGet(ActorTrustScore::new);
        if (score.id == null) {
            score.id = UUID.randomUUID();
        }
        score.actorId = actorId;
        score.scoreType = scoreType;
        score.capabilityKey = capabilityKey;
        score.dimensionKey = dimensionKey;
        score.actorType = actorType;
        score.trustScore = trustScore;
        score.decisionCount = decisionCount;
        score.overturnedCount = overturnedCount;
        score.alpha = alpha;
        score.beta = beta;
        score.attestationPositive = attestationPositive;
        score.attestationNegative = attestationNegative;
        score.lastComputedAt = lastComputedAt;
        scores.put(score.id, score);
    }

    @Override
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
        findByActorId(actorId).ifPresent(s -> s.trustScore = globalTrustScore);
    }

    @Override
    public List<ActorTrustScore> findAll() {
        return new ArrayList<>(scores.values());
    }

    @Override
    public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant since) {
        return scores.values().stream()
                .filter(s -> s.lastComputedAt != null && s.lastComputedAt.isAfter(since))
                .toList();
    }

    public void clear() {
        scores.clear();
    }

    private Optional<ActorTrustScore> findByKey(final String actorId, final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey) {
        return scores.values().stream()
                .filter(s -> actorId.equals(s.actorId)
                        && scoreType.equals(s.scoreType)
                        && Objects.equals(capabilityKey, s.capabilityKey)
                        && Objects.equals(dimensionKey, s.dimensionKey))
                .findFirst();
    }
}
