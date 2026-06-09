package io.casehub.ledger.runtime.service;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.qualifier.CrossTenant;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;

/**
 * Default {@link TrustScoreSource}: reads pre-computed scores from {@link ActorTrustScoreRepository}.
 *
 * <p>No caching, no startup hydration — each call reads the database. Suitable for most
 * deployments where trust scoring is backed by the nightly batch job or incremental observer.
 */
@ApplicationScoped
@DefaultBean
public class MaterializedTrustScoreSource implements TrustScoreSource {

    private final ActorTrustScoreRepository repository;

    @Inject
    public MaterializedTrustScoreSource(@CrossTenant final ActorTrustScoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public OptionalDouble globalScore(final String actorId) {
        return repository.findByActorId(actorId)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble capabilityScore(final String actorId, final String capabilityTag) {
        return repository.findCapabilityScore(actorId, capabilityTag)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble dimensionScore(final String actorId, final String dimensionKey) {
        return repository.findDimensionScore(actorId, dimensionKey)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble capabilityDimensionScore(final String actorId, final String capabilityTag,
            final String dimensionKey) {
        return repository.findCapabilityDimension(actorId, capabilityTag, dimensionKey)
                .map(s -> OptionalDouble.of(s.trustScore))
                .orElse(OptionalDouble.empty());
    }

    @Override
    public int decisionCount(final String actorId, final String capabilityTag) {
        return repository.findCapabilityScore(actorId, capabilityTag)
                .map(s -> s.decisionCount)
                .orElse(0);
    }

    @Override
    public Map<String, Double> allCapabilityScores(final String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY).stream()
                .collect(Collectors.toMap(s -> s.capabilityKey, s -> s.trustScore));
    }

    @Override
    public Map<String, Double> allDimensionScores(final String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }

    @Override
    public Map<String, Double> qualityScores(final String actorId, final String capabilityTag) {
        return repository.findCapabilityDimensions(actorId, capabilityTag).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }
}
