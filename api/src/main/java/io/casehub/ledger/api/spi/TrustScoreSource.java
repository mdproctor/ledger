package io.casehub.ledger.api.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Source of trust scores for an actor. Abstracts whether scores are read from a materialized
 * store, an in-memory cache, or computed on demand from raw attestation history.
 *
 * <p>All implementations must agree on the empty-actor contract: when no EVENT entries exist
 * for the actor, score methods return {@code OptionalDouble.empty()} and {@code decisionCount}
 * returns 0.
 *
 * <p>Batch methods ({@link #scoresFor} and {@link #decisionCountsFor}) default to a per-actor
 * loop. Implementations backed by a persistent store should override with a single
 * {@code WHERE actorId IN (...)} query to avoid N round-trips.
 */
public interface TrustScoreSource {

    OptionalDouble globalScore(String actorId);

    OptionalDouble capabilityScore(String actorId, String capabilityTag);

    OptionalDouble dimensionScore(String actorId, String dimensionKey);

    OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey);

    /** Returns 0 when no trust history exists for the actor+capability pair. */
    int decisionCount(String actorId, String capabilityTag);

    Map<String, Double> allCapabilityScores(String actorId);

    Map<String, Double> allDimensionScores(String actorId);

    Map<String, Double> qualityScores(String actorId, String capabilityTag);

    /**
     * Batch capability scores for multiple candidates against the same capability tag.
     *
     * <p>Every candidate appears in the result map. {@code OptionalDouble.empty()} means no
     * score has been computed yet — the actor is in the BOOTSTRAP phase for this capability.
     *
     * <p>Ordering follows {@code candidateIds} insertion order. Implementations backed by a
     * persistent store should override with a single {@code IN (...)} query.
     *
     * @param candidateIds actor IDs to score; empty list returns an empty map
     * @param capabilityTag the capability being evaluated
     * @return map from actorId → score; all candidates present, never null values
     */
    default Map<String, OptionalDouble> scoresFor(final List<String> candidateIds,
            final String capabilityTag) {
        final Map<String, OptionalDouble> result = new LinkedHashMap<>(candidateIds.size());
        for (final String id : candidateIds) {
            result.put(id, capabilityScore(id, capabilityTag));
        }
        return result;
    }

    /**
     * Batch decision counts for multiple candidates against the same capability tag.
     *
     * <p>Every candidate appears in the result map. 0 means no history (BOOTSTRAP phase).
     * Ordering follows {@code candidateIds} insertion order.
     *
     * @param candidateIds actor IDs to query; empty list returns an empty map
     * @param capabilityTag the capability being evaluated
     * @return map from actorId → decision count; all candidates present
     */
    default Map<String, Integer> decisionCountsFor(final List<String> candidateIds,
            final String capabilityTag) {
        final Map<String, Integer> result = new LinkedHashMap<>(candidateIds.size());
        for (final String id : candidateIds) {
            result.put(id, decisionCount(id, capabilityTag));
        }
        return result;
    }
}
