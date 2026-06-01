package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;

/**
 * JPA entity listener enforcing ENFORCE validation mode.
 *
 * <p>Reads {@link LedgerEntry#pendingIdentityStatus} (transient, set by
 * {@link ActorIdentityValidationEnricher}) in a {@code @PrePersist} callback.
 * Throws {@link LedgerIdentityViolationException} only when:
 * <ul>
 *   <li>validation mode is ENFORCE, AND</li>
 *   <li>pendingIdentityStatus is non-null (DID binding was configured), AND</li>
 *   <li>status is not VALID</li>
 * </ul>
 *
 * <p><strong>ENFORCE is JPA-only.</strong> This listener is registered via
 * {@code @EntityListeners} on {@link LedgerEntry} and does not fire in
 * {@code InMemoryLedgerEntryRepository}.
 *
 * <p><strong>No sequence gap risk.</strong> Sequence numbers are computed via
 * {@code SELECT MAX(sequenceNumber) + 1} within the same {@code @Transactional}
 * boundary. A thrown exception rolls back the transaction including that computation.
 */
@ApplicationScoped
public class LedgerIdentityEnforcementListener {

    @Inject
    LedgerConfig config;

    @PrePersist
    public void enforceIdentity(final Object entity) {
        if (!(entity instanceof LedgerEntry entry)) return;
        if (entry.pendingIdentityStatus == null) return; // no DID binding configured
        if (config.agentIdentity().validationMode()
                != LedgerConfig.AgentIdentityConfig.ValidationMode.ENFORCE) return;
        if (entry.pendingIdentityStatus != IdentityBindingStatus.VALID) {
            throw new LedgerIdentityViolationException(entry.actorId, entry.pendingIdentityStatus);
        }
    }
}
