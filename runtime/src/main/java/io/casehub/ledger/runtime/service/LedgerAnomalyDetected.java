package io.casehub.ledger.runtime.service;

/**
 * CDI event fired by {@link LedgerHealthJob} when a ledger integrity anomaly is detected.
 *
 * <p>
 * Two disjoint subtypes cover the two distinct anomaly shapes:
 * <ul>
 * <li>{@link LedgerSequenceGapDetected} — a gap in per-(subject, tenant) sequence numbers</li>
 * <li>{@link LedgerReconciliationMismatchDetected} — a count discrepancy between a domain
 *     entity source and the ledger</li>
 * </ul>
 *
 * An observer on {@code LedgerAnomalyDetected} receives both subtypes (CDI resolves by the
 * fired object's runtime type and all its implemented interfaces). Pattern-match to
 * distinguish them:
 * <pre>{@code
 * void onAnomaly(@Observes LedgerAnomalyDetected event) {
 *     switch (event) {
 *         case LedgerSequenceGapDetected g ->
 *             log.errorf("Gap for subject %s / tenant %s: expected %d, got %d",
 *                 g.subjectId(), g.tenancyId(), g.expectedCount(), g.actualCount());
 *         case LedgerReconciliationMismatchDetected r ->
 *             log.errorf("Mismatch for %s: domain=%d, ledger=%d",
 *                 r.entityType(), r.domainCount(), r.ledgerCount());
 *     }
 * }
 * }</pre>
 */
public sealed interface LedgerAnomalyDetected
        permits LedgerSequenceGapDetected, LedgerReconciliationMismatchDetected {}
