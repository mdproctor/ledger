package io.casehub.ledger.core.model;

public sealed interface LedgerAnomalyDetected
        permits LedgerSequenceGapDetected, LedgerReconciliationMismatchDetected {}
