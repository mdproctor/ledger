package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.api.spi.ReactiveOutcomeRecorder;

/**
 * Default reactive bridge — wraps {@link OutcomeRecorder} on the Mutiny worker pool.
 *
 * <p>{@code @DefaultBean} with no {@code @IfBuildProperty} gate. This bridge has no
 * Hibernate Reactive dependency and must be active under all profiles. Per the
 * {@code reactive-spi-bridge-default-bean} platform protocol: bridges are always active;
 * native async adapters activate via {@code @Alternative @Priority(N)}.
 *
 * <p>Callers on the Vert.x event loop use this safely — the blocking delegate runs on the
 * worker pool, not the calling thread.
 */
@DefaultBean
@ApplicationScoped
public class BlockingToReactiveOutcomeRecorder implements ReactiveOutcomeRecorder {

    @Inject
    OutcomeRecorder blocking;

    @Override
    public Uni<Void> record(final OutcomeRecord record) {
        return Uni.createFrom()
                .<Void>item(() -> {
                    blocking.record(record);
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
