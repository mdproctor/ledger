package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.OutcomeRecord;
import io.smallrye.mutiny.Uni;

/**
 * Non-blocking variant of {@link OutcomeRecorder}.
 *
 * <p>The default implementation wraps {@link OutcomeRecorder} on the Mutiny worker pool —
 * safe to call from the Vert.x event loop. Callers must subscribe to the returned {@code Uni}.
 *
 * <p>Failures (including {@code IllegalStateException} for unconfigured attestor) are
 * emitted as Uni failures, not thrown synchronously.
 */
public interface ReactiveOutcomeRecorder {

    /**
     * Non-blocking equivalent of {@link OutcomeRecorder#record(OutcomeRecord)}.
     * Returns a {@code Uni<Void>} that completes after both writes are committed.
     */
    Uni<Void> record(OutcomeRecord record);
}
