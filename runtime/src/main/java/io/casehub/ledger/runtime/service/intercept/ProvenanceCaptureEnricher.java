package io.casehub.ledger.runtime.service.intercept;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.model.supplement.ProvenanceSupplement;
import io.casehub.ledger.runtime.service.LedgerEntryEnricher;

/**
 * {@link LedgerEntryEnricher} that auto-attaches a {@link ProvenanceSupplement} when a
 * {@link ProvenanceCapture}-annotated method is active on the current thread.
 *
 * <p>
 * Runs in the content enrichment phase of the save pipeline, before hashing and signing.
 * When no {@link ProvenanceCapture} is active, this enricher is a no-op (zero overhead).
 *
 * <p>
 * If the entry already carries a {@link ProvenanceSupplement} (attached manually by the caller),
 * it is replaced — the interceptor's context takes precedence to ensure consistency across
 * all entries persisted within the annotated scope.
 */
@ApplicationScoped
@Priority(30)
public class ProvenanceCaptureEnricher implements LedgerEntryEnricher {

    @Inject
    ProvenanceContext context;

    @Override
    public void enrich(final LedgerEntry entry) {
        if (!context.isActive()) {
            return;
        }
        final ProvenanceContext.SourceState state = context.current();
        final ProvenanceSupplement ps = new ProvenanceSupplement();
        ps.sourceEntityType = emptyToNull(state.entityType());
        ps.sourceEntityId = state.entityId();
        ps.sourceEntitySystem = emptyToNull(state.entitySystem());
        // Preserve agentConfigHash if a supplement was manually attached before the enricher ran —
        // the forensic config-hash binding is orthogonal to workflow provenance capture.
        entry.provenance().map(existing -> existing.agentConfigHash)
                .ifPresent(hash -> ps.agentConfigHash = hash);
        entry.attach(ps);
    }

    private static String emptyToNull(final String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}
