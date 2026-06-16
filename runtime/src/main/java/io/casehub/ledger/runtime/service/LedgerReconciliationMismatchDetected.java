package io.casehub.ledger.runtime.service;

/**
 * Fired by {@link LedgerHealthJob} when a reconciliation source reports a count mismatch
 * between its domain entity count and the ledger entry count.
 *
 * @param entityType the subject type name from {@link LedgerReconciliationSource#subjectType()}
 * @param domainCount the count reported by the domain entity source
 * @param ledgerCount the count found in the ledger
 */
public record LedgerReconciliationMismatchDetected(
        String entityType,
        long domainCount,
        long ledgerCount)
        implements LedgerAnomalyDetected {}
