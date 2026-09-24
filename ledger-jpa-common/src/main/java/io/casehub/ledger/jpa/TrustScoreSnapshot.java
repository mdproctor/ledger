package io.casehub.ledger.jpa;

import io.casehub.ledger.api.model.TrustScoreSnapshotBase;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity
@Table(name = "trust_score_snapshot")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorGlobal",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.GLOBAL"
                + " ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndCapability",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.CAPABILITY"
                + " AND s.capabilityTag = :capabilityTag ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndDimension",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.DIMENSION"
                + " AND s.dimensionKey = :dimensionKey ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndTimeRange",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.occurredAt >= :from AND s.occurredAt <= :to"
                + " ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.deleteOlderThan",
        query = "DELETE FROM TrustScoreSnapshot s WHERE s.occurredAt < :cutoff")
public class TrustScoreSnapshot extends TrustScoreSnapshotBase {

    protected TrustScoreSnapshot() {}

    public TrustScoreSnapshot(final String actorId, final io.casehub.ledger.api.model.ScoreType scoreType,
            final String capabilityTag, final String dimensionKey,
            final double score, final double previousScore, final java.time.Instant occurredAt) {
        super(actorId, scoreType, capabilityTag, dimensionKey, score, previousScore, occurredAt);
    }
}
