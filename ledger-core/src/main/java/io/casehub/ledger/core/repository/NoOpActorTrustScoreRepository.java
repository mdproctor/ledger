package io.casehub.ledger.core.repository;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.ActorType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * No-op {@link ActorTrustScoreRepository} — returns empty results for all queries.
 *
 * <p>Framework modules wrap this with their DI annotations:
 * Quarkus {@code @DefaultBean}, Spring {@code @ConditionalOnMissingBean}.
 */
public class NoOpActorTrustScoreRepository implements ActorTrustScoreRepository {

    @Override
    public Optional<ActorTrustScoreBase> findByActorId(final String actorId) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityScore(final String actorId,
                                                             final String capabilityTag) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScoreBase> findDimensionScore(final String actorId,
                                                            final String dimension) {
        return Optional.empty();
    }

    @Override
    public Optional<ActorTrustScoreBase> findCapabilityDimension(final String actorId,
                                                                 final String capabilityTag, final String dimension) {
        return Optional.empty();
    }

    @Override
    public List<ActorTrustScoreBase> findCapabilityDimensions(final String actorId,
                                                              final String capabilityTag) {
        return List.of();
    }

    @Override
    public List<ActorTrustScoreBase> findByActorIdAndScoreType(final String actorId,
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
    }

    @Override
    public void updateGlobalTrustScore(final String actorId, final double globalTrustScore) {
    }

    @Override
    public List<ActorTrustScoreBase> findAll() {
        return List.of();
    }

    @Override
    public List<ActorTrustScoreBase> findAllByLastComputedAtAfter(final Instant since) {
        return List.of();
    }
}
