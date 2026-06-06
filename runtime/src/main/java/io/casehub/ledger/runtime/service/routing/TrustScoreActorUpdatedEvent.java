package io.casehub.ledger.runtime.service.routing;

import java.time.Instant;
import java.util.List;

import io.casehub.ledger.runtime.model.ActorTrustScore;

public record TrustScoreActorUpdatedEvent(
        String actorId,
        List<ActorTrustScore> scores,
        Instant computedAt) {
}
