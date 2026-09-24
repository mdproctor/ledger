package io.casehub.ledger.core.event;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.core.model.AttestationRecordedEvent;

public interface LedgerEventPublisher {
    void publishKeyRotated(AgentKeyRotatedEvent event);
    void publishActorTrustUpdated(TrustScoreActorUpdatedEvent event);
    void publishAttestationRecorded(AttestationRecordedEvent event);
}
