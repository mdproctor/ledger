package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * No-op {@link ErasureReceiptRepository} — satisfies the CDI injection point when
 * neither {@code JpaErasureReceiptRepository} nor the in-memory alternative is active.
 *
 * <p>Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaErasureReceiptRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryErasureReceiptRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 */
@DefaultBean
@ApplicationScoped
public class NoOpErasureReceiptRepository implements ErasureReceiptRepository {

    @Override
    public List<ErasureReceiptLedgerEntry> findByErasedActorId(
            final String erasedActorId, final String tenancyId) {
        return List.of();
    }
}
