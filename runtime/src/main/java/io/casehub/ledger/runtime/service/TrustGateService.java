package io.casehub.ledger.runtime.service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;

/**
 * CDI bean for trust threshold enforcement.
 *
 * <p>
 * Single query point for trust decisions. Consumers call these methods rather than querying
 * {@link ActorTrustScoreRepository} directly — threshold and fallback logic stays in one place.
 */
@ApplicationScoped
public class TrustGateService {

    private final ActorTrustScoreRepository repository;

    @Inject
    public TrustGateService(final ActorTrustScoreRepository repository) {
        this.repository = repository;
    }

    /** Returns true if the actor's global trust score meets or exceeds {@code minTrust}. */
    public boolean meetsThreshold(final String actorId, final double minTrust) {
        return repository.findByActorId(actorId)
                .map(s -> s.trustScore >= minTrust)
                .orElse(false);
    }

    /**
     * Returns true if the actor's CAPABILITY score for {@code capabilityTag} meets {@code minTrust}.
     * Falls back to the global score when no capability-specific score has been computed.
     */
    public boolean meetsThreshold(final String actorId, final String capabilityTag,
            final double minTrust) {
        return repository.findCapabilityScore(actorId, capabilityTag)
                .map(s -> s.trustScore >= minTrust)
                .orElseGet(() -> meetsThreshold(actorId, minTrust));
    }

    /**
     * Returns true if the actor's CAPABILITY_DIMENSION quality score for the given
     * capability+dimension meets {@code minScore}. Returns false if no score exists.
     */
    public boolean meetsQualityThreshold(final String actorId, final String capabilityTag,
            final String dimension, final double minScore) {
        return repository.findCapabilityDimension(actorId, capabilityTag, dimension)
                .map(s -> s.trustScore >= minScore)
                .orElse(false);
    }

    /** Returns the actor's global trust score, or empty if not yet computed. */
    public Optional<Double> currentScore(final String actorId) {
        return repository.findByActorId(actorId).map(s -> s.trustScore);
    }

    /** Returns the actor's CAPABILITY score for the given tag, or empty if not yet computed. */
    public Optional<Double> currentScore(final String actorId, final String capabilityTag) {
        return repository.findCapabilityScore(actorId, capabilityTag).map(s -> s.trustScore);
    }

    /** Returns the actor's CAPABILITY_DIMENSION quality score, or empty if not yet computed. */
    public Optional<Double> qualityScore(final String actorId, final String capabilityTag,
            final String dimension) {
        return repository.findCapabilityDimension(actorId, capabilityTag, dimension)
                .map(s -> s.trustScore);
    }

    /**
     * Returns all DIMENSION scores for the actor, keyed by dimension name.
     * Empty map if no dimension scores have been computed.
     */
    public Map<String, Double> dimensionScores(final String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.DIMENSION).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }

    /**
     * Returns all CAPABILITY trust scores for the actor, keyed by capability tag.
     * Empty map if no capability scores have been computed yet.
     */
    public Map<String, Double> allCapabilityScores(final String actorId) {
        return repository.findByActorIdAndScoreType(actorId, ScoreType.CAPABILITY).stream()
                .collect(Collectors.toMap(s -> s.capabilityKey, s -> s.trustScore));
    }

    /** Returns the DIMENSION score for a specific dimension, or empty if not yet computed. */
    public Optional<Double> dimensionScore(final String actorId, final String dimension) {
        return repository.findDimensionScore(actorId, dimension).map(s -> s.trustScore);
    }

    /**
     * Returns all CAPABILITY_DIMENSION quality scores for the actor scoped to
     * {@code capabilityTag}, keyed by dimension name. Empty map if none computed.
     */
    public Map<String, Double> qualityScores(final String actorId, final String capabilityTag) {
        return repository.findCapabilityDimensions(actorId, capabilityTag).stream()
                .collect(Collectors.toMap(s -> s.dimensionKey, s -> s.trustScore));
    }

    /**
     * Returns the full {@link ActorTrustScore} entity for the actor's GLOBAL row, or empty.
     * Use when the caller needs metrics beyond the scalar score (alpha, beta, decisionCount, etc.).
     */
    public Optional<ActorTrustScore> findScore(final String actorId) {
        return repository.findByActorId(actorId);
    }
}
