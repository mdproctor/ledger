package io.casehub.ledger.runtime.service;

import java.util.UUID;

/**
 * Fired by {@link LedgerHealthJob} when a per-(subject, tenant) sequence gap is detected.
 *
 * <p>
 * A gap means the subject's entries span sequence numbers [min, max] but the actual entry
 * count is less than {@code max - min + 1}, indicating deletion after write.
 *
 * @param subjectId     the aggregate UUID where the gap was detected
 * @param tenancyId     the tenant scope of the affected subject
 * @param expectedCount {@code MAX(sequenceNumber) - MIN(sequenceNumber) + 1}
 * @param actualCount   the actual number of entries present
 */
public record LedgerSequenceGapDetected(
        UUID subjectId,
        String tenancyId,
        long expectedCount,
        long actualCount)
        implements LedgerAnomalyDetected {}
