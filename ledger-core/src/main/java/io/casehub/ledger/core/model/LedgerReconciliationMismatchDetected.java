package io.casehub.ledger.core.model;

public record LedgerReconciliationMismatchDetected(
        String entityType,
        long domainCount,
        long ledgerCount)
        implements LedgerAnomalyDetected {}
