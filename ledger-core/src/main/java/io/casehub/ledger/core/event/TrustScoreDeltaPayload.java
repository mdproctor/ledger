package io.casehub.ledger.core.event;

import java.util.List;

public record TrustScoreDeltaPayload(List<TrustScoreDelta> deltas) {
}
