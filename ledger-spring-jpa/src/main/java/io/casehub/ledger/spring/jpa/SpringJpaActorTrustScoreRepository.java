package io.casehub.ledger.spring.jpa;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.jpa.ActorTrustScore;
import io.casehub.platform.api.identity.ActorType;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpringJpaActorTrustScoreRepository implements ActorTrustScoreRepository {

    private final EntityManager em;

    public SpringJpaActorTrustScoreRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<ActorTrustScoreBase> findByActorId(String actorId) {
        return em.createNamedQuery("ActorTrustScore.findGlobalByActorId", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.GLOBAL)
                .getResultStream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityScore(String actorId, String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityByActorIdAndTag", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY)
                .setParameter("capabilityKey", capabilityTag)
                .getResultStream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findDimensionScore(String actorId, String dimension) {
        return em.createNamedQuery("ActorTrustScore.findDimensionByActorIdAndKey", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.DIMENSION)
                .setParameter("dimensionKey", dimension)
                .getResultStream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityDimension(String actorId, String capabilityTag, String dimension) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionByKeys", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .setParameter("dimensionKey", dimension)
                .getResultStream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public List<ActorTrustScoreBase> findCapabilityDimensions(String actorId, String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionsByCapability", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .getResultStream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findByActorIdAndScoreType(String actorId, ScoreType scoreType) {
        return em.createNamedQuery("ActorTrustScore.findByActorIdAndScoreType", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", scoreType)
                .getResultStream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    @Transactional
    public void upsert(String actorId, ScoreType scoreType,
                       String capabilityKey, String dimensionKey,
                       ActorType actorType, double trustScore,
                       int decisionCount, int overturnedCount,
                       double alpha, double beta,
                       int attestationPositive, int attestationNegative,
                       Instant lastComputedAt) {
        ActorTrustScore score = findExisting(actorId, scoreType, capabilityKey, dimensionKey);
        if (score == null) {
            score = new ActorTrustScore();
            score.id = UUID.randomUUID();
            score.actorId = actorId;
            score.scoreType = scoreType;
            score.capabilityKey = capabilityKey;
            score.dimensionKey = dimensionKey;
        }
        score.actorType = actorType;
        score.trustScore = trustScore;
        score.alphaValue = alpha;
        score.betaValue = beta;
        score.decisionCount = decisionCount;
        score.overturnedCount = overturnedCount;
        score.attestationPositive = attestationPositive;
        score.attestationNegative = attestationNegative;
        score.lastComputedAt = lastComputedAt;
        em.merge(score);
    }

    private ActorTrustScore findExisting(String actorId, ScoreType scoreType,
                                          String capabilityKey, String dimensionKey) {
        return switch (scoreType) {
            case GLOBAL -> (ActorTrustScore) findByActorId(actorId).orElse(null);
            case CAPABILITY -> (ActorTrustScore) findCapabilityScore(actorId, capabilityKey).orElse(null);
            case DIMENSION -> (ActorTrustScore) findDimensionScore(actorId, dimensionKey).orElse(null);
            case CAPABILITY_DIMENSION -> (ActorTrustScore) findCapabilityDimension(actorId, capabilityKey, dimensionKey).orElse(null);
        };
    }

    @Override
    @Transactional
    public void updateGlobalTrustScore(String actorId, double globalTrustScore) {
        findByActorId(actorId).ifPresent(score -> {
            score.globalTrustScore = globalTrustScore;
            em.merge(score);
        });
    }

    @Override
    public List<ActorTrustScoreBase> findAll() {
        return em.createNamedQuery("ActorTrustScore.findAll", ActorTrustScore.class)
                .getResultStream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findAllDetached() {
        return em.createNamedQuery("ActorTrustScore.findAll", ActorTrustScore.class)
                .getResultStream()
                .peek(em::detach)
                .<ActorTrustScoreBase>map(s -> s)
                .toList();
    }

    @Override
    public List<ActorTrustScoreBase> findAllByLastComputedAtAfter(Instant since) {
        return em.createNamedQuery("ActorTrustScore.findAllByLastComputedAtAfter", ActorTrustScore.class)
                .setParameter("since", since)
                .getResultStream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findCapabilityScoresByActorIds(Collection<String> actorIds, String capabilityTag) {
        if (actorIds.isEmpty()) {
            return List.of();
        }
        return em.createNamedQuery("ActorTrustScore.findCapabilityScoresByActorIds", ActorTrustScore.class)
                .setParameter("actorIds", actorIds)
                .setParameter("scoreType", ScoreType.CAPABILITY)
                .setParameter("capabilityKey", capabilityTag)
                .getResultStream().<ActorTrustScoreBase>map(s -> s).toList();
    }
}
