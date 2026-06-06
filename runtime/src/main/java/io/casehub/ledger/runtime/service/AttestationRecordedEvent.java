package io.casehub.ledger.runtime.service;

import java.util.UUID;

public record AttestationRecordedEvent(
        String actorId,
        UUID ledgerEntryId,
        UUID attestationId) {
}
