package io.casehub.ledger.runtime.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;

/**
 * JPA / EntityManager implementation of {@link ActorTrustScoreRepository}.
 *
 * <p>
 * Upsert is a find-then-update to remain compatible with H2 and PostgreSQL without
 * database-specific SQL. The unique constraint (actor_id, capability_key, dimension_key) with
 * NULLS NOT DISTINCT prevents duplicate rows at the database level.
 *
 * <p>
 * Upsert assumption: each {@code (actorId, scoreType, capabilityKey, dimensionKey)} combination
 * is upserted at most once per transaction. Calling {@code upsert()} twice for the same key in
 * a single transaction may produce a duplicate row if Hibernate does not flush before the second
 * find. Under the default {@code FlushModeType.AUTO}, named queries trigger a flush, so this is
 * safe in practice.
 */
@ApplicationScoped
public class JpaActorTrustScoreRepository implements ActorTrustScoreRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public Optional<ActorTrustScore> findByActorId(final String actorId) {
        return em.createNamedQuery("ActorTrustScore.findGlobalByActorId", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.GLOBAL)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityScore(final String actorId, final String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityByActorIdAndTag", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY)
                .setParameter("capabilityKey", capabilityTag)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findDimensionScore(final String actorId, final String dimension) {
        return em.createNamedQuery("ActorTrustScore.findDimensionByActorIdAndKey", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.DIMENSION)
                .setParameter("dimensionKey", dimension)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityDimension(final String actorId,
            final String capabilityTag, final String dimension) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionByKeys", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .setParameter("dimensionKey", dimension)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<ActorTrustScore> findCapabilityDimensions(final String actorId, final String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionsByCapability", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .getResultList();
    }

    @Override
    public List<ActorTrustScore> findByActorIdAndScoreType(
            final String actorId, final ScoreType scoreType) {
        return em.createNamedQuery("ActorTrustScore.findByActorIdAndScoreType", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", scoreType)
                .getResultList();
    }

    @Override
    @Transactional
    public void upsert(final String actorId, final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType, final double trustScore,
            final int decisionCount, final int overturnedCount,
            final double alpha, final double beta,
            final int attestationPositive, final int attestationNegative,
            final Instant lastComputedAt) {

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
        score.alpha = alpha;
        score.beta = beta;
        score.decisionCount = decisionCount;
        score.overturnedCount = overturnedCount;
        score.attestationPositive = attestationPositive;
        score.attestationNegative = attestationNegative;
        score.lastComputedAt = lastComputedAt;
        em.merge(score);
    }

    private ActorTrustScore findExisting(final String actorId, final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey) {
        return switch (scoreType) {
            case GLOBAL -> findByActorId(actorId).orElse(null);
            case CAPABILITY -> findCapabilityScore(actorId, capabilityKey).orElse(null);
            case DIMENSION -> findDimensionScore(actorId, dimensionKey).orElse(null);
            case CAPABILITY_DIMENSION -> findCapabilityDimension(actorId, capabilityKey, dimensionKey).orElse(null);
        };
    }

    @Override
    @Transactional
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
        findByActorId(actorId).ifPresent(score -> {
            score.globalTrustScore = globalTrustScore;
            em.merge(score);
        });
    }

    @Override
    public List<ActorTrustScore> findAll() {
        return em.createNamedQuery("ActorTrustScore.findAll", ActorTrustScore.class)
                .getResultList();
    }

    @Override
    public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant since) {
        return em.createNamedQuery("ActorTrustScore.findAllByLastComputedAtAfter", ActorTrustScore.class)
                .setParameter("since", since)
                .getResultList();
    }
}
