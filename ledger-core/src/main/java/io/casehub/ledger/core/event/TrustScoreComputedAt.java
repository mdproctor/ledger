package io.casehub.ledger.core.event;

import java.time.Instant;

public record TrustScoreComputedAt(Instant computedAt, int actorCount) {
}
