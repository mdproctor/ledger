package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.UUID;

import io.casehub.platform.api.identity.ActorType;

public class ActorTrustScoreBase {

    public UUID id;
    public String actorId;
    public ScoreType scoreType = ScoreType.GLOBAL;
    public String capabilityKey;
    public String dimensionKey;
    public ActorType actorType;
    public double trustScore;
    public double alphaValue;
    public double betaValue;
    public int decisionCount;
    public int overturnedCount;
    public int attestationPositive;
    public int attestationNegative;
    public Instant lastComputedAt;
    public double globalTrustScore;
}
