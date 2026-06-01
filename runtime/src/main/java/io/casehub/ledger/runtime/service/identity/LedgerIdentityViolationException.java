package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.IdentityBindingStatus;

/**
 * Thrown by {@link LedgerIdentityEnforcementListener} when ENFORCE mode blocks a write
 * due to a non-VALID identity binding validation result.
 */
public class LedgerIdentityViolationException extends RuntimeException {

    public final String actorId;
    public final IdentityBindingStatus status;

    public LedgerIdentityViolationException(final String actorId, final IdentityBindingStatus status) {
        super("Identity binding validation failed for actor " + actorId + ": " + status);
        this.actorId = actorId;
        this.status = status;
    }
}
