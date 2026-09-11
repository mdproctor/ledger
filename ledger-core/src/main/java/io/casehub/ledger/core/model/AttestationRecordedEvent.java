package io.casehub.ledger.core.model;

import java.util.UUID;

public record AttestationRecordedEvent(
        String actorId,
        UUID ledgerEntryId,
        UUID attestationId) {
}
