package io.casehub.ledger.runtime.service.routing;

import java.time.Instant;
import java.util.List;

import io.casehub.ledger.api.model.ActorTrustScoreBase;

public record TrustScoreActorUpdatedEvent(
        String actorId,
        List<ActorTrustScoreBase> scores,
        Instant computedAt) {
}
