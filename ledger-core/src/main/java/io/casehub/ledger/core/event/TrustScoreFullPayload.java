package io.casehub.ledger.core.event;

import java.util.List;

import io.casehub.ledger.api.model.ActorTrustScoreBase;

public record TrustScoreFullPayload(List<ActorTrustScoreBase> scores) {
}
