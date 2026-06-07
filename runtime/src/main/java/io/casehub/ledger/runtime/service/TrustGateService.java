package io.casehub.ledger.runtime.service;

import java.util.Map;
import java.util.OptionalDouble;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * CDI bean for trust threshold enforcement.
 *
 * <p>
 * Policy layer on top of {@link TrustScoreSource}. Consumers call these methods rather
 * than querying the source directly — threshold checks and CAPABILITY-to-GLOBAL fallback
 * logic stays in one place.
 */
@ApplicationScoped
public class TrustGateService {

    private final TrustScoreSource source;

    @Inject
    public TrustGateService(final TrustScoreSource source) {
        this.source = source;
    }

    public Uni<Boolean> meetsThresholdAsync(final String actorId, final double minTrust) {
        return Uni.createFrom().item(() -> meetsThreshold(actorId, minTrust))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public boolean meetsThreshold(final String actorId, final double minTrust) {
        final OptionalDouble score = source.globalScore(actorId);
        return score.isPresent() && score.getAsDouble() >= minTrust;
    }

    /**
     * Returns true if the actor's CAPABILITY score for {@code capabilityTag} meets {@code minTrust}.
     * Falls back to the global score when no capability-specific score has been computed.
     */
    public boolean meetsThreshold(final String actorId, final String capabilityTag,
            final double minTrust) {
        final OptionalDouble capScore = source.capabilityScore(actorId, capabilityTag);
        if (capScore.isPresent()) {
            return capScore.getAsDouble() >= minTrust;
        }
        return meetsThreshold(actorId, minTrust);
    }

    public boolean meetsQualityThreshold(final String actorId, final String capabilityTag,
            final String dimension, final double minScore) {
        final OptionalDouble score = source.capabilityDimensionScore(actorId, capabilityTag, dimension);
        return score.isPresent() && score.getAsDouble() >= minScore;
    }

    public OptionalDouble currentScore(final String actorId) {
        return source.globalScore(actorId);
    }

    public OptionalDouble currentScore(final String actorId, final String capabilityTag) {
        return source.capabilityScore(actorId, capabilityTag);
    }

    public OptionalDouble qualityScore(final String actorId, final String capabilityTag,
            final String dimension) {
        return source.capabilityDimensionScore(actorId, capabilityTag, dimension);
    }

    public Map<String, Double> allDimensionScores(final String actorId) {
        return source.allDimensionScores(actorId);
    }

    public Map<String, Double> allCapabilityScores(final String actorId) {
        return source.allCapabilityScores(actorId);
    }

    public OptionalDouble dimensionScore(final String actorId, final String dimension) {
        return source.dimensionScore(actorId, dimension);
    }

    public Map<String, Double> qualityScores(final String actorId, final String capabilityTag) {
        return source.qualityScores(actorId, capabilityTag);
    }
}
