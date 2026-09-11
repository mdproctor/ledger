package io.casehub.ledger.core.model;

import java.util.UUID;

public record LedgerSequenceGapDetected(
        UUID subjectId,
        String tenancyId,
        long expectedCount,
        long actualCount)
        implements LedgerAnomalyDetected {}
