package io.casehub.ledger.runtime.service.routing;

import java.util.List;

import io.casehub.ledger.api.model.ActorTrustScoreBase;

public record TrustScoreFullPayload(List<ActorTrustScoreBase> scores) {
}
