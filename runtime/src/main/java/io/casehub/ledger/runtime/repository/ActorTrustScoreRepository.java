package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.casehub.ledger.api.model.ActorTrustScore.ScoreType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.model.ActorTrustScore;

/** SPI for persisting and querying {@link ActorTrustScore} records. */
public interface ActorTrustScoreRepository {

    /**
     * Find the GLOBAL trust score for an actor, or empty if none computed yet.
     */
    Optional<ActorTrustScore> findByActorId(String actorId);

    /**
     * Find the CAPABILITY score for an actor and capability tag, or empty if not yet computed.
     */
    Optional<ActorTrustScore> findCapabilityScore(String actorId, String capabilityTag);

    /**
     * Find the DIMENSION score for an actor and dimension name, or empty if not yet computed.
     */
    Optional<ActorTrustScore> findDimensionScore(String actorId, String dimension);

    /**
     * Find the CAPABILITY_DIMENSION score for an actor, capability tag, and dimension name.
     */
    Optional<ActorTrustScore> findCapabilityDimension(String actorId, String capabilityTag, String dimension);

    /**
     * Return all CAPABILITY_DIMENSION scores for an actor scoped to the given capability tag.
     */
    List<ActorTrustScore> findCapabilityDimensions(String actorId, String capabilityTag);

    /**
     * Return all trust scores for an actor of a given type.
     * For GLOBAL: returns 0 or 1 result. For CAPABILITY/DIMENSION/CAPABILITY_DIMENSION: returns all scoped rows.
     */
    List<ActorTrustScore> findByActorIdAndScoreType(String actorId, ScoreType scoreType);

    /**
     * Upsert (insert or update) a trust score for the given actor and scope.
     *
     * @param capabilityKey the capability tag, or null for GLOBAL and DIMENSION rows
     * @param dimensionKey  the dimension name, or null for GLOBAL and CAPABILITY rows
     */
    void upsert(String actorId, ScoreType scoreType,
            String capabilityKey, String dimensionKey,
            ActorType actorType, double trustScore,
            int decisionCount, int overturnedCount, double alpha, double beta,
            int attestationPositive, int attestationNegative,
            Instant lastComputedAt);

    /**
     * Update the EigenTrust global trust score for an actor's GLOBAL row.
     */
    void updateGlobalTrustScore(String actorId, double globalTrustScore);

    /**
     * Return all computed trust scores across all actors and score types.
     */
    List<ActorTrustScore> findAll();

    /**
     * Return all trust scores whose {@code lastComputedAt} timestamp is strictly after {@code since}.
     */
    List<ActorTrustScore> findAllByLastComputedAtAfter(Instant since);

    /**
     * Batch-fetch CAPABILITY scores for multiple actors against the same capability tag.
     *
     * <p>Returns only rows that exist — absent actors are not represented. Callers must
     * correlate by {@code actorId} to distinguish "no score" from "score is 0".
     *
     * <p>Default implementation loops over {@link #findCapabilityScore}. JPA implementations
     * should override with a single {@code WHERE actorId IN (...)} query.
     *
     * @param actorIds     the actors to query; empty collection returns an empty list
     * @param capabilityTag the capability tag to filter on
     * @return matching CAPABILITY rows in unspecified order
     */
    default List<ActorTrustScore> findCapabilityScoresByActorIds(final Collection<String> actorIds,
            final String capabilityTag) {
        return actorIds.stream()
                .map(id -> findCapabilityScore(id, capabilityTag))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
