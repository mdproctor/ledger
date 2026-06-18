package io.casehub.ledger.runtime.service.model;

import java.util.UUID;

/**
 * Aggregate sequence statistics for a single (subject, tenant) pair.
 * Returned by {@code CrossTenantLedgerEntryRepository.findSequenceStats()} for gap detection.
 *
 * <p>{@code min} and {@code max} are {@code int} to match the {@code int} type of
 * {@link io.casehub.ledger.runtime.model.LedgerEntry#sequenceNumber} exactly — no
 * widening through Hibernate's constructor invocation path.
 */
public record SubjectSequenceStats(
        UUID subjectId,
        String tenancyId,
        long count,
        int min,
        int max) {}
