package io.casehub.ledger.runtime.repository;

import io.casehub.ledger.runtime.model.ActorIdentityBindingEntry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * No-op {@link ActorIdentityBindingRepository} — satisfies the CDI injection point when
 * neither {@code JpaActorIdentityBindingRepository} nor the in-memory alternative is active.
 *
 * <p>Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaActorIdentityBindingRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryActorIdentityBindingRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 *
 * <p>Writes go through {@code LedgerEntryRepository} regardless of which implementation
 * is active here. This bean governs read access only.
 */
@DefaultBean
@ApplicationScoped
public class NoOpActorIdentityBindingRepository implements ActorIdentityBindingRepository {

    @Override
    public Optional<ActorIdentityBindingEntry> latestBindingFor(final String actorId,
            final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<ActorIdentityBindingEntry> bindingHistoryFor(final String actorId,
            final String tenancyId) {
        return List.of();
    }
}
