package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.arc.DefaultBean;

/**
 * No-op {@link ActorTrustScoreRepository} that satisfies the CDI injection point when
 * neither the JPA implementation ({@code JpaActorTrustScoreRepository}) nor an in-memory
 * alternative ({@code InMemoryActorTrustScoreRepository}) is active.
 *
 * <p>
 * Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaActorTrustScoreRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryActorTrustScoreRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 *
 * <p>
 * Consumers that do not need trust scoring can leave this default active —
 * zero overhead, no database access. Trust gate checks will always see empty scores.
 */
@DefaultBean
@ApplicationScoped
public class NoOpActorTrustScoreRepository implements ActorTrustScoreRepository {

    @Override
    public Optional<ActorTrustScore> findByActorId(final String actorId) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityScore(final String actorId,
            final String capabilityTag) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScore> findDimensionScore(final String actorId,
            final String dimension) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScore> findCapabilityDimension(final String actorId,
            final String capabilityTag, final String dimension) {
        return Optional.empty();
    }

    @Override
    public List<ActorTrustScore> findCapabilityDimensions(final String actorId,
            final String capabilityTag) {
        return List.of();
    }

    @Override
    public List<ActorTrustScore> findByActorIdAndScoreType(final String actorId,
            final ScoreType scoreType) {
        return List.of();
    }

    @Override
    public void upsert(final String actorId, final ScoreType scoreType,
            final String capabilityKey, final String dimensionKey,
            final ActorType actorType, final double trustScore,
            final int decisionCount, final int overturnedCount,
            final double alpha, final double beta,
            final int attestationPositive, final int attestationNegative,
            final Instant lastComputedAt) {
        // no-op
    }

    @Override
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
        // no-op
    }

    @Override
    public List<ActorTrustScore> findAll() {
        return List.of();
    }

    @Override
    public List<ActorTrustScore> findAllByLastComputedAtAfter(final Instant since) {
        return List.of();
    }
}
