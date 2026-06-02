package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.OutcomeRecord;

/**
 * Records a plugin decision and its outcome as a single atomic operation.
 *
 * <p>The default implementation is {@code DefaultOutcomeRecorder} ({@code @DefaultBean}).
 * Applications may substitute a custom implementation by declaring an {@code @ApplicationScoped}
 * bean implementing this interface — CDI will prefer it over the {@code @DefaultBean}.
 *
 * @see ReactiveOutcomeRecorder for the non-blocking variant
 */
public interface OutcomeRecorder {

    /**
     * Write an {@link OutcomeRecord} as both a {@code LedgerEntry} (EVENT) and a
     * {@code LedgerAttestation}. Both writes commit in the same transaction.
     * For JPA consumers, both are visible in the database before this method returns.
     *
     * @throws IllegalStateException if {@code record.attestorId()} is null and
     *         {@code casehub.ledger.outcome.default-attestor-id} is not configured
     */
    void record(OutcomeRecord record);
}
