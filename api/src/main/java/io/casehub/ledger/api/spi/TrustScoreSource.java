package io.casehub.ledger.api.spi;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Source of trust scores for an actor. Abstracts whether scores are read from a materialized
 * store, an in-memory cache, or computed on demand from raw attestation history.
 *
 * <p>All implementations must agree on the empty-actor contract: when no EVENT entries exist
 * for the actor, score methods return {@code OptionalDouble.empty()} and {@code decisionCount}
 * returns 0.
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
}
