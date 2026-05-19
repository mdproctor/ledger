package io.casehub.ledger.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;

import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import io.casehub.ledger.runtime.repository.ReactiveKeyRotationRepository;

/**
 * Test-only {@link ReactiveKeyRotationRepository} that delegates to the blocking
 * {@link KeyRotationRepository} via {@link Uni#createFrom()}.
 *
 * <p>
 * Registered as {@link DefaultBean} so that the H2/JDBC test suite can resolve
 * {@link ReactiveKeyRotationRepository} injections without a Vert.x reactive datasource.
 *
 * <p>
 * Not suitable for production — consumers provide their own reactive implementation.
 */
@DefaultBean
@ApplicationScoped
class BlockingReactiveKeyRotationRepository implements ReactiveKeyRotationRepository {

    @Inject
    KeyRotationRepository blocking;

    @Override
    public Uni<List<KeyRotationEntry>> findByActorId(final String actorId) {
        return Uni.createFrom().item(() -> blocking.findByActorId(actorId));
    }

    @Override
    public Uni<List<KeyRotationEntry>> findCompromisedByActorIdAndKeyRef(
            final String actorId, final String keyRef) {
        return Uni.createFrom().item(() -> blocking.findCompromisedByActorIdAndKeyRef(actorId, keyRef));
    }
}
