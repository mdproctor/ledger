package io.casehub.ledger.runtime.repository.jpa;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.jpa.ActorTrustScore;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
@DefaultBean
public class JpaActorTrustScoreRepository implements ActorTrustScoreRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public Optional<ActorTrustScoreBase> findByActorId(final String actorId) {
        return em.createNamedQuery("ActorTrustScore.findGlobalByActorId", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.GLOBAL)
                .setMaxResults(1).getResultList().stream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityScore(final String actorId, final String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityByActorIdAndTag", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY)
                .setParameter("capabilityKey", capabilityTag)
                .setMaxResults(1).getResultList().stream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findDimensionScore(final String actorId, final String dimension) {
        return em.createNamedQuery("ActorTrustScore.findDimensionByActorIdAndKey", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.DIMENSION)
                .setParameter("dimensionKey", dimension)
                .setMaxResults(1).getResultList().stream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityDimension(final String actorId,
            final String capabilityTag, final String dimension) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionByKeys", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .setParameter("dimensionKey", dimension)
                .setMaxResults(1).getResultList().stream()
                .<ActorTrustScoreBase>map(s -> s)
                .findFirst();
    }

    @Override
    public List<ActorTrustScoreBase> findCapabilityDimensions(final String actorId, final String capabilityTag) {
        return em.createNamedQuery("ActorTrustScore.findCapabilityDimensionsByCapability", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", ScoreType.CAPABILITY_DIMENSION)
                .setParameter("capabilityKey", capabilityTag)
                .getResultList().stream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findByActorIdAndScoreType(
            final String actorId, final ScoreType scoreType) {
        return em.createNamedQuery("ActorTrustScore.findByActorIdAndScoreType", ActorTrustScore.class)
                .setParameter("actorId", actorId)
                .setParameter("scoreType", scoreType)
                .getResultList().stream().<ActorTrustScoreBase>map(s -> s).toList();
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

        ActorTrustScore existing = findExisting(actorId, scoreType, capabilityKey, dimensionKey);
        if (existing == null) {
            ActorTrustScore score = new ActorTrustScore();
            score.id = UUID.randomUUID();
            score.actorId = actorId;
            score.scoreType = scoreType;
            score.capabilityKey = capabilityKey;
            score.dimensionKey = dimensionKey;
            score.actorType = actorType;
            score.trustScore = trustScore;
            score.alphaValue    = alpha;
            score.betaValue     = beta;
            score.decisionCount = decisionCount;
            score.overturnedCount = overturnedCount;
            score.attestationPositive = attestationPositive;
            score.attestationNegative = attestationNegative;
            score.lastComputedAt = lastComputedAt;
            em.persist(score);
        } else {
            em.createQuery("UPDATE ActorTrustScore s SET " +
                    "s.actorType = :actorType, s.trustScore = :trustScore, " +
                    "s.alphaValue = :alpha, s.betaValue = :beta, " +
                    "s.decisionCount = :decisionCount, s.overturnedCount = :overturnedCount, " +
                    "s.attestationPositive = :posAtt, s.attestationNegative = :negAtt, " +
                    "s.lastComputedAt = :lastComputedAt " +
                    "WHERE s.id = :id")
                .setParameter("actorType", actorType)
                .setParameter("trustScore", trustScore)
                .setParameter("alpha", alpha)
                .setParameter("beta", beta)
                .setParameter("decisionCount", decisionCount)
                .setParameter("overturnedCount", overturnedCount)
                .setParameter("posAtt", attestationPositive)
                .setParameter("negAtt", attestationNegative)
                .setParameter("lastComputedAt", lastComputedAt)
                .setParameter("id", existing.id)
                .executeUpdate();
        }
    }

    private ActorTrustScore findExisting(final String actorId, final ScoreType scoreType,
                                         final String capabilityKey, final String dimensionKey) {
        return switch (scoreType) {
            case GLOBAL -> (ActorTrustScore) findByActorId(actorId).orElse(null);
            case CAPABILITY -> (ActorTrustScore) findCapabilityScore(actorId, capabilityKey).orElse(null);
            case DIMENSION -> (ActorTrustScore) findDimensionScore(actorId, dimensionKey).orElse(null);
            case CAPABILITY_DIMENSION -> (ActorTrustScore) findCapabilityDimension(actorId, capabilityKey, dimensionKey).orElse(null);
        };
    }

    @Override
    @Transactional
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
        em.createQuery("UPDATE ActorTrustScore s SET s.globalTrustScore = :gts WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey IS NULL")
          .setParameter("gts", globalTrustScore)
          .setParameter("actorId", actorId)
          .setParameter("scoreType", ScoreType.GLOBAL)
          .executeUpdate();
    }

    @Override
    public List<ActorTrustScoreBase> findAll() {
        return em.createNamedQuery("ActorTrustScore.findAll", ActorTrustScore.class)
                .getResultList().stream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findAllByLastComputedAtAfter(final Instant since) {
        return em.createNamedQuery("ActorTrustScore.findAllByLastComputedAtAfter", ActorTrustScore.class)
                .setParameter("since", since)
                .getResultList().stream().<ActorTrustScoreBase>map(s -> s).toList();
    }

    @Override
    public List<ActorTrustScoreBase> findCapabilityScoresByActorIds(final Collection<String> actorIds,
            final String capabilityTag) {
        if (actorIds.isEmpty()) {
            return List.of();
        }
        return em.createNamedQuery("ActorTrustScore.findCapabilityScoresByActorIds", ActorTrustScore.class)
                .setParameter("actorIds", actorIds)
                .setParameter("scoreType", ScoreType.CAPABILITY)
                .setParameter("capabilityKey", capabilityTag)
                .getResultList().stream().<ActorTrustScoreBase>map(s -> s).toList();
    }
}
