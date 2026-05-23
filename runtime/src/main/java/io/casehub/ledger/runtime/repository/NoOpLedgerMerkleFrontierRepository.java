package io.casehub.ledger.runtime.repository;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.quarkus.arc.DefaultBean;

/**
 * No-op {@link LedgerMerkleFrontierRepository} that satisfies the injection point when
 * neither the JPA implementation ({@code JpaLedgerMerkleFrontierRepository}) nor an
 * in-memory alternative ({@code InMemoryLedgerMerkleFrontierRepository}) is selected.
 *
 * <p>
 * Consumers that do not need Merkle verification (no calls to
 * {@link io.casehub.ledger.runtime.service.LedgerVerificationService}) can leave this
 * default active — zero overhead, no database access. Calls to {@code treeRoot()} or
 * {@code inclusionProof()} will return an empty frontier and throw
 * {@link IllegalStateException}, which is the correct semantics when Merkle state
 * has not been maintained.
 *
 * <p>
 * Activation priority (lowest to highest):
 * <ol>
 * <li>This {@code @DefaultBean} — active when nothing else is present</li>
 * <li>{@code JpaLedgerMerkleFrontierRepository @Alternative} — activate via
 *     {@code quarkus.arc.selected-alternatives}</li>
 * <li>{@code InMemoryLedgerMerkleFrontierRepository @Alternative @Priority(1)} —
 *     active when {@code casehub-ledger-memory} is on the classpath</li>
 * </ol>
 */
@DefaultBean
@ApplicationScoped
public class NoOpLedgerMerkleFrontierRepository implements LedgerMerkleFrontierRepository {

    @Override
    public List<LedgerMerkleFrontier> findBySubjectId(final UUID subjectId) {
        return List.of();
    }

    @Override
    public void replace(final UUID subjectId, final List<LedgerMerkleFrontier> newFrontier) {
        // no-op — Merkle frontier is not persisted in this deployment
    }
}
