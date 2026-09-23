package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.UUID;

public class TrustScoreSnapshotBase {

    public UUID id;
    public String actorId;
    public ScoreType scoreType;
    public String capabilityTag;
    public String dimensionKey;
    public double score;
    public double previousScore;
    public Instant occurredAt;

    protected TrustScoreSnapshotBase() {}

    public TrustScoreSnapshotBase(String actorId, ScoreType scoreType,
            String capabilityTag, String dimensionKey,
            double score, double previousScore, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.scoreType = scoreType;
        this.capabilityTag = capabilityTag;
        this.dimensionKey = dimensionKey;
        this.score = score;
        this.previousScore = previousScore;
        this.occurredAt = occurredAt;
    }
}
