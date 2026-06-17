package io.casehub.ledger.runtime.model;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Bayesian Beta trust score for a decision-making actor, scoped by score type.
 *
 * <p>
 * One row per {@code (actor_id, capability_key, dimension_key)} triple.
 * Plain {@code @Entity} — queries via {@code @NamedQuery} + EntityManager.
 */
@Entity
@Table(name = "actor_trust_score", uniqueConstraints = @UniqueConstraint(
        name = "uq_actor_trust_score_key",
        columnNames = {"actor_id", "capability_key", "dimension_key"}))
@NamedQuery(
        name = "ActorTrustScore.findAll",
        query = "SELECT s FROM ActorTrustScore s")
@NamedQuery(
        name = "ActorTrustScore.findGlobalByActorId",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey IS NULL")
@NamedQuery(
        name = "ActorTrustScore.findByActorIdAndScoreType",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityByActorIdAndTag",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey IS NULL")
@NamedQuery(
        name = "ActorTrustScore.findDimensionByActorIdAndKey",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey = :dimensionKey")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityDimensionByKeys",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey = :dimensionKey")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityDimensionsByCapability",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey")
@NamedQuery(
        name = "ActorTrustScore.findAllByLastComputedAtAfter",
        query = "SELECT s FROM ActorTrustScore s WHERE s.lastComputedAt > :since")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityScoresByActorIds",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey IS NULL")
public class ActorTrustScore extends io.casehub.ledger.api.model.ActorTrustScore {

}
