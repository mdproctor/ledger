package io.casehub.ledger.runtime.repository;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.quarkus.arc.DefaultBean;

/**
 * No-op {@link ActorIdentityBindingRepository} that satisfies the CDI injection point when
 * neither the JPA implementation ({@code JpaActorIdentityBindingRepository}) nor an in-memory
 * alternative ({@code InMemoryActorIdentityBindingRepository}) is active.
 *
 * <p>
 * Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaActorIdentityBindingRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryActorIdentityBindingRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 *
 * <p>
 * This bean is the mechanism that eliminates {@code quarkus.arc.exclude-types} workarounds
 * in consumer modules. With {@code JpaActorIdentityBindingRepository} now {@code @Alternative},
 * this {@code @DefaultBean} occupies the default CDI slot, allowing
 * {@code ActorIdentityBindingObserver} to resolve cleanly in deployments without a datasource —
 * without excluding the entire identity service package.
 *
 * <p>
 * Use a JPA or in-memory implementation in any context where binding entries must actually
 * be persisted.
 */
@DefaultBean
@ApplicationScoped
public class NoOpActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId) {
        return Optional.empty();
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId) {
        return List.of();
    }

    /**
     * No-op save — returns the entry unchanged. Does NOT:
     * assign {@code sequenceNumber}, compute digest, or call {@code EntityManager.persist()}.
     * This bean exists solely to satisfy the CDI injection point; use a JPA or in-memory
     * implementation for any context where binding entries must actually be persisted.
     */
    @Override
    public ActorIdentityBindingEntry save(final ActorIdentityBindingEntry entry) {
        return entry;
    }
}
