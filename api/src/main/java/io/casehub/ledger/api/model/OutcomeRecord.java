package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.casehub.platform.api.identity.ActorType;

/**
 * Captures a single plugin decision and its outcome for recording via {@link io.casehub.ledger.api.spi.OutcomeRecorder}.
 *
 * <p>Use {@link #of(String, UUID, String, AttestationVerdict, double)} for routing-aware writes.
 * Use {@link #ofGlobal(String, UUID, AttestationVerdict, double)} only when capability-differentiated
 * routing is not the goal — GLOBAL-scoped attestations do not reach {@code TrustScoreCache}
 * and therefore do not influence {@code TrustWeightedAgentStrategy}.
 *
 * <p>Confidence in (0.0, 1.0]. Recommended values: 0.1 (tick-level), 0.7 (game-level), 1.0 (session).
 */
public record OutcomeRecord(
        String actorId,
        UUID subjectId,
        AttestationVerdict verdict,
        double confidence,
        String capabilityTag,
        ActorType actorType,
        String actorRole,
        Instant occurredAt,
        String attestorId,
        ActorType attestorType
) {
    public OutcomeRecord {
        Objects.requireNonNull(actorId,       "actorId required");
        Objects.requireNonNull(subjectId,     "subjectId required");
        Objects.requireNonNull(verdict,       "verdict required");
        Objects.requireNonNull(capabilityTag, "capabilityTag required — use CapabilityTag.GLOBAL if intentional");
        if (confidence <= 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0.0, 1.0] — got " + confidence
                            + ". Recommended: 0.1 (tick), 0.7 (game), 1.0 (session).");
        }
        if ((attestorId == null) != (attestorType == null)) {
            throw new IllegalArgumentException(
                    "attestorId and attestorType must be provided together or both left null.");
        }
        if (actorType == null) {
            actorType = ActorType.AGENT;
        }
    }

    /**
     * Primary factory for routing-aware outcome recording.
     * capabilityTag is required — GLOBAL-scoped attestations do not reach TrustScoreCache.
     */
    public static OutcomeRecord of(final String actorId, final UUID subjectId,
            final String capabilityTag, final AttestationVerdict verdict, final double confidence) {
        return new OutcomeRecord(actorId, subjectId, verdict, confidence,
                capabilityTag, ActorType.AGENT, null, null, null, null);
    }

    /**
     * Factory for outcomes that intentionally target the global Beta score only.
     * These do NOT reach TrustScoreCache or TrustWeightedAgentStrategy.
     */
    public static OutcomeRecord ofGlobal(final String actorId, final UUID subjectId,
            final AttestationVerdict verdict, final double confidence) {
        return new OutcomeRecord(actorId, subjectId, verdict, confidence,
                CapabilityTag.GLOBAL, ActorType.AGENT, null, null, null, null);
    }

    /** @throws NullPointerException if role is null */
    public OutcomeRecord withActorRole(final String role) {
        Objects.requireNonNull(role, "role");
        return new OutcomeRecord(actorId, subjectId, verdict, confidence, capabilityTag,
                actorType, role, occurredAt, attestorId, attestorType);
    }

    /**
     * @throws NullPointerException if t is null.
     * Pass ActorType.AGENT explicitly to reset to the default rather than passing null.
     */
    public OutcomeRecord withActorType(final ActorType t) {
        Objects.requireNonNull(t, "actorType — use ActorType.AGENT to set the default explicitly");
        return new OutcomeRecord(actorId, subjectId, verdict, confidence, capabilityTag,
                t, actorRole, occurredAt, attestorId, attestorType);
    }

    /** @throws NullPointerException if ts is null */
    public OutcomeRecord withOccurredAt(final Instant ts) {
        Objects.requireNonNull(ts, "occurredAt");
        return new OutcomeRecord(actorId, subjectId, verdict, confidence, capabilityTag,
                actorType, actorRole, ts, attestorId, attestorType);
    }

    /**
     * Override the attestor. Both id and type must be non-null —
     * they are always set together to maintain the pair invariant.
     */
    public OutcomeRecord withAttestor(final String id, final ActorType t) {
        Objects.requireNonNull(id, "attestorId");
        Objects.requireNonNull(t,  "attestorType");
        return new OutcomeRecord(actorId, subjectId, verdict, confidence, capabilityTag,
                actorType, actorRole, occurredAt, id, t);
    }
}
