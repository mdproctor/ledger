package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Bayesian Beta trust score for a decision-making actor, scoped by score type.
 *
 * <p>
 * One row per {@code (actor_id, capability_key, dimension_key)} triple:
 * <ul>
 * <li>{@code GLOBAL} — capability_key null, dimension_key null. Classic score across all decisions.</li>
 * <li>{@code CAPABILITY} — capability_key set, dimension_key null. Scoped binary trust. See ADR 0008.</li>
 * <li>{@code DIMENSION} — capability_key null, dimension_key set. Cross-capability quality score. See #62.</li>
 * <li>{@code CAPABILITY_DIMENSION} — both keys set. Per-capability quality dimension score. See #76.</li>
 * </ul>
 *
 * <p>
 * Binary scores (GLOBAL, CAPABILITY, CAPABILITY_DIMENSION) use Bayesian Beta statistics.
 * Continuous scores (DIMENSION, CAPABILITY_DIMENSION) use decay-weighted average; alpha and beta
 * are stored as 0.0 for these rows. See #78 for the ADR documenting this distinction.
 */
@MappedSuperclass
public class ActorTrustScore {

    /** Score type discriminator — determines which key columns are non-null. */
    public enum ScoreType {
        /** Classic cross-decision score. capability_key and dimension_key are null. */
        GLOBAL,
        /** Capability-scoped binary trust. capability_key is set; dimension_key is null. See ADR 0008. */
        CAPABILITY,
        /** Cross-capability quality dimension. capability_key is null; dimension_key is set. See #62. */
        DIMENSION,
        /** Per-capability quality dimension. Both capability_key and dimension_key are set. See #76. */
        CAPABILITY_DIMENSION
    }

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false)
    public ScoreType scoreType = ScoreType.GLOBAL;

    /** Capability tag for CAPABILITY and CAPABILITY_DIMENSION rows; null for GLOBAL and DIMENSION. */
    @Column(name = "capability_key")
    public String capabilityKey;

    /** Quality dimension name for DIMENSION and CAPABILITY_DIMENSION rows; null for GLOBAL and CAPABILITY. */
    @Column(name = "dimension_key")
    public String dimensionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    public ActorType actorType;

    @Column(name = "trust_score")
    public double trustScore;

    /** Bayesian Beta α parameter. Stored as 0.0 for DIMENSION and CAPABILITY_DIMENSION rows. */
    @Column(name = "alpha_value")
    public double alpha;

    /** Bayesian Beta β parameter. Stored as 0.0 for DIMENSION and CAPABILITY_DIMENSION rows. */
    @Column(name = "beta_value")
    public double beta;

    @Column(name = "decision_count")
    public int decisionCount;

    @Column(name = "overturned_count")
    public int overturnedCount;

    @Column(name = "attestation_positive")
    public int attestationPositive;

    @Column(name = "attestation_negative")
    public int attestationNegative;

    @Column(name = "last_computed_at")
    public Instant lastComputedAt;

    /**
     * EigenTrust global trust share in [0.0, 1.0]; values sum to ≤ 1.0 across all actors.
     * Only meaningful on GLOBAL rows. Zero when EigenTrust is disabled or not yet computed.
     */
    @Column(name = "global_trust_score")
    public double globalTrustScore;
}
